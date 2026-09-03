// 정기 일정 규칙 — 백엔드 `8. 정기 일정` 응답 매핑.
//
// 규칙을 등록하면 앞으로 8주분 회차가 일반 일정(Reservation)으로 자동 생성된다.
// 개별 회차의 수정·취소는 일반 일정 API 를 그대로 쓴다.

import '../../reservation/data/reservation_models.dart';

/// 반복 주기.
enum RecurringFrequency { weekly, biweekly, monthly, unknown }

const _freqWire = {
  RecurringFrequency.weekly: 'WEEKLY',
  RecurringFrequency.biweekly: 'BIWEEKLY',
  RecurringFrequency.monthly: 'MONTHLY',
};

String recurringFrequencyWire(RecurringFrequency f) => _freqWire[f] ?? 'WEEKLY';

RecurringFrequency recurringFrequencyFrom(String? raw) {
  for (final e in _freqWire.entries) {
    if (e.value == raw) return e.key;
  }
  return RecurringFrequency.unknown;
}

String recurringFrequencyLabel(RecurringFrequency f) {
  switch (f) {
    case RecurringFrequency.weekly:
      return '매주';
    case RecurringFrequency.biweekly:
      return '격주';
    case RecurringFrequency.monthly:
      return '매월';
    case RecurringFrequency.unknown:
      return '-';
  }
}

const _dowNames = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];
const _dowKo = ['월', '화', '수', '목', '금', '토', '일'];

/// DateTime.weekday (월=1 … 일=7) → 백엔드 java.time.DayOfWeek 이름.
String dayOfWeekWire(int weekday) => _dowNames[(weekday - 1) % 7];

/// java.time.DayOfWeek 이름 → 한국어 요일 한 글자.
String dayOfWeekKo(String? name) {
  final i = _dowNames.indexOf(name ?? '');
  return i < 0 ? '-' : _dowKo[i];
}

/// "HH:mm[:ss]" → "HH:mm".
String hhmmOf(String? raw) {
  if (raw == null || raw.length < 5) return raw ?? '-';
  return raw.substring(0, 5);
}

/// GET /bands/{id}/recurring-rules 의 항목.
class RecurringRule {
  const RecurringRule({
    required this.id,
    required this.roomId,
    required this.roomName,
    required this.frequency,
    required this.dayOfWeek,
    required this.startTime,
    required this.endTime,
    required this.startDate,
    this.endDate,
    this.cost,
    this.note,
    required this.createdBy,
  });

  final int id;
  final int? roomId;
  final String roomName;
  final RecurringFrequency frequency;

  /// java.time.DayOfWeek 이름 (예: "SATURDAY").
  final String dayOfWeek;

  /// "HH:mm".
  final String startTime;
  final String endTime;

  /// "yyyy-MM-dd".
  final String startDate;
  final String? endDate;
  final int? cost;
  final String? note;
  final int createdBy;

  /// "매주 토 15:00–18:00" 형태의 한 줄.
  String get summary =>
      '${recurringFrequencyLabel(frequency)} ${dayOfWeekKo(dayOfWeek)} '
      '$startTime–$endTime';

  factory RecurringRule.fromJson(Map<String, dynamic> json) {
    return RecurringRule(
      id: (json['id'] as num).toInt(),
      roomId: (json['roomId'] as num?)?.toInt(),
      roomName: json['roomName'] as String? ?? '합주실',
      frequency: recurringFrequencyFrom(json['frequency'] as String?),
      dayOfWeek: json['dayOfWeek'] as String? ?? '',
      startTime: hhmmOf(json['startTime'] as String?),
      endTime: hhmmOf(json['endTime'] as String?),
      startDate: json['startDate'] as String? ?? '',
      endDate: json['endDate'] as String?,
      cost: (json['cost'] as num?)?.toInt(),
      note: json['note'] as String?,
      createdBy: (json['createdBy'] as num?)?.toInt() ?? 0,
    );
  }
}

/// POST /bands/{id}/recurring-rules 응답 — 규칙 + 생성된 회차 수 + 겹침 경고.
class RecurringWriteResult {
  const RecurringWriteResult({
    required this.rule,
    required this.occurrenceCount,
    required this.overlaps,
  });

  final RecurringRule rule;
  final int occurrenceCount;
  final List<OverlapWarning> overlaps;

  factory RecurringWriteResult.fromJson(Map<String, dynamic> json) {
    return RecurringWriteResult(
      rule: RecurringRule.fromJson(json['rule'] as Map<String, dynamic>),
      occurrenceCount: (json['occurrenceCount'] as num?)?.toInt() ?? 0,
      overlaps: (json['overlaps'] as List<dynamic>? ?? const [])
          .map((e) => OverlapWarning.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
    );
  }
}
