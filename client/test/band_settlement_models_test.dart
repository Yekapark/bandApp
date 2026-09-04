import 'package:bandapp_client/features/settlement/data/settlement_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Map<String, dynamic> row({int? myAmount, bool? myPaid, String? roomName}) => {
        'settlementId': 7,
        'reservationId': 12,
        'startAt': '2026-09-10T10:00:00Z',
        'roomName': roomName,
        'totalAmount': 90000,
        'shareCount': 3,
        'paidCount': 1,
        'myAmount': myAmount,
        'myPaid': myPaid,
      };

  group('BandSettlementItem', () {
    test('내 몫이 있고 미납이면 iStillOwe', () {
      final s = BandSettlementItem.fromJson(row(myAmount: 30000, myPaid: false));

      expect(s.isMine, isTrue);
      expect(s.iStillOwe, isTrue);
      expect(s.allPaid, isFalse);
      expect(s.startAt.isUtc, isFalse);
    });

    test('납부했으면 iStillOwe 가 false', () {
      final s = BandSettlementItem.fromJson(row(myAmount: 30000, myPaid: true));

      expect(s.iStillOwe, isFalse);
    });

    test('분담 대상이 아니면 isMine 이 false 이고 몫이 null', () {
      final s = BandSettlementItem.fromJson(row());

      expect(s.isMine, isFalse);
      expect(s.iStillOwe, isFalse);
      expect(s.myAmount, isNull);
    });

    test('전원 납부면 allPaid', () {
      final j = row(myAmount: 30000, myPaid: true)..['paidCount'] = 3;

      expect(BandSettlementItem.fromJson(j).allPaid, isTrue);
    });

    test('삭제된 합주실이면 roomName 이 null', () {
      expect(BandSettlementItem.fromJson(row()).roomName, isNull);
    });
  });

  group('BandSettlementPage', () {
    test('목록·미납합계·커서를 읽는다', () {
      final p = BandSettlementPage.fromJson({
        'settlements': [row(myAmount: 30000, myPaid: false)],
        'myOutstandingTotal': 30000,
        'nextCursor': 7,
      });

      expect(p.items, hasLength(1));
      expect(p.myOutstandingTotal, 30000);
      expect(p.nextCursor, 7);
    });

    test('빈 목록도 안전하게 읽는다', () {
      final p = BandSettlementPage.fromJson({'settlements': <dynamic>[]});

      expect(p.items, isEmpty);
      expect(p.myOutstandingTotal, 0);
      expect(p.nextCursor, isNull);
    });
  });
}
