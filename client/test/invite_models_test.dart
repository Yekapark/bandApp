import 'package:bandapp_client/features/band/data/invite_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('BandInvite.fromJson', () {
    test('unlimited uses when maxUses null', () {
      final invite = BandInvite.fromJson({
        'code': 'ABCD1234',
        'link': 'https://band.example/invite/ABCD1234',
        'expiresAt': '2026-09-11T00:00:00Z',
        'maxUses': null,
        'usedCount': 3,
        'revoked': false,
      });
      expect(invite.code, 'ABCD1234');
      expect(invite.isUnlimited, isTrue);
      expect(invite.remainingUses, isNull);
      expect(invite.expiresAt, isNotNull);
    });

    test('remaining uses computed from maxUses - usedCount', () {
      final invite = BandInvite.fromJson({
        'code': 'ZZ',
        'link': '',
        'maxUses': 5,
        'usedCount': 2,
        'revoked': false,
      });
      expect(invite.isUnlimited, isFalse);
      expect(invite.remainingUses, 3);
      expect(invite.expiresAt, isNull);
    });
  });

  // 서버의 "현재 코드" 는 무효화만 안 됐으면 만료·소진된 것도 돌려준다 — 그대로 보이면 못 쓰는 코드를 공유한다.
  group('BandInvite.isUsableAt', () {
    final now = DateTime.utc(2026, 10, 2, 12);
    BandInvite invite({DateTime? expiresAt, int? maxUses, int used = 0}) =>
        BandInvite(
          code: 'ABCD2345',
          link: '',
          expiresAt: expiresAt,
          maxUses: maxUses,
          usedCount: used,
          revoked: false,
        );

    test('기한 안·횟수 남음 → 쓸 수 있다', () {
      expect(invite(expiresAt: now.add(const Duration(days: 1))).isUsableAt(now),
          isTrue);
      expect(invite(maxUses: 3, used: 2).isUsableAt(now), isTrue);
    });

    test('기한이 지났거나 횟수를 다 썼으면 못 쓴다', () {
      expect(invite(expiresAt: now.subtract(const Duration(minutes: 1)))
              .isUsableAt(now),
          isFalse);
      expect(invite(maxUses: 3, used: 3).isUsableAt(now), isFalse);
    });
  });
}
