import 'package:bandapp_client/core/format/formatters.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('구독 종료일은 연도까지 쓴다 (U4)', () {
    // 정오 UTC 라 어느 시간대에서 돌려도 같은 날짜다.
    final s = Fmt.dateKoWithYearUtc(DateTime.utc(2027, 9, 10, 12));
    expect(s, '2027년 9월 10일 (금)');
  });

  test('연도 없는 표기는 그대로', () {
    expect(Fmt.dateKo(DateTime(2026, 9, 10)), '9월 10일 (목)');
  });

  test('자정을 넘기는 일정은 끝 시각에 다음날을 붙인다', () {
    final start = DateTime(2026, 10, 31, 23).toUtc();
    expect(Fmt.timeRange(start, start.add(const Duration(hours: 3))), '23:00–다음날 02:00');
    expect(Fmt.timeRange(start.subtract(const Duration(hours: 4)), start), '19:00–23:00');
  });
}
