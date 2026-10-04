import 'package:bandapp_client/features/notification/application/notification_route.dart';
import 'package:bandapp_client/routing/app_router.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

/// LAUNCH_REVIEW U6 — 알림을 누르면 그 알림의 화면으로 간다.
void main() {
  test('일정 알림은 그 일정으로', () {
    expect(notificationRoute('RESERVATION_REMINDER', 12), Routes.reservation(12));
    expect(notificationRoute('RESERVATION_CREATED', 12), Routes.reservation(12));
    expect(notificationRoute('ATTENDANCE_NUDGE', 12), Routes.reservation(12));
    expect(notificationRoute('RESERVATION_CHANGED', 12), Routes.reservation(12));
  });

  test('정산 요청은 그 일정의 정산으로', () {
    expect(notificationRoute('SETTLEMENT_REQUESTED', 7), Routes.settlement(7));
  });

  test('요금제 알림은 요금제 화면으로 — 일정 id 가 없어도 없는 일정으로 가지 않는다', () {
    expect(notificationRoute('PLAN_EXPIRING_SOON', 0), Routes.plan);
    expect(notificationRoute('PLAN_EXPIRED', null), Routes.plan);
    expect(notificationRoute('PLAN_PURCHASER_LEFT', null), Routes.plan);
  });

  test('취소된 일정은 달력으로, 운영자 알림은 이동 없음', () {
    expect(notificationRoute('RESERVATION_CANCELLED', 5), Routes.calendar);
    expect(notificationRoute('REPORT_RECEIVED', 3), isNull);
    expect(notificationRoute('RECURRING_RULE_CANCELLED', null), Routes.calendar);
  });

  test('모르는 종류·id 없음은 알림 목록으로', () {
    expect(notificationRoute('SOMETHING_NEW', null), Routes.notifications);
  });

  test('나간 밴드의 알림은 열지 않는다 — 목록을 못 불러왔으면 단정하지 않는다 (PUSH-07)', () {
    expect(notificationBandGone(3, [1, 2]), isTrue);
    expect(notificationBandGone(2, [1, 2]), isFalse);
    expect(notificationBandGone(3, null), isFalse);
    expect(notificationBandGone(3, const []), isFalse);
    expect(notificationBandGone(null, [1]), isFalse);
  });

  // U39·QA-R05 — 탭 화면을 push 하면 탭 껍데기가 하나 더 쌓여 본문이 빈 화면이 됐다(go_router assertion).
  testWidgets('취소 알림 → 캘린더 탭이 실제로 보인다, 일정 알림 → 상세가 위에 얹힌다', (tester) async {
    final router = GoRouter(initialLocation: Routes.home, routes: [
      StatefulShellRoute.indexedStack(
        builder: (_, __, shell) => Scaffold(body: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: Routes.home, builder: (_, __) => const Text('HOME')),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: Routes.calendar, builder: (_, __) => const Text('CAL')),
          ]),
        ],
      ),
      GoRoute(
          path: Routes.notifications,
          builder: (_, __) => const Scaffold(body: Text('NOTI'))),
      GoRoute(
          path: '/reservations/:id',
          builder: (_, s) => Scaffold(body: Text('R${s.pathParameters['id']}'))),
    ]);
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    router.push(Routes.notifications);
    await tester.pumpAndSettle();

    openNotificationRoute(router, notificationRoute('RESERVATION_CANCELLED', 5)!);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('CAL').hitTestable(), findsOneWidget);

    router.push(Routes.notifications);
    await tester.pumpAndSettle();
    openNotificationRoute(router, Routes.reservation(12));
    await tester.pumpAndSettle();
    expect(find.text('R12').hitTestable(), findsOneWidget);
    router.pop(); // 상세는 위에 얹혔으므로 뒤로 가면 알림 목록
    await tester.pumpAndSettle();
    expect(find.text('NOTI').hitTestable(), findsOneWidget);
  });
}
