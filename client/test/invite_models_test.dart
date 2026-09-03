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
}
