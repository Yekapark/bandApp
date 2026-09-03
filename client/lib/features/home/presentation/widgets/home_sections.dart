import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/format/formatters.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../routing/app_router.dart';
import '../../../../shared/widgets/soon.dart';
import '../../../band/data/band_models.dart';
import '../../../reservation/data/reservation_models.dart';

/// 멤버 아바타 가로 레일.
class MemberRail extends StatelessWidget {
  const MemberRail({super.key, required this.membersAsync});
  final AsyncValue<List<BandMember>> membersAsync;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 84,
      child: membersAsync.when(
        loading: () => const _RailSkeleton(),
        error: (_, __) => const Align(
          alignment: Alignment.centerLeft,
          child: Text('멤버를 불러오지 못했습니다.',
              style: TextStyle(fontSize: 12, color: AppColors.textDim)),
        ),
        data: (members) => ListView.separated(
          scrollDirection: Axis.horizontal,
          itemCount: members.length,
          separatorBuilder: (_, __) => const SizedBox(width: 8),
          itemBuilder: (_, i) => _MemberChip(member: members[i]),
        ),
      ),
    );
  }
}

class _MemberChip extends StatelessWidget {
  const _MemberChip({required this.member});
  final BandMember member;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 58,
      child: Column(
        children: [
          Container(
            width: 50,
            height: 50,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.surfaceAlt,
              border: Border.all(
                color: member.isLeader
                    ? AppColors.primary
                    : AppColors.borderStrong,
                width: 1.5,
              ),
            ),
            alignment: Alignment.center,
            child: Text(
              member.initial,
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color:
                    member.isLeader ? AppColors.primary : AppColors.textPrimary,
              ),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            member.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w500),
          ),
        ],
      ),
    );
  }
}

class _RailSkeleton extends StatelessWidget {
  const _RailSkeleton();

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      scrollDirection: Axis.horizontal,
      itemCount: 4,
      separatorBuilder: (_, __) => const SizedBox(width: 8),
      itemBuilder: (_, __) => Column(
        children: [
          Container(
            width: 50,
            height: 50,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.surfaceAlt,
            ),
          ),
          const SizedBox(height: 6),
          Container(width: 34, height: 10, color: AppColors.surfaceAlt),
        ],
      ),
    );
  }
}

/// 상단 "다음 합주" 카드 (오렌지 그라디언트). 일정이 없으면 안내 카드.
class NextRehearsalCard extends StatelessWidget {
  const NextRehearsalCard({super.key, required this.reservation});
  final Reservation? reservation;

  @override
  Widget build(BuildContext context) {
    final r = reservation;
    if (r == null) {
      return Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: AppColors.surfaceRaised,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: AppColors.borderStrong),
        ),
        child: Row(
          children: [
            const Expanded(
              child: Text(
                '예정된 합주가 없어요.\n캘린더에서 일정을 기록해 보세요.',
                style: TextStyle(
                    fontSize: 13, height: 1.5, color: AppColors.textDim),
              ),
            ),
            TextButton(
              onPressed: () => context.push(Routes.newReservation),
              child: const Text('일정 추가',
                  style: TextStyle(color: AppColors.primary)),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [AppColors.primary, Color(0xFFC8391F)],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('NEXT REHEARSAL',
              style: AppTypography.display(
                  fontSize: 12,
                  letterSpacing: 3,
                  color: AppColors.onPrimary.withOpacity(0.55))),
          const SizedBox(height: 4),
          Text(
            Fmt.dateTimeKo(r.startAt),
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w900,
              letterSpacing: -0.5,
              color: AppColors.onPrimary,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '${r.roomName} · ${Fmt.durationKo(r.startAt, r.endAt)}',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: AppColors.onPrimary.withOpacity(0.72),
            ),
          ),
          const SizedBox(height: 14),
          if (r.isPending)
            _MiniPill(text: '승인 대기', color: AppColors.purpleSoft)
          else
            _MiniPill(text: '확정', color: const Color(0xFFFFD9C6)),
        ],
      ),
    );
  }
}

class _MiniPill extends StatelessWidget {
  const _MiniPill({required this.text, required this.color});
  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.28),
        borderRadius: BorderRadius.circular(99),
      ),
      child: Text(
        text,
        style:
            TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: color),
      ),
    );
  }
}

/// 2칸 요약 그리드. 정산 합계 집계 API 가 아직 없어 좌측 카드는 안내 문구.
class SummaryRow extends StatelessWidget {
  const SummaryRow({super.key, required this.upcomingCount});
  final int? upcomingCount;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _SummaryCard(
            title: '이번 달 정산',
            value: '—',
            hint: '일정 상세에서 확인',
            onTap: () => showSoon(context, '정산'),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _SummaryCard(
            title: '예정 합주',
            value: upcomingCount == null ? '…' : '$upcomingCount회',
            hint: '앞으로 60일',
            valueColor: AppColors.textPrimary,
            onTap: () => context.push(Routes.calendar),
          ),
        ),
      ],
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({
    required this.title,
    required this.value,
    required this.hint,
    required this.onTap,
    this.valueColor = AppColors.purple,
  });

  final String title;
  final String value;
  final String hint;
  final VoidCallback onTap;
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(15),
        decoration: BoxDecoration(
          color: AppColors.surfaceRaised,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.borderFaint),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title,
                style: const TextStyle(fontSize: 11, color: AppColors.textDim)),
            const SizedBox(height: 6),
            Text(value,
                style: AppTypography.mono(fontSize: 19, color: valueColor)),
            const SizedBox(height: 3),
            Text(hint,
                style: const TextStyle(
                    fontSize: 10.5, color: AppColors.textFaint)),
          ],
        ),
      ),
    );
  }
}

/// "다가오는 일정" 리스트.
class UpcomingList extends StatelessWidget {
  const UpcomingList({super.key, required this.upcomingAsync});
  final AsyncValue<List<Reservation>> upcomingAsync;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Expanded(
              child: Text('다가오는 일정',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
            ),
            GestureDetector(
              onTap: () => context.push(Routes.calendar),
              child: const Text('캘린더',
                  style: TextStyle(fontSize: 11.5, color: AppColors.primary)),
            ),
          ],
        ),
        const SizedBox(height: 10),
        upcomingAsync.when(
          loading: () => const _ListSkeleton(),
          error: (_, __) => const Text('일정을 불러오지 못했습니다.',
              style: TextStyle(fontSize: 12, color: AppColors.textDim)),
          data: (items) {
            if (items.isEmpty) {
              return Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 22),
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.borderFaint),
                ),
                child: const Text('예정된 일정이 없어요.',
                    style: TextStyle(fontSize: 12.5, color: AppColors.textDim)),
              );
            }
            return Column(
              children: [
                for (final r in items.take(5))
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: _UpcomingTile(reservation: r),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _UpcomingTile extends StatelessWidget {
  const _UpcomingTile({required this.reservation});
  final Reservation reservation;

  @override
  Widget build(BuildContext context) {
    final r = reservation;
    return GestureDetector(
      onTap: () => context.push(Routes.reservation(r.id)),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.borderFaint),
        ),
        child: Row(
          children: [
            SizedBox(
              width: 40,
              child: Column(
                children: [
                  Text(Fmt.day(r.startAt),
                      style: AppTypography.display(
                          fontSize: 19,
                          letterSpacing: 1,
                          color: AppColors.primary)),
                  const SizedBox(height: 2),
                  Text(Fmt.dow(r.startAt),
                      style: const TextStyle(
                          fontSize: 9.5, color: AppColors.textFaint)),
                ],
              ),
            ),
            const SizedBox(width: 13),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(r.roomName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontSize: 13.5, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 2),
                  Text(
                    '${Fmt.time(r.startAt)} · ${Fmt.durationKo(r.startAt, r.endAt)}',
                    style:
                        const TextStyle(fontSize: 11, color: AppColors.textDim),
                  ),
                ],
              ),
            ),
            _StatusBadge(pending: r.isPending),
          ],
        ),
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.pending});
  final bool pending;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: pending
            ? AppColors.purple.withOpacity(0.18)
            : AppColors.primary.withOpacity(0.16),
        borderRadius: BorderRadius.circular(7),
      ),
      child: Text(
        pending ? '대기' : '확정',
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w700,
          color: pending ? AppColors.purpleSoft : AppColors.primarySoft,
        ),
      ),
    );
  }
}

class _ListSkeleton extends StatelessWidget {
  const _ListSkeleton();

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (var i = 0; i < 3; i++)
          Container(
            height: 56,
            margin: const EdgeInsets.only(bottom: 8),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(14),
            ),
          ),
      ],
    );
  }
}
