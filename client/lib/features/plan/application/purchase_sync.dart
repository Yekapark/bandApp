import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:in_app_purchase/in_app_purchase.dart';

import '../../../core/network/api_exception.dart';
import '../../notification/data/push_service.dart' show scaffoldMessengerKey;
import '../data/iap_service.dart';
import '../data/plan_repository.dart';
import 'plan_providers.dart';

/// 이 Google 계정이 PREMIUM 상품을 모두 가지고 있어 더 결제할 수 없다(B4).
class NoPremiumSlotException implements Exception {
  const NoPremiumSlotException();

  static final String message =
      '한 Google 계정으로는 밴드 ${IapService.productIds.length}개까지 프리미엄을 결제할 수 있어요. '
      '다른 Google 계정으로 결제해 주세요.';

  @override
  String toString() => message;
}

/// 결제 흐름의 상태 — 요금제 화면이 버튼을 잠그고 풀 때 쓴다. 안내 문구는 [PurchaseSync] 가 직접 띄운다.
enum PurchaseEventKind { pending, verified, failed, canceled }

class PurchaseEvent {
  const PurchaseEvent(this.kind, {this.bandId});

  final PurchaseEventKind kind;

  /// [PurchaseEventKind.verified] 일 때 PREMIUM 이 반영된 밴드.
  final int? bandId;
}

/// Play Billing 창구. 테스트에서 가짜로 바꿔 끼운다.
final iapServiceProvider = Provider<IapService>((ref) => IapService());

/// 로그인한 동안 앱 전역에서 Play 결제 스트림을 듣고, 검증을 못 끝낸 구매를 서버에 보낸다.
final purchaseSyncProvider = Provider<PurchaseSync>((ref) {
  final sync = PurchaseSync(ref, ref.watch(iapServiceProvider));
  ref.onDispose(sync.dispose);
  return sync;
});

/// 결제 스트림을 **앱 전역에서 한 번만** 듣는다 (LAUNCH_REVIEW B2).
///
/// **왜 요금제 화면이 아니라 여기인가** — 예전에는 요금제 화면만 스트림을 들었다. 결제 직후 검증 전에
/// 앱이 꺼지거나 네트워크가 끊기면 그 구매는 요금제 화면을 다시 열 때까지 검증되지 않았고, 3일이
/// 지나면 Google 이 자동 환불한다. 이제 로그인하면 [start] 가 스트림을 열고 스토어에 가진 구매를 다시
/// 물어보며([IapService.restorePurchases]), 앱으로 돌아올 때도 한 번 더 묻는다.
///
/// **밴드는 구매에 적힌 값으로 정한다** (B3) — 구매할 때 [IapService.bandTag] 를 붙이고, 서버
/// `/plan/google/restore` 가 그 값으로 밴드를 고른다. "지금 선택된 밴드" 로 검증하면 밴드장이 밴드를 두 개
/// 가진 경우 다른 밴드가 PREMIUM 이 된다. 밴드 표시가 없는 옛 구매(`PURCHASE_BAND_UNKNOWN`)는 이 앱에서
/// 방금 결제를 시작한 밴드가 있을 때만 그 밴드로 검증한다.
///
/// 이미 확인 처리된 구매(`pendingCompletePurchase == false`)는 건드리지 않는다 — 서버가 반영을 끝냈고,
/// 갱신·해지는 서버가 웹훅으로 받는다. 그래서 앱을 켤 때마다 서버를 부르지 않는다.
class PurchaseSync {
  PurchaseSync(this._ref, this._iap);

  final Ref _ref;
  final IapService _iap;
  final _events = StreamController<PurchaseEvent>.broadcast();

  StreamSubscription<List<PurchaseDetails>>? _sub;
  AppLifecycleListener? _lifecycle;

  /// 이 앱에서 방금 결제를 시작한 밴드. 결과가 오면 비운다. 밴드 표시가 없는 구매의 대체 경로에만 쓴다.
  int? _buyingBandId;

  /// 서버에 보내는 중인 구매 토큰 — 시작 복구와 결제 결과가 겹쳐도 한 번만 보낸다.
  final _inFlight = <String>{};

  DateTime? _lastRestore;

  /// 이 Google 계정이 지금 가진 PREMIUM 상품(해지 예약한 것 포함). 스토어가 알려 준 구매로 채운다.
  /// 같은 상품은 다시 살 수 없으므로 결제할 때 이걸 빼고 고른다(B4).
  final _owned = <String>{};

  /// 방금 결제 창을 띄운 상품과 고를 수 있던 상품들 — "이미 보유" 로 실패하면 다음 상품으로 다시 띄운다.
  String? _buyingProductId;
  Map<String, ProductDetails> _buyingCandidates = const {};

  /// 스토어에 다시 묻는 최소 간격 — 알림 눌러 들락날락할 때마다 묻지 않게.
  static const _minRestoreGap = Duration(seconds: 30);

  Stream<PurchaseEvent> get events => _events.stream;

  /// 로그인 직후. 여러 번 불러도 한 번만 연다.
  void start() {
    if (_sub != null) return;
    _sub = _iap.purchaseStream.listen(_onUpdates, onError: (_) {});
    _lifecycle = AppLifecycleListener(onResume: _restore);
    _lastRestore = null;
    _restore();
  }

  /// 로그아웃. 남은 구매는 스토어에 그대로 있다가 다음 로그인 때 다시 온다.
  void stop() {
    _sub?.cancel();
    _sub = null;
    _lifecycle?.dispose();
    _lifecycle = null;
    _buyingBandId = null;
  }

  void dispose() {
    stop();
    _events.close();
  }

  /// 요금제 화면의 구매 버튼. [products] 는 스토어에 올라가 있는 PREMIUM 상품들이다. 이 Google 계정이
  /// 아직 안 가진 상품을 [IapService.productIds] 순서로 골라 결제 창을 띄운다. 결과는 [events] 와 안내
  /// 문구로 온다. 모두 가지고 있으면 [NoPremiumSlotException].
  Future<void> buy(Map<String, ProductDetails> products,
      {required int bandId}) async {
    start(); // 로그인 이벤트보다 먼저 화면이 열린 경우에도 결과를 놓치지 않게.
    await _refreshOwned();
    final product = _nextSlot(products);
    if (product == null) throw const NoPremiumSlotException();
    _buyingBandId = bandId;
    _buyingCandidates = products;
    _buyingProductId = product.id;
    try {
      await _iap.buy(product, bandId: bandId);
    } catch (_) {
      _clearBuying();
      rethrow;
    }
  }

  ProductDetails? _nextSlot(Map<String, ProductDetails> products) {
    for (final id in IapService.productIds) {
      final p = products[id];
      if (p != null && !_owned.contains(id)) return p;
    }
    return null;
  }

  void _clearBuying() {
    _buyingBandId = null;
    _buyingProductId = null;
    _buyingCandidates = const {};
  }

  /// 가진 상품 목록을 지금 새로 받는다(간격 제한 없이). 스토어가 구매를 스트림으로 흘려보내면
  /// [_onUpdates] 가 [_owned] 를 채운다 — 스트림 전달은 한 박자 늦어서 한 번 양보한다.
  Future<void> _refreshOwned() async {
    _lastRestore = null;
    await _restore();
    await Future<void>.delayed(Duration.zero);
  }

  Future<void> _restore() async {
    final now = DateTime.now();
    final last = _lastRestore;
    if (last != null && now.difference(last) < _minRestoreGap) return;
    _lastRestore = now;
    try {
      if (!await _iap.isAvailable()) return;
      await _iap.restorePurchases();
    } catch (_) {
      // 스토어가 없거나(에뮬레이터·Play 미설치) 잠시 안 되는 것 — 다음 복귀 때 다시 묻는다.
    }
  }

  Future<void> _onUpdates(List<PurchaseDetails> purchases) async {
    // 가진 상품은 먼저, 기다림 없이 기록한다 — [_refreshOwned] 가 한 박자만 기다리고 고르기 때문.
    for (final p in purchases) {
      if (!IapService.productIds.contains(p.productID)) continue;
      if (p.status == PurchaseStatus.purchased ||
          p.status == PurchaseStatus.restored) {
        _owned.add(p.productID);
      }
    }
    for (final p in purchases) {
      if (!_isOurs(p)) continue;
      switch (p.status) {
        case PurchaseStatus.pending:
          _emit(const PurchaseEvent(PurchaseEventKind.pending));
        case PurchaseStatus.canceled:
          _clearBuying();
          _emit(const PurchaseEvent(PurchaseEventKind.canceled));
          if (p.pendingCompletePurchase) await _iap.complete(p);
        case PurchaseStatus.error:
          if (p.pendingCompletePurchase) await _iap.complete(p);
          // 가진 줄 몰랐던 상품이었다(다른 기기에서 샀거나 목록이 늦게 옴) — 다음 상품으로 다시 띄운다.
          final retried = _isAlreadyOwned(p) && await _retryWithNextSlot();
          if (!retried) {
            _clearBuying();
            _emit(const PurchaseEvent(PurchaseEventKind.failed));
            _toast(_isAlreadyOwned(p)
                ? NoPremiumSlotException.message
                : (p.error?.message ?? '결제에 실패했어요.'));
          }
        case PurchaseStatus.purchased:
        case PurchaseStatus.restored:
          // 이미 확인 처리된 구매는 서버가 반영을 끝낸 것이다 — 다시 보내지 않는다.
          if (p.pendingCompletePurchase) await _verify(p);
      }
    }
  }

  /// 서버에 반영하고, 성공했을 때만 스토어에 완료를 알린다. 실패하면 완료하지 않는다 —
  /// 스토어에 남아 있다가 다음 시작·복귀 때 다시 온다(3일 안에 반영되면 환불되지 않는다).
  Future<void> _verify(PurchaseDetails p) async {
    final token = _iap.purchaseToken(p);
    if (token == null || !_inFlight.add(token)) return;
    final buyingBand = _buyingBandId;
    final repo = _ref.read(planRepositoryProvider);
    try {
      int bandId;
      try {
        bandId = (await repo.restoreGooglePurchase(token)).bandId;
      } on ApiException catch (e) {
        // 밴드 표시가 없는 옛 구매 — 이 앱에서 방금 결제한 밴드가 있을 때만 그 밴드로 검증한다.
        if (e.code != 'PURCHASE_BAND_UNKNOWN' || buyingBand == null) rethrow;
        await repo.verifyGooglePurchase(buyingBand, token);
        bandId = buyingBand;
      }
      _ref.invalidate(bandPlanProvider(bandId));
      await _iap.complete(p);
      _clearBuying();
      _emit(PurchaseEvent(PurchaseEventKind.verified, bandId: bandId));
      _toast('결제가 확인돼 프리미엄이 시작됐어요.');
    } on ApiException catch (e) {
      _failed(buyingBand, e.message);
    } catch (_) {
      _failed(buyingBand, '구매를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      _inFlight.remove(token);
    }
  }

  /// 방금 결제한 사람에게만 알린다. 앱 시작 때 조용히 복구하다 실패한 것은 다음에 다시 시도하므로
  /// 매번 안내를 띄우지 않는다.
  void _failed(int? buyingBand, String message) {
    if (buyingBand == null) return;
    _clearBuying();
    _emit(const PurchaseEvent(PurchaseEventKind.failed));
    _toast(message);
  }

  /// 우리 상품의 이벤트인가. Play 는 **취소·오류 결과에 상품 id 를 비워** 보낸다 — 예전엔 id 로만 걸러서
  /// 이 이벤트들을 버렸고, 결제 창에서 취소하면 요금제 화면 버튼이 잠긴 채 남았다. 이 앱이 결제 창을 띄운
  /// 중이면 빈 id 도 우리 것으로 본다.
  bool _isOurs(PurchaseDetails p) =>
      IapService.productIds.contains(p.productID) ||
      (p.productID.isEmpty && _buyingBandId != null);

  static bool _isAlreadyOwned(PurchaseDetails p) =>
      (p.error?.message ?? '').contains('itemAlreadyOwned');

  /// 방금 띄운 상품을 "가진 것" 으로 적고, 남은 상품이 있으면 결제 창을 다시 띄운다.
  Future<bool> _retryWithNextSlot() async {
    final bandId = _buyingBandId;
    final tried = _buyingProductId;
    if (bandId == null || tried == null) return false;
    _owned.add(tried);
    final next = _nextSlot(_buyingCandidates);
    if (next == null) return false;
    _buyingProductId = next.id;
    try {
      await _iap.buy(next, bandId: bandId);
      return true;
    } catch (_) {
      return false;
    }
  }

  void _emit(PurchaseEvent e) {
    if (!_events.isClosed) _events.add(e);
  }

  void _toast(String msg) {
    scaffoldMessengerKey.currentState
      ?..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}
