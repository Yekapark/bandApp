import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/plan/application/plan_providers.dart';
import 'package:bandapp_client/features/plan/application/purchase_sync.dart';
import 'package:bandapp_client/features/plan/data/iap_service.dart';
import 'package:bandapp_client/features/plan/data/plan_models.dart';
import 'package:bandapp_client/features/plan/presentation/plan_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:in_app_purchase/in_app_purchase.dart';

/// 갱신 결제가 보류된 밴드 — 결제 버튼 대신 Play 에서 결제 수단을 고치라고 안내한다(두 번 청구 방지).
void main() {
  Future<void> pump(WidgetTester tester, BandPlan plan,
      {String role = 'LEADER'}) async {
    tester.view.physicalSize = const Size(800, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(ProviderScope(
      overrides: [
        iapServiceProvider.overrideWithValue(_StoreIap()),
        currentBandProvider.overrideWithValue(MyBand(
            id: 1,
            name: '밴드',
            myRole: role,
            memberCount: 3,
            joinedAt: DateTime(2026))),
        bandPlanProvider.overrideWith((ref, id) async => plan),
      ],
      child: const MaterialApp(home: PlanScreen()),
    ));
    await tester.pump();
    await tester.pump();
  }

  testWidgets('결제 보류면 구매 버튼 없이 Play 에서 고치라고 안내한다', (tester) async {
    await pump(tester, const BandPlan(tier: 'FREE', onHold: true));

    expect(find.textContaining('카드 결제가 실패해 프리미엄이 잠시 멈췄어요'), findsOneWidget);
    expect(find.text('Google Play 에서 구독 관리'), findsOneWidget);
    expect(find.textContaining('PREMIUM 시작'), findsNothing);
  });

  testWidgets('결제 보류면 밴드원에게도 같은 안내를 보인다(결제한 사람일 수 있다)', (tester) async {
    await pump(tester, const BandPlan(tier: 'FREE', onHold: true),
        role: 'MEMBER');

    expect(find.text('Google Play 에서 구독 관리'), findsOneWidget);
  });

  testWidgets('보류가 아니면 밴드장에게 구매 버튼을 보인다', (tester) async {
    await pump(tester, const BandPlan(tier: 'FREE'));

    expect(find.textContaining('PREMIUM 시작'), findsOneWidget);
    expect(find.textContaining('잠시 멈췄어요'), findsNothing);
  });
}

class _StoreIap extends IapService {
  _StoreIap() : super(iap: null);

  @override
  Stream<List<PurchaseDetails>> get purchaseStream => const Stream.empty();

  @override
  Future<bool> isAvailable() async => true;

  @override
  Future<Map<String, ProductDetails>> loadProducts() async => {
        'premium_yearly': ProductDetails(
          id: 'premium_yearly',
          title: 'PREMIUM',
          description: '',
          price: '₩19,000',
          rawPrice: 19000,
          currencyCode: 'KRW',
        ),
      };
}
