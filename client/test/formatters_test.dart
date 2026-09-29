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
}
