import '../../../routing/app_router.dart';

/// 알림을 눌렀을 때 갈 화면(LAUNCH_REVIEW U6). 푸시 알림을 눌렀을 때와 앱 안 알림 목록을 눌렀을 때가
/// **같은 규칙**을 쓴다 — 예전에는 목록이 모든 알림을 `/reservations/{id}` 로 보내서, 요금제 알림(일정 id 가
/// 없어 0)은 없는 일정 화면으로 갔고, 푸시는 눌러도 앱만 열렸다.
///
/// [type] 은 서버 `NotificationType` 이름, [reservationId] 는 일정 알림일 때만 있다(없으면 null 또는 0).
/// null 을 돌려주면 따로 갈 곳이 없다는 뜻이다.
String? notificationRoute(String? type, int? reservationId) {
  switch (type) {
    case 'PLAN_EXPIRING_SOON':
    case 'PLAN_EXPIRED':
    case 'PLAN_PURCHASER_LEFT': // 결제한 사람이 나가 갱신이 꺼졌다 — 밴드장에게 요금제 화면을 보인다
      return Routes.plan;
    case 'REPORT_RECEIVED':
      return null; // 운영자용 — 처리는 tools/moderate.py 로 한다
    case 'RESERVATION_CANCELLED':
      return Routes.calendar; // 취소된 일정은 상세가 비어 있다 — 달력에서 바뀐 일정을 본다
  }
  if (reservationId == null || reservationId <= 0) return Routes.notifications;
  if (type == 'SETTLEMENT_REQUESTED') return Routes.settlement(reservationId);
  return Routes.reservation(reservationId);
}
