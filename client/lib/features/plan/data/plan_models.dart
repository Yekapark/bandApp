// 밴드 요금제 — 백엔드 `16. 요금제` 응답.

class BandPlan {
  const BandPlan({
    required this.tier,
    this.mediaRetentionDays,
    this.startedAt,
    this.expiresAt,
  });

  /// FREE | PREMIUM
  final String tier;

  /// 첨부 미디어 보관일수. FREE=30, PREMIUM 은 무제한이라 null.
  final int? mediaRetentionDays;
  final DateTime? startedAt;

  /// PREMIUM 구독기간 종료 시각. FREE 면 null.
  final DateTime? expiresAt;

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
    );
  }
}
