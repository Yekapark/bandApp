import 'package:bandapp_client/features/recurring/data/recurring_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('frequency wire mapping', () {
    test('round-trips known values', () {
      for (final f in [
        RecurringFrequency.weekly,
        RecurringFrequency.biweekly,
        RecurringFrequency.monthly,
      ]) {
        expect(recurringFrequencyFrom(recurringFrequencyWire(f)), f);
      }
    });

    test('unknown wire value maps to unknown', () {
      expect(recurringFrequencyFrom('YEARLY'), RecurringFrequency.unknown);
    });
  });

  group('day-of-week helpers', () {
    test('DateTime.weekday → java DayOfWeek name', () {
      expect(dayOfWeekWire(1), 'MONDAY'); // 월
      expect(dayOfWeekWire(6), 'SATURDAY'); // 토
      expect(dayOfWeekWire(7), 'SUNDAY'); // 일
    });

    test('name → 한국어 한 글자', () {
      expect(dayOfWeekKo('SATURDAY'), '토');
      expect(dayOfWeekKo(null), '-');
      expect(dayOfWeekKo('NOPE'), '-');
    });
  });

  test('hhmmOf trims seconds', () {
    expect(hhmmOf('15:00:00'), '15:00');
    expect(hhmmOf('15:30'), '15:30');
    expect(hhmmOf(null), '-');
  });

  group('RecurringRule.fromJson', () {
    test('parses and builds a summary line', () {
      final rule = RecurringRule.fromJson({
        'id': 3,
        'roomId': 1,
        'roomName': '사운드박스 B',
        'frequency': 'BIWEEKLY',
        'dayOfWeek': 'SATURDAY',
        'startTime': '15:00:00',
        'endTime': '18:00:00',
        'startDate': '2026-09-05',
        'endDate': null,
        'cost': 30000,
        'note': '정기 합주',
        'createdBy': 9,
      });

      expect(rule.frequency, RecurringFrequency.biweekly);
      expect(rule.startTime, '15:00');
      expect(rule.summary, '격주 토 15:00–18:00');
      expect(rule.createdBy, 9);
    });
  });

  group('RecurringRuleDetail.fromJson', () {
    test('parses rule + occurrence list', () {
      final detail = RecurringRuleDetail.fromJson({
        'rule': {
          'id': 3,
          'roomId': 1,
          'roomName': '사운드박스 B',
          'frequency': 'WEEKLY',
          'dayOfWeek': 'SATURDAY',
          'startTime': '15:00',
          'endTime': '18:00',
          'startDate': '2026-09-05',
          'createdBy': 9,
        },
        'occurrenceCount': 2,
        'occurrences': [
          {
            'id': 100,
            'roomId': 1,
            'roomName': '사운드박스 B',
            'requestedBy': 9,
            'status': 'CONFIRMED',
            'startAt': '2026-09-05T06:00:00Z',
            'endAt': '2026-09-05T09:00:00Z',
          },
          {
            'id': 101,
            'roomId': 1,
            'roomName': '사운드박스 B',
            'requestedBy': 9,
            'status': 'CANCELLED',
            'startAt': '2026-09-12T06:00:00Z',
            'endAt': '2026-09-12T09:00:00Z',
          },
        ],
      });

      expect(detail.rule.id, 3);
      expect(detail.occurrenceCount, 2);
      expect(detail.occurrences, hasLength(2));
      expect(detail.occurrences.first.isActive, isTrue);
      expect(detail.occurrences[1].isActive, isFalse);
    });
  });

  group('RecurringWriteResult.fromJson', () {
    test('parses occurrence count and overlaps', () {
      final result = RecurringWriteResult.fromJson({
        'rule': {
          'id': 3,
          'roomId': 1,
          'roomName': '사운드박스 B',
          'frequency': 'WEEKLY',
          'dayOfWeek': 'SATURDAY',
          'startTime': '15:00',
          'endTime': '18:00',
          'startDate': '2026-09-05',
          'createdBy': 9,
        },
        'occurrenceCount': 8,
        'overlaps': [
          {
            'id': 50,
            'roomName': '사운드박스 B',
            'status': 'CONFIRMED',
            'startAt': '2026-09-12T06:00:00Z',
            'endAt': '2026-09-12T09:00:00Z',
          },
        ],
      });

      expect(result.occurrenceCount, 8);
      expect(result.overlaps, hasLength(1));
      expect(result.overlaps.first.roomName, '사운드박스 B');
    });
  });

  group('매월 몇째 주 (U26)', () {
    test('5번째 그 요일에서 시작하면 "마지막 주" 로 안내한다', () {
      // 2026-10-31 은 10월의 5번째 토요일.
      final hint = monthlyRuleHint(DateTime(2026, 10, 31), DateTime.saturday);
      expect(hint, contains('매월 마지막 주 토요일'));
      expect(hint, isNot(contains('건너뛰')));
    });

    test('1~4번째는 "N째 주" 로 안내한다', () {
      expect(monthlyRuleHint(DateTime(2026, 10, 10), DateTime.saturday),
          contains('매월 2째 주 토요일'));
    });

    test('서버 monthlyWeek -1 은 목록 한 줄에 "마지막 주" 로 보인다', () {
      final rule = RecurringRule.fromJson({
        'id': 1,
        'frequency': 'MONTHLY',
        'dayOfWeek': 'SATURDAY',
        'startTime': '15:00:00',
        'endTime': '18:00:00',
        'startDate': '2026-10-31',
        'monthlyWeek': -1,
      });
      expect(rule.summary, '매월 마지막 주 토 15:00–18:00');
    });

    test('매주 규칙(monthlyWeek null)은 주차를 붙이지 않는다', () {
      final rule = RecurringRule.fromJson({
        'id': 1,
        'frequency': 'WEEKLY',
        'dayOfWeek': 'SATURDAY',
        'startTime': '15:00',
        'endTime': '18:00',
        'startDate': '2026-10-31',
      });
      expect(rule.summary, '매주 토 15:00–18:00');
    });
  });
}
