import 'package:bandapp_client/features/notification/application/notification_route.dart';
import 'package:bandapp_client/routing/app_router.dart';
import 'package:flutter_test/flutter_test.dart';

/// LAUNCH_REVIEW U6 — 알림을 누르면 그 알림의 화면으로 간다.
void main() {
  test('일정 알림은 그 일정으로', () {
    expect(notificationRoute('RESERVATION_REMINDER', 12), Routes.reservation(12));
    expect(notificationRoute('RESERVATION_CREATED', 12), Routes.reservation(12));
    expect(notificationRoute('ATTENDANCE_NUDGE', 12), Routes.reservation(12));
  });

  test('정산 요청은 그 일정의 정산으로', () {
    expect(notificationRoute('SETTLEMENT_REQUESTED', 7), Routes.settlement(7));
  });

  test('요금제 알림은 요금제 화면으로 — 일정 id 가 없어도 없는 일정으로 가지 않는다', () {
    expect(notificationRoute('PLAN_EXPIRING_SOON', 0), Routes.plan);
    expect(notificationRoute('PLAN_EXPIRED', null), Routes.plan);
  });

  test('취소된 일정은 달력으로, 운영자 알림은 이동 없음', () {
    expect(notificationRoute('RESERVATION_CANCELLED', 5), Routes.calendar);
    expect(notificationRoute('REPORT_RECEIVED', 3), isNull);
  });

  test('모르는 종류·id 없음은 알림 목록으로', () {
    expect(notificationRoute('SOMETHING_NEW', null), Routes.notifications);
  });
}
