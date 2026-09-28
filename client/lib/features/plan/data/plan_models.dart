// 밴드 요금제 — 백엔드 `16. 요금제` 응답.

class BandPlan {
  const BandPlan({
    required this.tier,
    this.mediaRetentionDays,
    this.startedAt,
    this.expiresAt,
    this.canceled = false,
    this.autoRenewing = false,
  });

  /// FREE | PREMIUM
  final String tier;

  /// 첨부 미디어 보관일수. FREE=30, PREMIUM 은 무제한이라 null.
  final int? mediaRetentionDays;
  final DateTime? startedAt;

  /// PREMIUM 구독기간 종료 시각. FREE 면 null.
  final DateTime? expiresAt;

  /// 해지 예약됨. PREMIUM 인 채로 true 면 "결제한 기간은 남아 있지만 갱신하지 않는다" 는 뜻이다.
  /// 해지해도 [expiresAt] 까지는 혜택이 그대로고, 그 뒤에 서버 배치가 무료로 내린다.
  final bool canceled;

  /// Google Play 자동 갱신 구독 중(해지 예약 아님). 이 밴드는 다음 해에도 청구되므로 서버가 삭제를 막고,
  /// 앱은 삭제·위임·탈퇴 창에서 먼저 안내한다(LAUNCH_REVIEW B5). 쿠폰 프리미엄은 false.
  final bool autoRenewing;

  bool get isPremium => tier == 'PREMIUM';

  String get retentionLabel =>
      mediaRetentionDays == null ? '무제한' : '$mediaRetentionDays일';

  factory BandPlan.fromJson(Map<String, dynamic> json) {
    return BandPlan(
      tier: json['tier'] as String? ?? 'FREE',
      mediaRetentionDays: (json['mediaRetentionDays'] as num?)?.toInt(),
      startedAt: json['startedAt'] == null
          ? null
          : DateTime.parse(json['startedAt'] as String),
      expiresAt: json['expiresAt'] == null
          ? null
          : DateTime.parse(json['expiresAt'] as String),
      canceled: json['canceled'] as bool? ?? false,
      autoRenewing: json['autoRenewing'] as bool? ?? false,
    );
  }
}
