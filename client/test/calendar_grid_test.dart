import 'package:bandapp_client/core/format/formatters.dart';
import 'package:bandapp_client/features/reservation/application/calendar_providers.dart';
import 'package:flutter_test/flutter_test.dart';

/// QA CAL-09 — 월·연도 경계 달력 격자와 자정을 넘는 일정 길이.
void main() {
  test('격자는 그 달 1일이 든 주의 일요일부터 6주', () {
    // 2027-01-01 은 금요일 → 2026-12-27(일)부터. 연도를 넘어가도 앞 달 끝이 들어온다.
    expect(calendarGridStart(DateTime(2027, 1)), DateTime(2026, 12, 27));
    // 1일이 일요일이면 그날부터(2026-11-01).
    expect(calendarGridStart(DateTime(2026, 11)), DateTime(2026, 11, 1));
    // 12월 + 1 달은 다음 해 1월로 정리된다(달 이동 화살표).
    expect(DateTime(2026, 12 + 1), DateTime(2027, 1));
    expect(calendarGridStart(DateTime(2027, 1)).add(const Duration(days: calendarGridDays)),
        DateTime(2027, 2, 7));
  });

  test('자정을 넘는 일정도 길이를 바르게 센다', () {
    final start = DateTime.utc(2026, 12, 31, 14); // KST 23:00
    final end = DateTime.utc(2026, 12, 31, 17); //   KST 다음 날 02:00
    expect(Fmt.durationKo(start, end), '3시간');
  });

  test('해를 넘기는 날짜에는 연도를 붙인다 (정기 일정 시작·종료일)', () {
    final now = DateTime.now();
    expect(Fmt.dateKoMaybeYear(DateTime(now.year, 3, 1)), isNot(contains('년')));
    expect(Fmt.dateKoMaybeYear(DateTime(now.year + 1, 3, 1)), startsWith('${now.year + 1}년 3월 1일'));
  });
}
