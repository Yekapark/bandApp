/// 내 푸시 알림 설정 (`GET/PUT /api/v1/notifications/settings`).
///
/// [reminderOffsets] 는 "일정 시작 N분 전"에 리마인더를 받을 시점(분) 목록.
/// 중복·순서·개수 상한(기본 5개)·범위(1~1440분)는 서버가 정리하므로,
/// 클라이언트는 프리셋 토글만 보낸다.
class NotificationSetting {
  const NotificationSetting({
    required this.pushEnabled,
    required this.reminderOffsets,
  });

  final bool pushEnabled;
  final List<int> reminderOffsets;

  factory NotificationSetting.fromJson(Map<String, dynamic> json) {
    final raw = json['reminderOffsets'] as List<dynamic>? ?? const [];
    return NotificationSetting(
      pushEnabled: json['pushEnabled'] as bool? ?? true,
      reminderOffsets:
          raw.map((e) => (e as num).toInt()).toList(growable: false),
    );
  }
}

/// 받은 알림 한 건. 서버가 발송 시점의 문구를 그대로 보관해 돌려준다 —
/// 가리키던 일정이 나중에 바뀌거나 지워져도 알림 내용은 그대로다.
class AppNotification {
  const AppNotification({
    required this.id,
    required this.type,
    required this.reservationId,
    required this.title,
    required this.body,
    required this.sentAt,
  });

  final int id;

  /// RESERVATION_CREATED · RESERVATION_REMINDER · SETTLEMENT_REQUESTED 등.
  final String type;

  /// 눌렀을 때 이동할 일정 id.
  final int reservationId;
  final String title;
  final String body;
  final DateTime sentAt;

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    return AppNotification(
      id: (json['id'] as num).toInt(),
      type: json['type'] as String? ?? '',
      reservationId: (json['reservationId'] as num?)?.toInt() ?? 0,
      title: json['title'] as String? ?? '알림',
      body: json['body'] as String? ?? '',
      sentAt: DateTime.parse(json['sentAt'] as String).toLocal(),
    );
  }
}

/// 알림 목록 한 페이지. [nextCursor] 가 null 이면 마지막 페이지.
class NotificationPage {
  const NotificationPage({required this.items, this.nextCursor});

  final List<AppNotification> items;
  final int? nextCursor;

  factory NotificationPage.fromJson(Map<String, dynamic> json) {
    final raw = json['notifications'] as List<dynamic>? ?? const [];
    return NotificationPage(
      items: raw
          .map((e) => AppNotification.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      nextCursor: (json['nextCursor'] as num?)?.toInt(),
    );
  }
}
