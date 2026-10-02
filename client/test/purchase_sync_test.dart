import 'dart:async';

import 'package:bandapp_client/core/network/api_exception.dart';
import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/plan/application/purchase_sync.dart';
import 'package:bandapp_client/features/plan/data/iap_service.dart';
import 'package:bandapp_client/features/plan/data/plan_models.dart';
import 'package:bandapp_client/features/plan/data/plan_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_purchase/in_app_purchase.dart';

/// LAUNCH_REVIEW B2·B3 — 검증 못 끝낸 구매를 앱 전역에서 서버에 보내고, 밴드는 구매에 적힌 값(서버 판단)을 따른다.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _FakeIap iap;
  late _FakeRepo repo;
  late ProviderContainer container;
  late PurchaseSync sync;

  /// 서버가 돌려줄 내 역할(모든 밴드 공통). 결제 직전에 다시 받는다(#61).
  var myRole = 'LEADER';

  setUp(() {
    iap = _FakeIap();
    repo = _FakeRepo();
    myRole = 'LEADER';
    container = ProviderContainer(overrides: [
      iapServiceProvider.overrideWithValue(iap),
      planRepositoryProvider.overrideWithValue(repo),
      myBandsProvider.overrideWith((ref) async => [
            for (var id = 1; id <= 9; id++)
              MyBand(
                id: id,
                name: 'b$id',
                myRole: myRole,
                memberCount: 2,
                joinedAt: DateTime(2026),
              ),
          ]),
    ]);
    sync = container.read(purchaseSyncProvider);
  });

  tearDown(() => container.dispose());

  Future<void> settle() => Future<void>.delayed(Duration.zero);

  test('시작하면 스토어에 가진 구매를 다시 묻는다', () async {
    sync.start();
    await settle();
    expect(iap.restoreCalls, 1);

    sync.start(); // 두 번 불러도 한 번만
    await settle();
    expect(iap.restoreCalls, 1);
  });

  test('확인 안 된 구매는 복구 API 로 보내고, 서버가 정한 밴드로 끝낸다', () async {
    repo.restoreResult = 7;
    final events = <PurchaseEvent>[];
    sync.events.listen(events.add);
    sync.start();

    final p = _purchase('tok-1', PurchaseStatus.restored, pending: true);
    iap.emit([p]);
    await settle();

    expect(repo.restored, ['tok-1']);
    expect(repo.verified, isEmpty);
    expect(iap.completed, [p]);
    // 이 앱에서 지금 결제한 것이 아니므로 요금제 화면 버튼은 건드리지 않는다.
    expect(events, isEmpty);
  });

  test('이미 확인 처리된 구매는 서버를 부르지 않는다', () async {
    sync.start();
    iap.emit([_purchase('tok-2', PurchaseStatus.restored, pending: false)]);
    await settle();

    expect(repo.restored, isEmpty);
    expect(iap.completed, isEmpty);
  });

  test('밴드 표시 없는 옛 구매는 방금 결제한 밴드가 없으면 끝내지 않고 남겨 둔다', () async {
    repo.restoreError = ApiException(code: 'PURCHASE_BAND_UNKNOWN', message: 'x', statusCode: 422);
    sync.start();
    iap.emit([_purchase('tok-3', PurchaseStatus.restored, pending: true)]);
    await settle();

    expect(repo.verified, isEmpty); // 지금 선택된 밴드로 검증하지 않는다
    expect(iap.completed, isEmpty);
  });

  test('밴드 표시 없는 구매라도 이 앱에서 방금 결제한 밴드가 있으면 그 밴드로 검증한다', () async {
    repo.restoreError = ApiException(code: 'PURCHASE_BAND_UNKNOWN', message: 'x', statusCode: 422);
    sync.start();
    await sync.buy(_products(), bandId: 3);
    expect(iap.boughtFor, 3);

    final p = _purchase('tok-4', PurchaseStatus.purchased, pending: true);
    iap.emit([p]);
    await settle();

    expect(repo.verified, [(3, 'tok-4')]);
    expect(iap.completed, [p]);
  });

  group('밴드마다 결제 — 같은 값의 상품 여러 개 (B4)', () {
    test('이 계정이 이미 가진 상품은 건너뛰고 다음 상품으로 결제한다', () async {
      iap.ownedOnStore = ['premium_yearly'];
      sync.start();
      await settle();

      await sync.buy(_products(), bandId: 2);

      expect(iap.bought, ['premium_yearly_2']);
    });

    test('모두 가지고 있으면 결제 창을 띄우지 않고 알린다', () async {
      iap.ownedOnStore = List.of(IapService.productIds);
      sync.start();
      await settle();

      await expectLater(sync.buy(_products(), bandId: 2),
          throwsA(isA<NoPremiumSlotException>()));
      expect(iap.bought, isEmpty);
    });

    test('스토어에 올라간 상품만 고른다', () async {
      sync.start();
      await settle();

      await sync.buy(_products(['premium_yearly_3']), bandId: 2);

      expect(iap.bought, ['premium_yearly_3']);
    });

    test('"이미 보유" 로 실패하면 다음 상품으로 다시 띄운다', () async {
      sync.start();
      await settle();
      await sync.buy(_products(), bandId: 2);
      expect(iap.bought, ['premium_yearly']);

      // Play 는 오류 결과에 상품 id 를 비워 보낸다.
      iap.emit([_errorPurchase('BillingResponse.itemAlreadyOwned')]);
      await settle();
      await settle(); // 다시 띄우기 전에 스토어 목록을 새로 받는다

      expect(iap.bought, ['premium_yearly', 'premium_yearly_2']);
    });

    test('결제 창에서 취소하면(상품 id 가 빈 이벤트) 버튼을 풀라고 알린다', () async {
      final events = <PurchaseEvent>[];
      sync.events.listen(events.add);
      sync.start();
      await settle();
      await sync.buy(_products(), bandId: 2);

      iap.emit([_purchase('', PurchaseStatus.canceled, pending: false, productId: '')]);
      await settle();

      expect(events.map((e) => e.kind), [PurchaseEventKind.canceled]);
    });
  });

  group('감사 #11 — 결제 흐름 구멍', () {
    test('이 밴드의 결제가 승인 대기면 다시 결제하지 않는다(두 번 청구 방지)', () async {
      iap.unackedOnStore = [
        _purchase('tok-p', PurchaseStatus.restored, pending: true, band: 4),
      ];
      repo.restoreError = ApiException(code: 'PURCHASE_NOT_VERIFIED', message: 'x', statusCode: 402);
      sync.start();
      await settle();

      await expectLater(sync.buy(_products(), bandId: 4),
          throwsA(isA<PurchaseInProgressException>()));
      expect(iap.bought, isEmpty);

      // 다른 밴드는 결제할 수 있다 — 승인 대기 상품은 건너뛰고.
      await sync.buy(_products(), bandId: 5);
      expect(iap.bought, ['premium_yearly_2']);
    });

    test('결제 중에 흘러온 밴드 표시 없는 옛 구매를 결제 중인 밴드로 검증하지 않는다', () async {
      repo.restoreError = ApiException(code: 'PURCHASE_BAND_UNKNOWN', message: 'x', statusCode: 422);
      final events = <PurchaseEvent>[];
      sync.events.listen(events.add);
      sync.start();
      await settle();
      iap.unackedOnStore = [_purchase('tok-old', PurchaseStatus.restored, pending: true)];

      await sync.buy(_products(), bandId: 3);
      await settle();

      expect(repo.verified, isEmpty);
      expect(iap.completed, isEmpty);
      expect(events, isEmpty); // 진행 중인 결제를 실패로 알리지 않는다
    });

    test('결제 창을 못 띄웠는데 스트림 결과가 없으면 실패로 끝내 버튼을 푼다', () async {
      PurchaseSync.launchFailGrace = Duration.zero;
      addTearDown(() => PurchaseSync.launchFailGrace = const Duration(seconds: 2));
      iap.launchResult = false;
      final events = <PurchaseEvent>[];
      sync.events.listen(events.add);
      sync.start();
      await settle();

      await sync.buy(_products(), bandId: 2);
      await Future<void>.delayed(const Duration(milliseconds: 10));

      expect(events.map((e) => e.kind), [PurchaseEventKind.failed]);
    });

    test('서버 검증 뒤 스토어 완료 알림이 실패해도 성공으로 알린다', () async {
      iap.completeThrows = true;
      final events = <PurchaseEvent>[];
      sync.events.listen(events.add);
      sync.start();
      await sync.buy(_products(), bandId: 1);

      iap.emit([_purchase('tok-c', PurchaseStatus.purchased, pending: true, band: 1)]);
      await settle();

      expect(events.map((e) => e.kind), [PurchaseEventKind.verified]);
    });

    test('만료된 구독은 가진 목록에서 빠진다', () async {
      iap.ownedOnStore = List.of(IapService.productIds);
      sync.start();
      await settle();

      iap.ownedOnStore = [];
      await sync.buy(_products(), bandId: 2);

      expect(iap.bought, ['premium_yearly']);
    });

    test('"이미 보유" 가 이어져도 앞 상품으로 되돌아가지 않는다', () async {
      sync.start();
      await settle();
      await sync.buy(_products(), bandId: 2);

      iap.emit([_errorPurchase('BillingResponse.itemAlreadyOwned')]);
      await settle();
      await settle();
      iap.emit([_errorPurchase('BillingResponse.itemAlreadyOwned')]);
      await settle();
      await settle();

      expect(iap.bought, ['premium_yearly', 'premium_yearly_2', 'premium_yearly_3']);
    });

    test('결제 중에 다른 밴드의 옛 구매가 확인돼도 진행 중인 결제를 끝내지 않는다', () async {
      repo.restoreResult = 4;
      final events = <PurchaseEvent>[];
      sync.events.listen(events.add);
      sync.start();
      await settle();
      iap.unackedOnStore = [_purchase('tok-4', PurchaseStatus.restored, pending: true, band: 4)];

      await sync.buy(_products(), bandId: 5);
      await settle();
      expect(events, isEmpty);

      // 진행 중이던 밴드 5 결제를 취소하면(상품 id 빈 이벤트) 여전히 우리 결과로 받는다.
      iap.emit([_purchase('', PurchaseStatus.canceled, pending: false, productId: '')]);
      await settle();
      expect(events.map((e) => e.kind), [PurchaseEventKind.canceled]);
    });

    test('먼저 실패한 시도의 타이머가 다음 시도를 실패로 만들지 않는다', () async {
      PurchaseSync.launchFailGrace = const Duration(milliseconds: 20);
      addTearDown(() => PurchaseSync.launchFailGrace = const Duration(seconds: 2));
      final events = <PurchaseEvent>[];
      sync.events.listen(events.add);
      sync.start();
      await settle();

      iap.launchResult = false;
      await sync.buy(_products(), bandId: 2);
      iap.emit([_errorPurchase('BillingResponse.serviceUnavailable')]); // 스트림으로 실패가 옴
      await settle();
      iap.launchResult = true;
      await sync.buy(_products(), bandId: 2); // 곧바로 다시 시도
      await Future<void>.delayed(const Duration(milliseconds: 40));

      expect(events.map((e) => e.kind), [PurchaseEventKind.failed]);
    });

    test('결제 승인 대기면 버튼을 풀라고 알린다', () async {
      final events = <PurchaseEvent>[];
      sync.events.listen(events.add);
      sync.start();
      await sync.buy(_products(), bandId: 2);

      iap.emit([_purchase('tok-w', PurchaseStatus.pending, pending: true, band: 2)]);
      await settle();

      expect(events.map((e) => e.kind), [PurchaseEventKind.pending]);
    });
  });

  group('결제 보류(account hold) — 두 번 청구 방지', () {
    test('요금제가 결제 보류면 결제 창을 띄우지 않는다', () async {
      repo.viewResult = const BandPlan(tier: 'FREE', onHold: true);
      sync.start();
      await settle();

      await expectLater(
          sync.buy(_products(), bandId: 2),
          throwsA(isA<PurchaseBlockedException>().having((e) => e.message,
              'message', PurchaseBlockedException.onHold)));
      expect(iap.bought, isEmpty);
    });

    test('이미 구독 중(자동 갱신)이면 결제 창을 띄우지 않는다', () async {
      repo.viewResult = const BandPlan(tier: 'PREMIUM', autoRenewing: true);
      sync.start();
      await settle();

      await expectLater(sync.buy(_products(), bandId: 2),
          throwsA(isA<PurchaseBlockedException>()));
      expect(iap.bought, isEmpty);
    });

    test('서버가 BAND_ALREADY_SUBSCRIBED(409)로 거절하면 완료하지 않고, 다시 와도 서버에 또 보내지 않는다',
        () async {
      repo.restoreError = ApiException(
          code: 'BAND_ALREADY_SUBSCRIBED', message: 'x', statusCode: 409);
      sync.start();
      final p = _purchase('tok-h', PurchaseStatus.restored, pending: true, band: 6);
      iap.emit([p]);
      await settle();
      iap.emit([p]); // 앱 복귀 때 스토어가 같은 구매를 다시 흘려보낸다
      await settle();

      expect(repo.restored, ['tok-h']);
      expect(iap.completed, isEmpty); // 확인 처리 안 해야 Google 이 환불한다
      // "결제 처리 중" 으로 이 밴드를 묶어 두지 않는다 — 막는 건 요금제의 onHold 몫이다.
      await sync.buy(_products(), bandId: 6);
      expect(iap.bought, isNotEmpty);
    });

    test('방금 결제가 409 로 거절되면 버튼을 풀라고 알린다', () async {
      repo.restoreError = ApiException(
          code: 'BAND_ALREADY_SUBSCRIBED', message: 'x', statusCode: 409);
      final events = <PurchaseEvent>[];
      sync.events.listen(events.add);
      sync.start();
      await sync.buy(_products(), bandId: 2);

      iap.emit([_purchase('tok-n', PurchaseStatus.purchased, pending: true, band: 2)]);
      await settle();

      expect(events.map((e) => e.kind), [PurchaseEventKind.failed]);
      expect(iap.completed, isEmpty);
    });
  });

  test('#61 결제 직전에 다시 받아 보니 밴드장이 아니면 결제 창을 띄우지 않는다', () async {
    sync.start();
    await settle();
    myRole = 'MEMBER';

    await expectLater(
        sync.buy(_products(), bandId: 2),
        throwsA(isA<PurchaseBlockedException>().having((e) => e.message,
            'message', PurchaseBlockedException.notLeader)));
    expect(iap.bought, isEmpty);
  });

  test('#62 서버가 영구적으로 거절한 구매(403 밴드장 아님)는 이번 세션에 다시 보내지 않는다', () async {
    repo.restoreError = ApiException(
        code: 'NOT_BAND_LEADER', message: 'x', statusCode: 403);
    sync.start();
    final p = _purchase('tok-403', PurchaseStatus.restored, pending: true, band: 4);
    iap.emit([p]);
    await settle();
    iap.emit([p]);
    await settle();

    expect(repo.restored, ['tok-403']);
    expect(iap.completed, isEmpty);
  });

  test('#62 일시적 실패(5xx)는 다음에 다시 보낸다', () async {
    repo.restoreError =
        ApiException(code: 'INTERNAL', message: 'x', statusCode: 500);
    sync.start();
    final p = _purchase('tok-500', PurchaseStatus.restored, pending: true, band: 4);
    iap.emit([p]);
    await settle();
    iap.emit([p]);
    await settle();

    expect(repo.restored, ['tok-500', 'tok-500']);
  });

  test('서버 반영이 실패하면 끝내지 않는다 — 다음 시작·복귀 때 다시 온다', () async {
    repo.restoreError = ApiException(code: 'NETWORK', message: '연결 안 됨');
    sync.start();
    iap.emit([_purchase('tok-5', PurchaseStatus.restored, pending: true)]);
    await settle();

    expect(iap.completed, isEmpty);
  });
}

PurchaseDetails _purchase(String token, PurchaseStatus status,
    {required bool pending, String productId = 'premium_yearly', int? band}) {
  return PurchaseDetails(
    productID: productId,
    verificationData: PurchaseVerificationData(
      localVerificationData:
          band == null ? '{}' : '{"obfuscatedAccountId":"band-$band"}',
      serverVerificationData: token,
      source: 'google_play',
    ),
    transactionDate: null,
    status: status,
  )..pendingCompletePurchase = pending;
}

Map<String, ProductDetails> _products([List<String>? ids]) => {
      for (final id in ids ?? IapService.productIds) id: _product(id),
    };

PurchaseDetails _errorPurchase(String message) => PurchaseDetails(
      productID: '',
      verificationData: PurchaseVerificationData(
          localVerificationData: '', serverVerificationData: '', source: 'google_play'),
      transactionDate: null,
      status: PurchaseStatus.error,
    )..error = IAPError(source: 'google_play', code: 'purchase_error', message: message);

ProductDetails _product(String id) => ProductDetails(
      id: id,
      title: 'PREMIUM',
      description: '',
      price: '₩19,000',
      rawPrice: 19000,
      currencyCode: 'KRW',
    );

class _FakeIap extends IapService {
  _FakeIap() : super(iap: null);

  final _controller = StreamController<List<PurchaseDetails>>.broadcast();
  int restoreCalls = 0;
  int? boughtFor;
  final bought = <String>[];

  /// 스토어가 "이 계정이 가진 구독" 으로 돌려줄 상품들(확인 처리 끝난 것).
  List<String> ownedOnStore = [];

  /// 복구 때 함께 돌려줄 확인 처리 안 된 구매들.
  List<PurchaseDetails> unackedOnStore = [];
  bool launchResult = true;
  bool completeThrows = false;
  final completed = <PurchaseDetails>[];

  void emit(List<PurchaseDetails> purchases) => _controller.add(purchases);

  @override
  Stream<List<PurchaseDetails>> get purchaseStream => _controller.stream;

  @override
  Future<bool> isAvailable() async => true;

  @override
  Future<void> restorePurchases() async {
    restoreCalls++;
    if (ownedOnStore.isNotEmpty || unackedOnStore.isNotEmpty) {
      emit([
        for (final id in ownedOnStore)
          _purchase('owned-$id', PurchaseStatus.restored, pending: false, productId: id),
        ...unackedOnStore,
      ]);
    }
  }

  @override
  Future<bool> buy(ProductDetails product, {required int bandId}) async {
    boughtFor = bandId;
    bought.add(product.id);
    return launchResult;
  }

  @override
  Future<void> complete(PurchaseDetails purchase) async {
    if (completeThrows) throw Exception('acknowledge 실패');
    completed.add(purchase);
  }
}

class _FakeRepo extends PlanRepository {
  _FakeRepo() : super(Dio());

  int restoreResult = 1;
  BandPlan viewResult = const BandPlan(tier: 'FREE');
  ApiException? restoreError;
  final restored = <String>[];
  final verified = <(int, String)>[];

  @override
  Future<({int bandId, BandPlan plan})> restoreGooglePurchase(String purchaseToken) async {
    restored.add(purchaseToken);
    final err = restoreError;
    if (err != null) throw err;
    return (bandId: restoreResult, plan: const BandPlan(tier: 'PREMIUM'));
  }

  @override
  Future<BandPlan> view(int bandId) async => viewResult;

  @override
  Future<BandPlan> verifyGooglePurchase(int bandId, String purchaseToken) async {
    verified.add((bandId, purchaseToken));
    return const BandPlan(tier: 'PREMIUM');
  }
}
