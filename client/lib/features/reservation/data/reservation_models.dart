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
