enum SplitType { equal, attendeesOnly, unknown }

SplitType splitTypeFrom(String? raw) {
  switch (raw) {
    case 'EQUAL':
      return SplitType.equal;
    case 'ATTENDEES_ONLY':
      return SplitType.attendeesOnly;
    default:
      return SplitType.unknown;
  }
}

String splitTypeWire(SplitType t) =>
    t == SplitType.attendeesOnly ? 'ATTENDEES_ONLY' : 'EQUAL';

String splitTypeLabel(SplitType t) {
  switch (t) {
    case SplitType.equal:
      return '멤버 전원 균등';
    case SplitType.attendeesOnly:
      return '참석자만 균등';
    case SplitType.unknown:
      return '-';
  }
}

/// GET /bands/{bandId}/reservations/{rid}/settlement 의 한 줄 — 멤버 한 명의 몫.
class SettlementShare {
  const SettlementShare({
    required this.userId,
    required this.name,
    required this.role,
    required this.amount,
    required this.paid,
  });

  final int userId;
  final String name;
  final String role;
  final int amount;
  final bool paid;

  bool get isLeader => role == 'LEADER';

  factory SettlementShare.fromJson(Map<String, dynamic> json) {
    return SettlementShare(
      userId: (json['userId'] as num).toInt(),
      name: json['name'] as String? ?? '(알 수 없음)',
      role: json['role'] as String? ?? 'MEMBER',
      amount: (json['amount'] as num?)?.toInt() ?? 0,
      paid: json['paid'] as bool? ?? false,
    );
  }
}

/// 일정 하나의 정산 현황 전체.
class Settlement {
  const Settlement({
    required this.settlementId,
    required this.totalAmount,
    required this.splitType,
    required this.paidCount,
    required this.paidAmount,
    required this.outstandingAmount,
    required this.shares,
  });

  final int settlementId;
  final int totalAmount;
  final SplitType splitType;
  final int paidCount;
  final int paidAmount;
  final int outstandingAmount;
  final List<SettlementShare> shares;

  int get shareCount => shares.length;

  /// 0.0 ~ 1.0. 총액이 0이면 0.
  double get paidRatio =>
      totalAmount <= 0 ? 0 : (paidAmount / totalAmount).clamp(0, 1).toDouble();

  SettlementShare? shareOf(int userId) {
    for (final s in shares) {
      if (s.userId == userId) return s;
    }
    return null;
  }

  factory Settlement.fromJson(Map<String, dynamic> json) {
    return Settlement(
      settlementId: (json['settlementId'] as num).toInt(),
      totalAmount: (json['totalAmount'] as num?)?.toInt() ?? 0,
      splitType: splitTypeFrom(json['splitType'] as String?),
      paidCount: (json['paidCount'] as num?)?.toInt() ?? 0,
      paidAmount: (json['paidAmount'] as num?)?.toInt() ?? 0,
      outstandingAmount: (json['outstandingAmount'] as num?)?.toInt() ?? 0,
      shares: (json['shares'] as List<dynamic>? ?? const [])
          .map((e) => SettlementShare.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
    );
  }
}
