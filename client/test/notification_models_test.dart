import 'package:bandapp_client/features/notification/data/notification_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AppNotification.fromJson', () {
    test('발송 시각을 로컬로 바꿔 읽는다', () {
      final n = AppNotification.fromJson({
        'id': 128,
        'type': 'RESERVATION_REMINDER',
        'reservationId': 12,
        'title': '합주 리마인더',
        'body': '9월 4일 19:00 합주가 60분 뒤 시작해요.',
        'sentAt': '2026-09-04T10:00:00Z',
      });

      expect(n.id, 128);
      expect(n.type, 'RESERVATION_REMINDER');
      expect(n.reservationId, 12);
      expect(n.title, '합주 리마인더');
      expect(n.sentAt.isUtc, isFalse);
      expect(n.sentAt.toUtc(), DateTime.utc(2026, 9, 4, 10));
    });

    test('빠진 필드는 기본값으로 채운다', () {
      final n = AppNotification.fromJson({
        'id': 1,
        'sentAt': '2026-09-04T10:00:00Z',
      });

      expect(n.type, '');
      expect(n.reservationId, 0);
      expect(n.title, '알림');
      expect(n.body, '');
    });
  });

  group('NotificationPage.fromJson', () {
    test('목록과 다음 커서를 읽는다', () {
      final p = NotificationPage.fromJson({
        'notifications': [
          {'id': 2, 'sentAt': '2026-09-04T11:00:00Z'},
          {'id': 1, 'sentAt': '2026-09-04T10:00:00Z'},
        ],
        'nextCursor': 1,
      });

      expect(p.items, hasLength(2));
      expect(p.items.first.id, 2);
      expect(p.nextCursor, 1);
    });

    test('마지막 페이지는 nextCursor 가 null', () {
      final p = NotificationPage.fromJson({
        'notifications': <dynamic>[],
        'nextCursor': null,
      });

      expect(p.items, isEmpty);
      expect(p.nextCursor, isNull);
    });
  });
}
