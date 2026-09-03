import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../auth/application/auth_controller.dart';
import '../../band/application/band_providers.dart';
import '../../home/application/home_providers.dart';
import '../../reservation/application/calendar_providers.dart';
import '../../reservation/data/reservation_models.dart';
import '../application/recurring_providers.dart';
import '../data/recurring_repository.dart';

/// 정기 일정 규칙 상세 — 규칙 요약 + 최근 구간 회차 목록. 회차를 탭하면 일반 일정 상세로.
class RecurringDetailScreen extends ConsumerWidget {
  const RecurringDetailScreen({super.key, required this.ruleId});

  final int ruleId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final band = ref.watch(currentBandProvider);
    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text('밴드를 먼저 선택해 주세요.',
              style: TextStyle(color: AppColors.textDim)),
        ),
      );
    }
    final meId = ref.watch(authControllerProvider).user?.id;
    final key = (bandId: band.id, ruleId: ruleId);
    final detailAsync = ref.watch(recurringRuleDetailProvider(key));

    return Scaffold(
      appBar: AppBar(
        title: const Text('정기 일정',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: detailAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  e is ApiException ? e.message : '정기 일정을 불러오지 못했습니다.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.textDim),
                ),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: () =>
                      ref.invalidate(recurringRuleDetailProvider(key)),
                  child: const Text('다시 시도'),
                ),
              ],
            ),
          ),
        ),
        data: (detail) {
          final rule = detail.rule;
          final canDelete = band.isLeader || meId == rule.createdBy;
          return RefreshIndicator(
            color: AppColors.primary,
            backgroundColor: AppColors.surface,
            onRefresh: () async {
              ref.invalidate(recurringRuleDetailProvider(key));
              await ref.read(recurringRuleDetailProvider(key).future);
            },
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 40),
              children: [
                Text(
                  rule.summary,
                  style: AppTypography.mono(
                      fontSize: 18, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                _MetaRow('합주실', rule.roomName),
                _MetaRow(
                  '기간',
                  '${rule.startDate} 부터'
                      '${rule.endDate != null ? ' · ${rule.endDate} 까지' : ' (종료일 없음)'}',
                ),
                if ((rule.note ?? '').trim().isNotEmpty)
                  _MetaRow('메모', rule.note!.trim()),
                const SizedBox(height: 24),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    const Text('다가오는 회차',
                        style: TextStyle(
                            fontSize: 13, fontWeight: FontWeight.w700)),
                    const SizedBox(width: 8),
                    Text('${detail.occurrenceCount}회',
                        style: const TextStyle(
                            fontSize: 11.5, color: AppColors.textDim)),
                  ],
                ),
                const SizedBox(height: 4),
                const Text(
                  '오늘 이후 8주 구간만 표시돼요. 그 이전 회차는 캘린더에서 확인하세요.',
                  style: TextStyle(fontSize: 11, color: AppColors.textFaint),
                ),
                const SizedBox(height: 10),
                if (detail.occurrences.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 12),
                    child: Text('표시할 회차가 없어요.',
                        style:
                            TextStyle(fontSize: 12, color: AppColors.textDim)),
                  )
                else
                  for (final r in detail.occurrences)
                    _OccurrenceRow(
                      reservation: r,
                      onTap: () async {
                        await context.push(Routes.reservation(r.id));
                        if (!context.mounted) return;
                        ref.invalidate(recurringRuleDetailProvider(key));
                      },
                    ),
                if (canDelete) ...[
                  const SizedBox(height: 24),
                  OutlinedButton(
                    onPressed: () => _confirmDelete(context, ref, band.id),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size.fromHeight(50),
                      side: const BorderSide(color: AppColors.danger),
                      foregroundColor: AppColors.danger,
                    ),
                    child: const Text('정기 일정 삭제'),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    '아직 시작하지 않은 회차만 취소돼요. 개별로 수정해 둔 회차·과거 회차는 남습니다.',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 11, color: AppColors.textFaint),
                  ),
                ],
              ],
            ),
          );
        },
      ),
    );
  }

  Future<void> _confirmDelete(
    BuildContext context,
    WidgetRef ref,
    int bandId,
  ) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('정기 일정을 삭제할까요?', style: TextStyle(fontSize: 16)),
        content: const Text(
          '아직 시작하지 않은 회차만 취소돼요. 합주실에는 직접 취소 연락을 하세요.',
          style:
              TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.5),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('그대로 두기')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('삭제', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref
          .read(recurringRepositoryProvider)
          .delete(bandId: bandId, ruleId: ruleId);
      ref.invalidate(recurringRulesProvider(bandId));
      ref.invalidate(monthReservationsProvider);
      ref.invalidate(upcomingReservationsProvider(bandId));
      if (context.mounted) {
        context.pop();
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('정기 일정을 삭제했어요.')));
      }
    } on ApiException catch (e) {
      _snack(context, e.message);
    } catch (_) {
      _snack(context, '삭제하지 못했습니다.');
    }
  }

  static void _snack(BuildContext context, String msg) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _MetaRow extends StatelessWidget {
  const _MetaRow(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 56,
            child: Text(label,
                style:
                    const TextStyle(fontSize: 11.5, color: AppColors.textDim)),
          ),
          Expanded(
            child: Text(value,
                style: const TextStyle(
                    fontSize: 12.5, color: AppColors.textSecondary)),
          ),
        ],
      ),
    );
  }
}

class _OccurrenceRow extends StatelessWidget {
  const _OccurrenceRow({required this.reservation, required this.onTap});

  final Reservation reservation;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final r = reservation;
    final inactive = !r.isActive;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 7),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(13),
          border: Border.all(color: AppColors.borderFaint),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                '${Fmt.dateKoUtc(r.startAt)}  '
                '${Fmt.time(r.startAt)}–${Fmt.time(r.endAt)}',
                style: TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w600,
                  color: inactive ? AppColors.textFaint : AppColors.textPrimary,
                  decoration: inactive ? TextDecoration.lineThrough : null,
                ),
              ),
            ),
            Text(
              reservationStatusLabel(r.status),
              style: TextStyle(
                fontSize: 10.5,
                color: inactive ? AppColors.textFaint : AppColors.textDim,
              ),
            ),
            const SizedBox(width: 4),
            const Icon(Icons.chevron_right,
                size: 16, color: AppColors.textFaint),
          ],
        ),
      ),
    );
  }
}
