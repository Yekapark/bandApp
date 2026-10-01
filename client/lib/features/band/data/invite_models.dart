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

  /// 지금 이 코드로 들어올 수 있는가. 서버의 "현재 코드" 는 무효화만 안 됐으면 **만료·소진된 것도** 돌려준다 —
  /// 그대로 보여 주면 밴드장이 못 쓰는 코드를 공유하고, 받은 사람은 "만료된 초대코드" 를 본다.
  bool isUsableAt(DateTime now) =>
      !revoked &&
      (expiresAt == null || expiresAt!.isAfter(now)) &&
      (remainingUses == null || remainingUses! > 0);

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
