import 'package:intl/intl.dart';

/// 화면 표기용 포매터 모음. 서버는 UTC `Instant`(ISO-8601)를 주므로 항상 `toLocal()`.
class Fmt {
  const Fmt._();

  static final _won = NumberFormat.decimalPattern('ko_KR');
  static const _dows = ['월', '화', '수', '목', '금', '토', '일'];

  /// 45000 -> "₩ 45,000"
  static String won(num? amount) {
    if (amount == null) return '-';
    return '₩ ${_won.format(amount)}';
  }

  /// 2026-08-14T10:00:00Z -> "8월 14일 (금) 19:00"
  static String dateTimeKo(DateTime utc) {
    final d = utc.toLocal();
    return '${d.month}월 ${d.day}일 (${_dows[d.weekday - 1]}) '
        '${_two(d.hour)}:${_two(d.minute)}';
  }

  /// "19:00"
  static String time(DateTime utc) {
    final d = utc.toLocal();
    return '${_two(d.hour)}:${_two(d.minute)}';
  }

  /// "23:00–다음날 02:00". 자정을 넘기면 끝 시각이 다음 날임을 밝힌다 — 일정은 시작일에만 보여서
  /// "23:00–02:00" 만으로는 끝나는 날이 헷갈렸다(CAL-09).
  static String timeRange(DateTime startUtc, DateTime endUtc) {
    final s = startUtc.toLocal();
    final e = endUtc.toLocal();
    final nextDay = DateTime(e.year, e.month, e.day).isAfter(DateTime(s.year, s.month, s.day));
    return '${time(startUtc)}–${nextDay ? '다음날 ' : ''}${time(endUtc)}';
  }

  /// "14" (일)
  static String day(DateTime utc) => utc.toLocal().day.toString();

  /// "금"
  static String dow(DateTime utc) => _dows[utc.toLocal().weekday - 1];

  /// 이미 로컬인 날짜를 "2026년 9월" 로. (캘린더 헤더)
  static String monthTitleKo(DateTime local) =>
      '${local.year}년 ${local.month}월';

  /// 이미 로컬인 날짜를 "9월 10일 (목)" 로.
  static String dateKo(DateTime local) =>
      '${local.month}월 ${local.day}일 (${_dows[local.weekday - 1]})';

  /// 올해면 "3월 1일 (월)", 아니면 "2027년 3월 1일 (월)" — 정기 일정 종료일처럼 해를 넘길 수 있는 날짜.
  static String dateKoMaybeYear(DateTime local) => local.year == DateTime.now().year
      ? dateKo(local)
      : '${local.year}년 ${dateKo(local)}';

  /// UTC 를 로컬로 바꿔 "9월 10일 (목)" 로.
  static String dateKoUtc(DateTime utc) => dateKo(utc.toLocal());

  /// UTC 를 로컬로 바꿔 "2027년 9월 10일 (금)" 로 — 1년 뒤처럼 올해가 아닐 수 있는 날짜(구독 종료일 등).
  /// 연도가 없으면 연간 구독의 종료일이 "올해 9월" 로 읽힌다(LAUNCH_REVIEW U4).
  static String dateKoWithYearUtc(DateTime utc) {
    final d = utc.toLocal();
    return '${d.year}년 ${dateKo(d)}';
  }

  /// 로컬 날짜를 "2026-09-10" 로 (쿼리 파라미터·라우트용).
  static String ymd(DateTime local) =>
      '${local.year.toString().padLeft(4, '0')}-'
      '${_two(local.month)}-${_two(local.day)}';

  /// 로컬 시각을 "19:00" 로.
  static String hhmm(DateTime local) =>
      '${_two(local.hour)}:${_two(local.minute)}';

  /// 두 시각 사이를 "3시간" / "1시간 30분" 형태로.
  static String durationKo(DateTime startUtc, DateTime endUtc) {
    final mins = endUtc.difference(startUtc).inMinutes;
    if (mins <= 0) return '-';
    final h = mins ~/ 60;
    final m = mins % 60;
    if (h == 0) return '$m분';
    if (m == 0) return '$h시간';
    return '$h시간 $m분';
  }

  static String _two(int v) => v.toString().padLeft(2, '0');
}
