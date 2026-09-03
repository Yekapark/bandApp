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
