/// 밴드 초대코드 — 백엔드 `4. 초대` 응답.
class BandInvite {
  const BandInvite({
    required this.code,
    required this.link,
    this.expiresAt,
    this.maxUses,
    required this.usedCount,
    required this.revoked,
  });

  /// 8자 영숫자.
  final String code;

  /// 공유용 링크 (앱 설치 시 앱, 미설치 시 스토어 유도 웹페이지).
  final String link;
  final DateTime? expiresAt;

  /// null = 사용 횟수 무제한.
  final int? maxUses;
  final int usedCount;
  final bool revoked;

  bool get isUnlimited => maxUses == null;
  int? get remainingUses => maxUses == null ? null : (maxUses! - usedCount);

  factory BandInvite.fromJson(Map<String, dynamic> json) {
    return BandInvite(
      code: json['code'] as String? ?? '',
      link: json['link'] as String? ?? '',
      expiresAt: json['expiresAt'] == null
          ? null
          : DateTime.parse(json['expiresAt'] as String),
      maxUses: (json['maxUses'] as num?)?.toInt(),
      usedCount: (json['usedCount'] as num?)?.toInt() ?? 0,
      revoked: json['revoked'] as bool? ?? false,
    );
  }
}
