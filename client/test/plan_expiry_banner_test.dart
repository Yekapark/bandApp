import 'package:bandapp_client/features/home/presentation/widgets/plan_expiry_banner.dart';
import 'package:bandapp_client/features/plan/data/plan_models.dart';
import 'package:flutter_test/flutter_test.dart';

/// LAUNCH_REVIEW B6 — 정말로 끝나는 구독에만 "끝나요" 배너를 띄운다.
void main() {
  final now = DateTime.utc(2026, 9, 28);
  BandPlan premium({required bool autoRenewing, bool canceled = false}) =>
      BandPlan(
        tier: 'PREMIUM',
        expiresAt: now.add(const Duration(days: 6)),
        canceled: canceled,
        autoRenewing: autoRenewing,
      );

  test('자동 갱신 중이면 만료일이 가까워도 띄우지 않는다', () {
    expect(PlanExpiryBanner.messageFor(premium(autoRenewing: true), now), isNull);
  });

  test('해지 예약했으면 띄운다', () {
    expect(
        PlanExpiryBanner.messageFor(
            premium(autoRenewing: false, canceled: true), now),
        contains('끝나요'));
  });

  test('쿠폰 프리미엄(자동 갱신 아님)도 띄운다', () {
    expect(PlanExpiryBanner.messageFor(premium(autoRenewing: false), now),
        contains('끝나요'));
  });

  test('30일보다 많이 남았으면 띄우지 않는다', () {
    final far = BandPlan(
        tier: 'PREMIUM', expiresAt: now.add(const Duration(days: 200)));
    expect(PlanExpiryBanner.messageFor(far, now), isNull);
  });

  test('무료는 띄우지 않는다', () {
    expect(PlanExpiryBanner.messageFor(const BandPlan(tier: 'FREE'), now),
        isNull);
  });

  test('결제 보류면 무료여도 차분하게 띄운다', () {
    final msg = PlanExpiryBanner.messageFor(
        const BandPlan(tier: 'FREE', onHold: true), now);
    expect(msg, contains('잠시 멈췄어요'));
    expect(msg, isNot(contains('사라져요')));
  });
}
