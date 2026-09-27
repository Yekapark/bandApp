import 'dart:async';

import 'package:bandapp_client/core/network/api_exception.dart';
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

  setUp(() {
    iap = _FakeIap();
    repo = _FakeRepo();
    container = ProviderContainer(overrides: [
      iapServiceProvider.overrideWithValue(iap),
      planRepositoryProvider.overrideWithValue(repo),
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
    expect(events.single.kind, PurchaseEventKind.verified);
    expect(events.single.bandId, 7);
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
    await sync.buy(_product(), bandId: 3);
    expect(iap.boughtFor, 3);

    final p = _purchase('tok-4', PurchaseStatus.purchased, pending: true);
    iap.emit([p]);
    await settle();

    expect(repo.verified, [(3, 'tok-4')]);
    expect(iap.completed, [p]);
  });

  test('서버 반영이 실패하면 끝내지 않는다 — 다음 시작·복귀 때 다시 온다', () async {
    repo.restoreError = ApiException(code: 'NETWORK', message: '연결 안 됨');
    sync.start();
    iap.emit([_purchase('tok-5', PurchaseStatus.restored, pending: true)]);
    await settle();

    expect(iap.completed, isEmpty);
  });
}

PurchaseDetails _purchase(String token, PurchaseStatus status, {required bool pending}) {
  return PurchaseDetails(
    productID: IapService.productId,
    verificationData: PurchaseVerificationData(
      localVerificationData: '{}',
      serverVerificationData: token,
      source: 'google_play',
    ),
    transactionDate: null,
    status: status,
  )..pendingCompletePurchase = pending;
}

ProductDetails _product() => ProductDetails(
      id: IapService.productId,
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
  final completed = <PurchaseDetails>[];

  void emit(List<PurchaseDetails> purchases) => _controller.add(purchases);

  @override
  Stream<List<PurchaseDetails>> get purchaseStream => _controller.stream;

  @override
  Future<bool> isAvailable() async => true;

  @override
  Future<void> restorePurchases() async => restoreCalls++;

  @override
  Future<void> buy(ProductDetails product, {required int bandId}) async => boughtFor = bandId;

  @override
  Future<void> complete(PurchaseDetails purchase) async => completed.add(purchase);
}

class _FakeRepo extends PlanRepository {
  _FakeRepo() : super(Dio());

  int restoreResult = 1;
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
  Future<BandPlan> verifyGooglePurchase(int bandId, String purchaseToken) async {
    verified.add((bandId, purchaseToken));
    return const BandPlan(tier: 'PREMIUM');
  }
}
