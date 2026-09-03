enum ReservationStatus { pending, confirmed, cancelled, rejected, unknown }

ReservationStatus _statusFrom(String? raw) {
  switch (raw) {
    case 'PENDING':
      return ReservationStatus.pending;
    case 'CONFIRMED':
      return ReservationStatus.confirmed;
    case 'CANCELLED':
      return ReservationStatus.cancelled;
    case 'REJECTED':
      return ReservationStatus.rejected;
    default:
      return ReservationStatus.unknown;
  }
}

String reservationStatusLabel(ReservationStatus s) {
  switch (s) {
    case ReservationStatus.pending:
      return '승인 대기';
    case ReservationStatus.confirmed:
      return '확정';
    case ReservationStatus.cancelled:
      return '취소됨';
    case ReservationStatus.rejected:
      return '거절됨';
    case ReservationStatus.unknown:
      return '-';
  }
}

/// GET /bands/{bandId}/reservations 의 항목.
class Reservation {
  const Reservation({
    required this.id,
    required this.roomId,
    required this.roomName,
    required this.requestedBy,
    required this.status,
    required this.startAt,
    required this.endAt,
    this.cost,
    this.note,
    this.recurringRuleId,
  });

  final int id;
  final int? roomId;
  final String roomName;
  final int requestedBy;
  final ReservationStatus status;

  /// 서버는 UTC(Instant). 표시할 때 toLocal().
  final DateTime startAt;
  final DateTime endAt;
  final int? cost;
  final String? note;
  final int? recurringRuleId;

  bool get isPending => status == ReservationStatus.pending;
  bool get isConfirmed => status == ReservationStatus.confirmed;
  bool get isActive =>
      status == ReservationStatus.pending ||
      status == ReservationStatus.confirmed;

  factory Reservation.fromJson(Map<String, dynamic> json) {
    return Reservation(
      id: (json['id'] as num).toInt(),
      roomId: (json['roomId'] as num?)?.toInt(),
      roomName: json['roomName'] as String? ?? '합주실',
      requestedBy: (json['requestedBy'] as num).toInt(),
      status: _statusFrom(json['status'] as String?),
      startAt: DateTime.parse(json['startAt'] as String),
      endAt: DateTime.parse(json['endAt'] as String),
      cost: (json['cost'] as num?)?.toInt(),
      note: json['note'] as String?,
      recurringRuleId: (json['recurringRuleId'] as num?)?.toInt(),
    );
  }
}

/// 등록/수정 시 시간대가 겹치는 기존 일정. 요청이 실패한 것은 아니다 — 경고일 뿐.
class OverlapWarning {
  const OverlapWarning({
    required this.id,
    required this.roomName,
    required this.status,
    required this.startAt,
    required this.endAt,
  });

  final int id;
  final String roomName;
  final ReservationStatus status;
  final DateTime startAt;
  final DateTime endAt;

  factory OverlapWarning.fromJson(Map<String, dynamic> json) {
    return OverlapWarning(
      id: (json['id'] as num).toInt(),
      roomName: json['roomName'] as String? ?? '합주실',
      status: _statusFrom(json['status'] as String?),
      startAt: DateTime.parse(json['startAt'] as String),
      endAt: DateTime.parse(json['endAt'] as String),
    );
  }
}

/// POST/PUT /reservations 응답 — 등록된 일정 + 겹침 경고 목록.
class ReservationWriteResult {
  const ReservationWriteResult({
    required this.reservation,
    required this.overlaps,
  });

  final Reservation reservation;
  final List<OverlapWarning> overlaps;

  factory ReservationWriteResult.fromJson(Map<String, dynamic> json) {
    final overlaps = (json['overlaps'] as List<dynamic>? ?? const [])
        .map((e) => OverlapWarning.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
    return ReservationWriteResult(
      reservation:
          Reservation.fromJson(json['reservation'] as Map<String, dynamic>),
      overlaps: overlaps,
    );
  }
}

// ── 참석(RSVP) ──────────────────────────────────────────────────────────

enum AttendanceStatus { attending, absent, pending, unknown }

AttendanceStatus attendanceStatusFrom(String? raw) {
  switch (raw) {
    case 'ATTENDING':
      return AttendanceStatus.attending;
    case 'ABSENT':
      return AttendanceStatus.absent;
    case 'PENDING':
      return AttendanceStatus.pending;
    default:
      return AttendanceStatus.unknown;
  }
}

String attendanceStatusWire(AttendanceStatus s) {
  switch (s) {
    case AttendanceStatus.attending:
      return 'ATTENDING';
    case AttendanceStatus.absent:
      return 'ABSENT';
    case AttendanceStatus.pending:
    case AttendanceStatus.unknown:
      return 'PENDING';
  }
}

String attendanceStatusLabel(AttendanceStatus s) {
  switch (s) {
    case AttendanceStatus.attending:
      return '참석';
    case AttendanceStatus.absent:
      return '불참';
    case AttendanceStatus.pending:
    case AttendanceStatus.unknown:
      return '미정';
  }
}

class AttendanceEntry {
  const AttendanceEntry({
    required this.userId,
    required this.name,
    required this.role,
    required this.status,
  });

  final int userId;
  final String name;
  final String role;
  final AttendanceStatus status;

  bool get isLeader => role == 'LEADER';

  factory AttendanceEntry.fromJson(Map<String, dynamic> json) {
    return AttendanceEntry(
      userId: (json['userId'] as num).toInt(),
      name: json['name'] as String? ?? '탈퇴한 사용자',
      role: json['role'] as String? ?? 'MEMBER',
      status: attendanceStatusFrom(json['status'] as String?),
    );
  }
}

class AttendanceBoard {
  const AttendanceBoard({
    required this.attendingCount,
    required this.memberCount,
    required this.members,
  });

  final int attendingCount;
  final int memberCount;
  final List<AttendanceEntry> members;

  int get absentCount =>
      members.where((m) => m.status == AttendanceStatus.absent).length;
  int get pendingCount => memberCount - attendingCount - absentCount;

  AttendanceStatus statusOf(int userId) {
    for (final m in members) {
      if (m.userId == userId) return m.status;
    }
    return AttendanceStatus.pending;
  }

  factory AttendanceBoard.fromJson(Map<String, dynamic> json) {
    return AttendanceBoard(
      attendingCount: (json['attendingCount'] as num?)?.toInt() ?? 0,
      memberCount: (json['memberCount'] as num?)?.toInt() ?? 0,
      members: (json['members'] as List<dynamic>? ?? const [])
          .map((e) => AttendanceEntry.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
    );
  }
}

// ── 셋리스트 ────────────────────────────────────────────────────────────

class SetlistItem {
  const SetlistItem({
    required this.id,
    required this.title,
    this.artist,
    this.referenceUrl,
    required this.orderNo,
  });

  final int id;
  final String title;
  final String? artist;
  final String? referenceUrl;
  final int orderNo;

  factory SetlistItem.fromJson(Map<String, dynamic> json) {
    return SetlistItem(
      id: (json['id'] as num).toInt(),
      title: json['title'] as String? ?? '',
      artist: json['artist'] as String?,
      referenceUrl: json['referenceUrl'] as String?,
      orderNo: (json['orderNo'] as num?)?.toInt() ?? 0,
    );
  }
}

class Setlist {
  const Setlist({required this.items});

  final List<SetlistItem> items;

  int get count => items.length;

  factory Setlist.fromJson(Map<String, dynamic> json) {
    return Setlist(
      items: (json['items'] as List<dynamic>? ?? const [])
          .map((e) => SetlistItem.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
    );
  }

  static const empty = Setlist(items: []);
}

// ── 일정 상세 ──────────────────────────────────────────────────────────

/// GET /bands/{bandId}/reservations/{id} — 일정 + 참석 현황 + 셋리스트.
class ReservationDetail {
  const ReservationDetail({
    required this.reservation,
    required this.attendance,
    required this.setlist,
  });

  final Reservation reservation;
  final AttendanceBoard attendance;
  final Setlist setlist;

  factory ReservationDetail.fromJson(Map<String, dynamic> json) {
    return ReservationDetail(
      reservation: Reservation.fromJson(json),
      attendance: AttendanceBoard.fromJson(
        (json['attendance'] as Map<String, dynamic>?) ?? const {},
      ),
      setlist: json['setlist'] == null
          ? Setlist.empty
          : Setlist.fromJson(json['setlist'] as Map<String, dynamic>),
    );
  }
}
