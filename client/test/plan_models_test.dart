import 'package:bandapp_client/features/plan/data/plan_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('BandPlan.fromJson', () {
    test('FREE with 30-day retention', () {
      final plan = BandPlan.fromJson({
        'tier': 'FREE',
        'mediaRetentionDays': 30,
        'startedAt': '2026-01-01T00:00:00Z',
        'expiresAt': null,
      });
      expect(plan.isPremium, isFalse);
      expect(plan.retentionLabel, '30일');
      expect(plan.expiresAt, isNull);
    });

    test('PREMIUM: null retention means 무제한', () {
      final plan = BandPlan.fromJson({
        'tier': 'PREMIUM',
        'mediaRetentionDays': null,
        'startedAt': '2026-03-01T00:00:00Z',
        'expiresAt': '2026-04-01T00:00:00Z',
      });
      expect(plan.isPremium, isTrue);
      expect(plan.retentionLabel, '무제한');
      expect(plan.expiresAt, isNotNull);
    });

    test('defaults to FREE when tier missing', () {
      expect(BandPlan.fromJson(<String, dynamic>{}).tier, 'FREE');
    });
  });
}
