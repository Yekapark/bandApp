import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../band/application/band_providers.dart';
import '../application/calendar_providers.dart';
import '../data/reservation_models.dart';

/// 예약 캘린더 — 월간 뷰. 일정이 있는 날에 점, 날짜를 탭하면 그 날 일정 리스트.
class CalendarScreen extends ConsumerStatefulWidget {
  const CalendarScreen({super.key});

  @override
  ConsumerState<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends ConsumerState<CalendarScreen> {
  DateTime? _selected;

  DateTime _dayOnly(DateTime d) => DateTime(d.year, d.month, d.day);

  /// 그 달로 이동했을 때 기본 선택일: 오늘이 그 달이면 오늘, 아니면 1일.
  DateTime _defaultDayFor(DateTime month) {
    final now = DateTime.now();
    if (now.year == month.year && now.month == month.month) {
      return _dayOnly(now);
    }
    return DateTime(month.year, month.month);
  }

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    final month = ref.watch(calendarMonthProvider);
    final selected = _selected ?? _defaultDayFor(month);

    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text(
            '밴드를 먼저 선택해 주세요.',
            style: TextStyle(color: AppColors.textDim),
          ),
        ),
      );
    }

    final key = (bandId: band.id, month: month);
    final reservationsAsync = ref.watch(monthReservationsProvider(key));
    final byDay = <DateTime, List<Reservation>>{};
    for (final r in reservationsAsync.valueOrNull ?? const <Reservation>[]) {
      final d = _dayOnly(r.startAt.toLocal());
      byDay.putIfAbsent(d, () => []).add(r);
    }
    final dayItems = byDay[selected] ?? const <Reservation>[];

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '캘린더',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
        ),
        actions: [
          IconButton(
            tooltip: '정기 일정',
            icon: const Icon(Icons.repeat, size: 20),
            onPressed: () async {
              await context.push(Routes.recurring);
              if (!context.mounted) return;
              ref.invalidate(monthReservationsProvider);
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        color: AppColors.primary,
        backgroundColor: AppColors.surface,
        onRefresh: () async {
          ref.invalidate(monthReservationsProvider(key));
          await ref.read(monthReservationsProvider(key).future);
        },
        child: ListView(
          padding: const EdgeInsets.fromLTRB(18, 8, 18, 28),
          children: [
            _MonthHeader(
              month: month,
              onPrev: () => _changeMonth(-1),
              onNext: () => _changeMonth(1),
            ),
            const SizedBox(height: 14),
            const _WeekdayRow(),
            const SizedBox(height: 6),
            _MonthGrid(
              month: month,
              selected: selected,
              countByDay: {
                for (final e in byDay.entries) e.key: e.value.length,
              },
              onTapDay: (d) => setState(() => _selected = d),
            ),
            if (reservationsAsync.isLoading) ...[
              const SizedBox(height: 14),
              const Center(
                child: SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            ],
            if (reservationsAsync.hasError) ...[
              const SizedBox(height: 14),
              _InlineError(
                onRetry: () => ref.invalidate(monthReservationsProvider(key)),
              ),
            ],
            const SizedBox(height: 20),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(
                  Fmt.dateKo(selected),
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(width: 9),
                Text(
                  dayItems.isEmpty ? '일정 없음' : '${dayItems.length}건',
                  style:
                      const TextStyle(fontSize: 11.5, color: AppColors.textDim),
                ),
              ],
            ),
            const SizedBox(height: 10),
            if (dayItems.isEmpty)
              _EmptyDay()
            else
              for (final r in dayItems)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: _DayReservationTile(
                    reservation: r,
                    onTap: () => context.push(Routes.reservation(r.id)),
                  ),
                ),
            const SizedBox(height: 8),
            _AddOnDateButton(
              onTap: () => context.push(
                '${Routes.newReservation}?date=${Fmt.ymd(selected)}',
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _changeMonth(int delta) {
    final current = ref.read(calendarMonthProvider);
    final next = DateTime(current.year, current.month + delta);
    if (delta < 0) {
      ref.read(calendarMonthProvider.notifier).prev();
    } else {
      ref.read(calendarMonthProvider.notifier).next();
    }
    setState(() => _selected = _defaultDayFor(next));
  }
}

class _MonthHeader extends StatelessWidget {
  const _MonthHeader({
    required this.month,
    required this.onPrev,
    required this.onNext,
  });

  final DateTime month;
  final VoidCallback onPrev;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(
          Fmt.monthTitleKo(month),
          style: const TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w900,
            letterSpacing: -0.5,
          ),
        ),
        const Spacer(),
        _RoundIcon(icon: Icons.chevron_left, onTap: onPrev),
        const SizedBox(width: 6),
        _RoundIcon(icon: Icons.chevron_right, onTap: onNext),
      ],
    );
  }
}

class _RoundIcon extends StatelessWidget {
  const _RoundIcon({required this.icon, required this.onTap});
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 34,
        height: 34,
        decoration: BoxDecoration(
          color: AppColors.surfaceRaised,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppColors.borderStrong),
        ),
        child: Icon(icon, size: 20, color: AppColors.textSecondary),
      ),
    );
  }
}

class _WeekdayRow extends StatelessWidget {
  const _WeekdayRow();

  @override
  Widget build(BuildContext context) {
    const labels = ['일', '월', '화', '수', '목', '금', '토'];
    return Row(
      children: [
        for (final l in labels)
          Expanded(
            child: Center(
              child: Text(
                l,
                style: const TextStyle(
                  fontSize: 10.5,
                  color: AppColors.textFaint,
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _MonthGrid extends StatelessWidget {
  const _MonthGrid({
    required this.month,
    required this.selected,
    required this.countByDay,
    required this.onTapDay,
  });

  final DateTime month;
  final DateTime selected;
  final Map<DateTime, int> countByDay;
  final ValueChanged<DateTime> onTapDay;

  @override
  Widget build(BuildContext context) {
    final start = calendarGridStart(month);
    final today = DateTime.now();
    final todayOnly = DateTime(today.year, today.month, today.day);

    return GridView.count(
      crossAxisCount: 7,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 3,
      crossAxisSpacing: 3,
      childAspectRatio: 0.92,
      children: [
        for (var i = 0; i < calendarGridDays; i++)
          _buildCell(start.add(Duration(days: i)), month, selected, todayOnly),
      ],
    );
  }

  Widget _buildCell(
    DateTime day,
    DateTime month,
    DateTime selected,
    DateTime today,
  ) {
    final inMonth = day.month == month.month && day.year == month.year;
    final isSelected = day == selected;
    final isToday = day == today;
    final count = countByDay[day] ?? 0;

    Color bg;
    Color fg;
    if (isSelected) {
      bg = AppColors.primary;
      fg = AppColors.onPrimary;
    } else {
      bg = inMonth ? AppColors.surface : Colors.transparent;
      fg = inMonth ? AppColors.textPrimary : AppColors.textFaint;
    }

    return GestureDetector(
      onTap: () => onTapDay(day),
      child: Container(
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(11),
          border: isToday && !isSelected
              ? Border.all(color: AppColors.primary, width: 1.2)
              : null,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              '${day.day}',
              style: AppTypography.mono(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: fg,
              ),
            ),
            const SizedBox(height: 4),
            Container(
              width: 5,
              height: 5,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: count == 0
                    ? Colors.transparent
                    : (isSelected ? AppColors.onPrimary : AppColors.primary),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DayReservationTile extends StatelessWidget {
  const _DayReservationTile({required this.reservation, required this.onTap});
  final Reservation reservation;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final r = reservation;
    final tint = r.isPending ? AppColors.purple : AppColors.primary;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border(left: BorderSide(color: tint, width: 3)),
        ),
        child: Row(
          children: [
            SizedBox(
              width: 46,
              child: Text(
                Fmt.time(r.startAt),
                style: AppTypography.mono(fontSize: 12.5, color: tint),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    r.roomName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 13.5,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${Fmt.time(r.startAt)}–${Fmt.time(r.endAt)} · '
                    '${Fmt.durationKo(r.startAt, r.endAt)}',
                    style:
                        const TextStyle(fontSize: 11, color: AppColors.textDim),
                  ),
                ],
              ),
            ),
            Text(
              reservationStatusLabel(r.status),
              style: TextStyle(
                fontSize: 10.5,
                fontWeight: FontWeight.w700,
                color:
                    r.isPending ? AppColors.purpleSoft : AppColors.primarySoft,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyDay extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 22),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.borderFaint),
      ),
      child: const Text(
        '이 날은 등록된 합주가 없어요.',
        style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
      ),
    );
  }
}

class _AddOnDateButton extends StatelessWidget {
  const _AddOnDateButton({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 50,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.primary.withValues(alpha: 0.5)),
        ),
        child: const Text(
          '＋  이 날짜에 합주 등록',
          style: TextStyle(
            fontSize: 13.5,
            fontWeight: FontWeight.w700,
            color: AppColors.primary,
          ),
        ),
      ),
    );
  }
}

class _InlineError extends StatelessWidget {
  const _InlineError({required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Text(
          '일정을 불러오지 못했습니다.',
          style: TextStyle(fontSize: 12, color: AppColors.textDim),
        ),
        TextButton(onPressed: onRetry, child: const Text('다시 시도')),
      ],
    );
  }
}
