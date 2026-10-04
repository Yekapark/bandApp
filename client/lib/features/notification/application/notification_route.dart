import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../routing/app_router.dart';
import '../../reservation/application/calendar_providers.dart';
import '../../settlement/application/settlement_providers.dart';

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
    case 'RECURRING_RULE_CANCELLED':
      return Routes.calendar; // 정기 일정이 통째로 취소됐다 — 일정 id 가 없다(ruleId 만)
  }
  if (reservationId == null || reservationId <= 0) return Routes.notifications;
  // RESERVATION_CHANGED(시간·장소가 바뀜)도 여기 — 그 일정 상세로.
  if (type == 'SETTLEMENT_REQUESTED') return Routes.settlement(reservationId);
  return Routes.reservation(reservationId);
}

/// 알림의 밴드가 이미 내 목록에 없는가(나갔거나 추방·삭제, PUSH-07). 그대로 열면 "현재 밴드" 가 첫 밴드로
/// 돌아가서 요금제 알림이 **다른 밴드의 요금제**를 보여준다. 목록을 아직 못 불러왔으면(콜드 스타트, null·빈 목록)
/// 단정하지 않는다 — 일정·정산은 서버가 밴드 소속을 확인해 404 로 막는다.
bool notificationBandGone(int? bandId, Iterable<int>? myBandIds) =>
    bandId != null &&
    myBandIds != null &&
    myBandIds.isNotEmpty &&
    !myBandIds.contains(bandId);

/// 하단 탭 화면(캘린더 등). 이 화면들은 탭 껍데기(StatefulShellRoute) 안에 있어서 **push 하면 껍데기가 하나 더
/// 쌓이고 본문이 빈 화면이 된다**(U39·QA-R05 — 취소 알림을 누르면 캘린더 탭만 보이고 본문이 비었다). 탭으로 이동(go)한다.
bool isTabRoute(String route) => const {
      Routes.home,
      Routes.calendar,
      Routes.map,
      Routes.board,
      Routes.settlements,
    }.contains(route);

/// 알림을 눌러 화면을 연다. 알림은 "다른 사람이 바꿨다" 는 뜻이라 받아 둔 일정·정산 상세를 먼저 버린다 —
/// 그 화면이 이미 뒤에 열려 있으면 autoDispose 만으로는 옛 값이 남는다(U38·QA-R04).
void openNotification(GoRouter router,
    void Function(ProviderOrFamily provider) invalidate, String route) {
  invalidate(settlementProvider);
  invalidate(reservationDetailProvider);
  openNotificationRoute(router, route);
}

/// 알림이 가리키는 화면을 연다 — 탭 화면은 go, 상세·요금제처럼 위에 얹는 화면은 push(뒤로 가면 알림 목록으로).
void openNotificationRoute(GoRouter router, String route) {
  if (isTabRoute(route)) {
    router.go(route);
  } else {
    router.push(route);
  }
}
