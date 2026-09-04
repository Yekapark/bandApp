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

/// GET /bands/{bandId}/settlements 의 한 줄 — 정산 한 건과 그 안에서의 내 몫.
///
/// 일정 상세를 하나씩 열지 않고 "내가 아직 안 낸 돈"을 보기 위한 목록용 모델이라,
/// 멤버별 몫 전체는 담지 않는다(그건 [Settlement] 가 한다).
class BandSettlementItem {
  const BandSettlementItem({
    required this.settlementId,
    required this.reservationId,
    required this.startAt,
    required this.roomName,
    required this.totalAmount,
    required this.shareCount,
    required this.paidCount,
    this.myAmount,
    this.myPaid,
  });

  final int settlementId;
  final int reservationId;
  final DateTime startAt;

  /// 삭제된 합주실이면 null.
  final String? roomName;
  final int totalAmount;
  final int shareCount;
  final int paidCount;

  /// 내가 이 정산의 분담 대상이 아니면 null (정산 후 합류한 멤버 등).
  final int? myAmount;
  final bool? myPaid;

  bool get isMine => myAmount != null;
  bool get iStillOwe => myAmount != null && myPaid == false;
  bool get allPaid => shareCount > 0 && paidCount == shareCount;

  factory BandSettlementItem.fromJson(Map<String, dynamic> json) {
    return BandSettlementItem(
      settlementId: (json['settlementId'] as num).toInt(),
      reservationId: (json['reservationId'] as num).toInt(),
      startAt: DateTime.parse(json['startAt'] as String).toLocal(),
      roomName: json['roomName'] as String?,
      totalAmount: (json['totalAmount'] as num?)?.toInt() ?? 0,
      shareCount: (json['shareCount'] as num?)?.toInt() ?? 0,
      paidCount: (json['paidCount'] as num?)?.toInt() ?? 0,
      myAmount: (json['myAmount'] as num?)?.toInt(),
      myPaid: json['myPaid'] as bool?,
    );
  }
}

/// 밴드 정산 목록 한 페이지.
class BandSettlementPage {
  const BandSettlementPage({
    required this.items,
    required this.myOutstandingTotal,
    this.nextCursor,
  });

  final List<BandSettlementItem> items;

  /// 이 목록에서 내가 아직 안 낸 금액의 합.
  final int myOutstandingTotal;
  final int? nextCursor;

  factory BandSettlementPage.fromJson(Map<String, dynamic> json) {
    final raw = json['settlements'] as List<dynamic>? ?? const [];
    return BandSettlementPage(
      items: raw
          .map((e) => BandSettlementItem.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      myOutstandingTotal: (json['myOutstandingTotal'] as num?)?.toInt() ?? 0,
      nextCursor: (json['nextCursor'] as num?)?.toInt(),
    );
  }
}
