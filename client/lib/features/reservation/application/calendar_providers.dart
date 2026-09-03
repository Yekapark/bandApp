import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/reservation_models.dart';
import '../data/reservation_repository.dart';
import '../data/room_models.dart';
import '../data/room_repository.dart';

/// 캘린더가 보고 있는 달(항상 1일 00:00 로컬로 정규화).
final calendarMonthProvider =
    NotifierProvider<CalendarMonth, DateTime>(CalendarMonth.new);

class CalendarMonth extends Notifier<DateTime> {
  @override
  DateTime build() {
    final now = DateTime.now();
    return DateTime(now.year, now.month);
  }

  void next() => state = DateTime(state.year, state.month + 1);
  void prev() => state = DateTime(state.year, state.month - 1);
  void jumpTo(DateTime day) => state = DateTime(day.year, day.month);
}

/// 월간 그리드는 6주(42칸). 그 달 1일이 포함된 주의 일요일부터 시작한다.
DateTime calendarGridStart(DateTime month) {
  final first = DateTime(month.year, month.month);
  final offset = first.weekday % 7; // Sun=0, Mon=1 … Sat=6
  return first.subtract(Duration(days: offset));
}

const calendarGridDays = 42;

typedef _MonthKey = ({int bandId, DateTime month});

/// 캘린더에서 취소·거절된 일정도 표시할지. 기본 false.
final showCancelledReservationsProvider =
    NotifierProvider<ShowCancelledReservations, bool>(
        ShowCancelledReservations.new);

class ShowCancelledReservations extends Notifier<bool> {
  @override
  bool build() => false;
  void toggle() => state = !state;
}

/// 캘린더 그리드에 걸치는 일정. [showCancelledReservationsProvider] 가 true 면 취소·거절 건도 포함.
final monthReservationsProvider =
    FutureProvider.family<List<Reservation>, _MonthKey>((ref, key) async {
  final start = calendarGridStart(key.month);
  final end = start.add(const Duration(days: calendarGridDays));
  final includeInactive = ref.watch(showCancelledReservationsProvider);
  return ref.watch(reservationRepositoryProvider).list(
        bandId: key.bandId,
        from: start,
        to: end,
        includeInactive: includeInactive,
      );
});

/// 밴드의 합주실 목록(usageCount 내림차순).
final roomsProvider =
    FutureProvider.family<List<Room>, int>((ref, bandId) async {
  return ref.watch(roomRepositoryProvider).list(bandId);
});

typedef ReservationKey = ({int bandId, int reservationId});

/// 일정 상세(참석 현황·셋리스트 포함).
final reservationDetailProvider =
    FutureProvider.family<ReservationDetail, ReservationKey>((ref, key) async {
  return ref.watch(reservationRepositoryProvider).detail(
        bandId: key.bandId,
        reservationId: key.reservationId,
      );
});
