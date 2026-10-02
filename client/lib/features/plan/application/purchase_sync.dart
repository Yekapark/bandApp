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

/// 이 밴드의 결제가 스토어에 이미 있고 아직 확인 처리되지 않았다 — 결제 승인 대기(느린 카드 등)이거나 검증이
/// 아직 안 끝난 구매. 또 결제하면 같은 밴드에 구독이 두 개 생겨 두 번 청구된다.
class PurchaseInProgressException implements Exception {
  const PurchaseInProgressException();

  static const String message = '이 밴드의 결제가 아직 처리 중이에요. 승인되면 프리미엄이 자동으로 시작돼요. '
      '잠시 후 요금제 화면을 다시 열어 확인해 주세요.';

  @override
  String toString() => message;
}

/// 서버의 요금제로 보아 이 밴드는 지금 결제하면 안 된다 — 이미 구독 중이거나, 구독이 결제 보류 중이다.
/// 또 결제하면 같은 밴드에 구독이 두 개 생겨 두 번 청구된다. [message] 를 그대로 보여 준다.
class PurchaseBlockedException implements Exception {
  const PurchaseBlockedException(this.message);

  static const String alreadyPremium = '이 밴드는 이미 프리미엄이에요.';
  static const String onHold = '카드 결제가 실패해 프리미엄이 잠시 멈췄어요. 새로 결제하지 말고 '
      'Google Play 에서 결제 수단을 고쳐 주세요. 고치면 바로 다시 이어져요.';

  final String message;

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

  /// 스토어에 있지만 아직 확인 처리되지 않은 구매가 적힌 밴드들(승인 대기·검증 전). 이 밴드는 다시 결제하지 않는다.
  final _unackedBands = <int>{};

  /// 서버가 "이 밴드는 이미 구독 중" 으로 거절한 구매 토큰(`BAND_ALREADY_SUBSCRIBED`). 서버가 확인 처리하지
  /// 않았으니 Google 이 3일 안에 자동 환불하고, 그동안 스토어는 이 구매를 복구 때마다 다시 흘려보낸다 — 다시
  /// 보내도 같은 답이라 이 앱이 켜져 있는 동안은 더 보내지 않는다.
  final _rejected = <String>{};

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
    // 화면의 요금제는 처음 열 때 받은 것이다. 그 사이 다른 기기·다른 밴드장이 결제했거나 갱신 결제가 보류됐으면
    // 같은 밴드에 구독이 하나 더 생겨 두 번 청구되므로, 결제 창을 띄우기 직전에 서버에서 새로 받아 본다.
    final plan = await _ref.refresh(bandPlanProvider(bandId).future);
    if (plan.onHold) {
      throw const PurchaseBlockedException(PurchaseBlockedException.onHold);
    }
    if (plan.isPremium && (plan.autoRenewing || plan.canceled)) {
      throw const PurchaseBlockedException(
          PurchaseBlockedException.alreadyPremium);
    }
    await _refreshOwned();
    if (_unackedBands.contains(bandId)) throw const PurchaseInProgressException();
    final product = _nextSlot(products);
    if (product == null) throw const NoPremiumSlotException();
    _buyingBandId = bandId;
    _buyingCandidates = products;
    try {
      await _launch(product, bandId);
    } catch (_) {
      _clearBuying();
      rethrow;
    }
  }

  /// 결제 창을 못 띄웠을 때 결제 스트림의 실패 결과를 기다리는 시간. 시험 때 줄인다.
  @visibleForTesting
  static Duration launchFailGrace = const Duration(seconds: 2);

  /// 결제 창을 띄운다. Play 는 창을 못 띄운 실패를 보통 결제 스트림으로도 보내 거기서 처리되지만, 안 보낼 때가
  /// 있다 — 그러면 아무 결과도 안 와서 요금제 화면 버튼이 돌기만 하며 잠겨 있었다. 잠깐 기다려도 이 시도가
  /// 그대로면 실패로 끝낸다.
  Future<void> _launch(ProductDetails product, int bandId) async {
    _buyingProductId = product.id;
    final attempt = ++_attempt;
    if (await _iap.buy(product, bandId: bandId)) return;
    Future<void>.delayed(launchFailGrace, () {
      // 그 사이 결과가 왔거나 새로 결제를 시작했으면(같은 밴드·상품이어도) 손대지 않는다.
      if (attempt != _attempt || _buyingBandId == null) return;
      _clearBuying();
      _emit(const PurchaseEvent(PurchaseEventKind.failed));
      _toast(_genericFailure);
    });
  }

  /// 결제 창을 띄운 횟수 — 늦게 도는 실패 타이머가 다음 시도를 건드리지 않게 시도를 구분한다.
  int _attempt = 0;

  static const alreadySubscribedMessage =
      '이 밴드에는 아직 끝나지 않은 구독이 있어요(결제 보류 중이거나, 해지했지만 기간이 남았어요). '
      '방금 결제는 3일 안에 자동으로 환불돼요. '
      '결제 수단은 Google Play 에서 고쳐 주세요.';

  static const _genericFailure = '결제를 완료하지 못했어요. 잠시 후 다시 시도해 주세요.';

  ProductDetails? _nextSlot(Map<String, ProductDetails> products) {
    for (final id in IapService.productIds) {
      final p = products[id];
      if (p != null && !_owned.contains(id)) return p;
    }
    return null;
  }

  void _clearBuying() {
    _attempt++;
    _buyingBandId = null;
    _buyingProductId = null;
    _buyingCandidates = const {};
  }

  /// 가진 상품 목록을 지금 새로 받는다(간격 제한 없이). 스토어가 구매를 스트림으로 흘려보내면
  /// [_onUpdates] 가 [_owned] 를 채운다 — 스트림 전달은 한 박자 늦어서 한 번 양보한다.
  Future<void> _refreshOwned() async {
    // 지난 목록은 버린다 — 만료된 구독·다른 Google 계정의 구독이 남아 있으면 상품을 헛되이 건너뛴다.
    _owned.clear();
    _unackedBands.clear();
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
          p.status == PurchaseStatus.restored ||
          p.status == PurchaseStatus.pending) {
        _owned.add(p.productID);
        final band = IapService.taggedBand(p);
        if (p.pendingCompletePurchase &&
            band != null &&
            !_rejected.contains(_iap.purchaseToken(p))) {
          _unackedBands.add(band);
        }
      }
    }
    for (final p in purchases) {
      if (!_isOurs(p)) continue;
      switch (p.status) {
        case PurchaseStatus.pending:
          // 결제 승인 대기(느린 카드 등). 승인되면 다음 시작·복귀 때 복구로 반영된다. 예전엔 안내 없이 버튼이
          // 계속 돌았다. 버튼은 풀고, 이 밴드는 [_unackedBands] 로 다시 결제하지 못하게 막는다.
          if (_isCurrent(p)) {
            _clearBuying();
            _emit(const PurchaseEvent(PurchaseEventKind.pending));
            _toast('결제 승인을 기다리고 있어요. 승인되면 프리미엄이 자동으로 시작돼요.');
          }
        case PurchaseStatus.canceled:
          _clearBuying();
          _emit(const PurchaseEvent(PurchaseEventKind.canceled));
          if (p.pendingCompletePurchase) await _completeQuietly(p);
        case PurchaseStatus.error:
          if (p.pendingCompletePurchase) await _completeQuietly(p);
          // 가진 줄 몰랐던 상품이었다(다른 기기에서 샀거나 목록이 늦게 옴) — 다음 상품으로 다시 띄운다.
          // Play 오류 원문은 "BillingResponse.serviceUnavailable" 같은 영문 코드라 보여 주지 않는다.
          final failure =
              _isAlreadyOwned(p) ? await _retryWithNextSlot() : _genericFailure;
          if (failure != null) {
            _clearBuying();
            _emit(const PurchaseEvent(PurchaseEventKind.failed));
            _toast(failure);
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
    if (token == null || _rejected.contains(token) || !_inFlight.add(token)) {
      return;
    }
    final buyingBand = _buyingBandId;
    final tag = IapService.taggedBand(p);
    final mine = _isCurrent(p);
    final repo = _ref.read(planRepositoryProvider);
    try {
      int bandId;
      try {
        bandId = (await repo.restoreGooglePurchase(token)).bandId;
      } on ApiException catch (e) {
        // 밴드 표시가 없는 구매 — 이 앱에서 방금 결제한 그 구매일 때만 결제 중인 밴드로 검증한다.
        if (e.code != 'PURCHASE_BAND_UNKNOWN' || !mine || buyingBand == null) {
          rethrow;
        }
        await repo.verifyGooglePurchase(buyingBand, token);
        bandId = buyingBand;
      }
      _ref.invalidate(bandPlanProvider(bandId));
      if (tag != null) _unackedBands.remove(tag);
      // 서버가 검증하면서 이미 확인 처리(acknowledge)했다. 여기서 실패해도 결제는 끝났으니 실패로 알리지 않는다.
      await _completeQuietly(p);
      if (mine) {
        _clearBuying();
        _emit(PurchaseEvent(PurchaseEventKind.verified, bandId: bandId));
      }
      _toast('결제가 확인돼 프리미엄이 시작됐어요.');
    } on ApiException catch (e) {
      if (e.code == 'BAND_ALREADY_SUBSCRIBED') {
        // 이 밴드에는 이미 다른 구독이 있다(결제 보류 포함). 완료(확인 처리)하지 않아야 Google 이 환불한다.
        _rejected.add(token);
        if (tag != null) _unackedBands.remove(tag);
        if (mine) {
          _clearBuying();
          _emit(const PurchaseEvent(PurchaseEventKind.failed));
        }
        // 앱이 꺼졌다 켜져 복구로 온 경우에도 돈 이야기라 한 번은 알린다.
        // 서버 문구가 보류·해지 후 남은 기간 두 경우를 다 설명한다. 비어 있으면 앱 문구로.
        _toast(e.message.trim().isEmpty ? alreadySubscribedMessage : e.message);
        return;
      }
      _failed(mine, e.message);
    } catch (_) {
      _failed(mine, '구매를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      _inFlight.remove(token);
    }
  }

  /// 방금 결제한 사람에게만 알린다. 앱 시작 때 조용히 복구하다 실패한 것은 다음에 다시 시도하므로
  /// 매번 안내를 띄우지 않는다.
  void _failed(bool mine, String message) {
    if (!mine) return;
    _clearBuying();
    _emit(const PurchaseEvent(PurchaseEventKind.failed));
    _toast(message);
  }

  /// 지금 결제 창을 띄운 그 결제의 결과인가. 결제를 시작·복귀할 때 스토어가 다른(옛) 미완료 구매를 같이
  /// 흘려보내면 그건 남의 결과다 — 예전엔 그 결과로 진행 중인 결제 상태를 지우고 버튼을 풀었고(다시 눌러 같은
  /// 밴드를 두 번 결제할 수 있었다), 밴드 표시 없는 옛 구매를 지금 결제 중인 밴드로 검증할 수 있었다(B3 재발).
  bool _isCurrent(PurchaseDetails p) {
    final buying = _buyingBandId;
    if (buying == null) return false;
    final tag = IapService.taggedBand(p);
    return tag == buying ||
        (tag == null && p.status != PurchaseStatus.restored);
  }

  /// 우리 상품의 이벤트인가. Play 는 **취소·오류 결과에 상품 id 를 비워** 보낸다 — 예전엔 id 로만 걸러서
  /// 이 이벤트들을 버렸고, 결제 창에서 취소하면 요금제 화면 버튼이 잠긴 채 남았다. 이 앱이 결제 창을 띄운
  /// 중이면 빈 id 도 우리 것으로 본다.
  bool _isOurs(PurchaseDetails p) =>
      IapService.productIds.contains(p.productID) ||
      (p.productID.isEmpty && _buyingBandId != null);

  static bool _isAlreadyOwned(PurchaseDetails p) =>
      (p.error?.message ?? '').contains('itemAlreadyOwned');

  /// 방금 띄운 상품을 "가진 것" 으로 적고, 남은 상품이 있으면 결제 창을 다시 띄운다. 다시 띄우기 전에 스토어
  /// 목록을 새로 받는다 — "이미 보유" 한 상품이 **이 밴드의** 승인 대기 결제면, 다음 상품으로 또 결제해 같은
  /// 밴드에 두 번 청구된다.
  /// 다시 띄웠으면 null, 아니면 사용자에게 보일 안내.
  Future<String?> _retryWithNextSlot() async {
    final bandId = _buyingBandId;
    final tried = _buyingProductId;
    if (bandId == null || tried == null) return _genericFailure;
    // 이번 결제에서 "이미 보유" 로 확인한 상품은 새 목록에 없어도 다시 고르지 않는다(안 그러면 둘 사이를 맴돈다).
    final known = {..._owned, tried};
    await _refreshOwned();
    _owned.addAll(known);
    if (_unackedBands.contains(bandId)) return PurchaseInProgressException.message;
    final next = _nextSlot(_buyingCandidates);
    if (next == null) return NoPremiumSlotException.message;
    try {
      await _launch(next, bandId);
      return null;
    } catch (_) {
      return _genericFailure;
    }
  }

  /// 스토어에 처리 끝을 알린다. 실패해도 흐름을 멈추지 않는다 — 확인 처리는 서버도 하고, 안 됐으면 다음 복구 때
  /// 다시 온다.
  Future<void> _completeQuietly(PurchaseDetails p) async {
    try {
      await _iap.complete(p);
    } catch (_) {}
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
