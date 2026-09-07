import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../auth/application/auth_controller.dart';
import '../../band/application/band_providers.dart';
import '../../home/application/home_providers.dart';
import '../../plan/application/plan_providers.dart';
import '../../reservation/application/calendar_providers.dart';
import '../application/recurring_providers.dart';
import '../data/recurring_models.dart';
import '../data/recurring_repository.dart';

/// 정기 일정 규칙 목록 — 등록하면 앞으로 8주분 회차가 캘린더에 자동으로 생긴다.
class RecurringListScreen extends ConsumerWidget {
  const RecurringListScreen({super.key});

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
    final rulesAsync = ref.watch(recurringRulesProvider(band.id));

    // 정기 일정 *등록*은 PREMIUM 기능이다(서버 RecurringRuleService.create 가 막는다).
    // 목록은 FREE 에서도 보여준다 — PREMIUM 을 쓰다 내려온 밴드의 기존 규칙은 계속 회차를
    // 만들기 때문에, 감추면 그 일정이 어디서 생겼는지 알 길이 없다.
    //
    // 아직 못 불러왔거나 조회에 실패했으면(null) 잠그지 않는다. 서버가 최종 방어선이라
    // 최악이라도 예전과 같은 동작(저장 시 403)이고, 반대로 잠가 버리면 통신이 잠깐 불안한
    // 것만으로 PREMIUM 사용자가 기능을 못 쓴다.
    final isFree =
        ref.watch(bandPlanProvider(band.id)).valueOrNull?.isPremium == false;

    return Scaffold(
      appBar: AppBar(
        title: const Text('정기 일정',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      floatingActionButton: isFree
          ? null
          : FloatingActionButton.extended(
              onPressed: () async {
                final added = await context.push<bool>(Routes.newRecurring);
                if (added == true) {
                  ref.invalidate(recurringRulesProvider(band.id));
                  ref.invalidate(monthReservationsProvider);
                  ref.invalidate(upcomingReservationsProvider(band.id));
                }
              },
              backgroundColor: AppColors.primary,
              foregroundColor: AppColors.onPrimary,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('정기 일정 추가',
                  style: TextStyle(fontWeight: FontWeight.w700)),
            ),
      body: Column(
        children: [
          if (isFree) const _PremiumLock(),
          Expanded(
            child: RefreshIndicator(
        color: AppColors.primary,
        backgroundColor: AppColors.surface,
        onRefresh: () async {
          ref.invalidate(recurringRulesProvider(band.id));
          await ref.read(recurringRulesProvider(band.id).future);
        },
        child: rulesAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => _Scrollable(
            child: _Message(
              e is ApiException ? e.message : '정기 일정을 불러오지 못했어요.',
            ),
          ),
          data: (rules) {
            if (rules.isEmpty) {
              return const _Scrollable(
                child: _Message(
                  '등록된 정기 일정이 없어요.\n매주·격주·매월 반복되는 합주를 추가해 보세요.',
                ),
              );
            }
            return ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 96),
              itemCount: rules.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (context, i) {
                final rule = rules[i];
                final canDelete = band.isLeader || meId == rule.createdBy;
                return _RuleCard(
                  rule: rule,
                  canDelete: canDelete,
                  onTap: () async {
                    await context.push(Routes.recurringDetail(rule.id));
                    if (context.mounted) {
                      ref.invalidate(recurringRulesProvider(band.id));
                    }
                  },
                  onDelete: () => _confirmDelete(context, ref, band.id, rule),
                );
              },
            );
          },
        ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmDelete(
    BuildContext context,
    WidgetRef ref,
    int bandId,
    RecurringRule rule,
  ) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('정기 일정을 삭제할까요?', style: TextStyle(fontSize: 16)),
        content: const Text(
          '아직 시작하지 않은 회차만 취소돼요. 이미 지난 회차와 오늘 이후라도 개별로 '
          '따로 손댄 회차는 그대로 남아요. 합주실에는 직접 연락해 주세요.',
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
          .delete(bandId: bandId, ruleId: rule.id);
      ref.invalidate(recurringRulesProvider(bandId));
      ref.invalidate(monthReservationsProvider);
      ref.invalidate(upcomingReservationsProvider(bandId));
      if (context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('정기 일정을 삭제했어요.')));
      }
    } on ApiException catch (e) {
      _snack(context, e.message);
    } catch (_) {
      _snack(context, '삭제하지 못했어요.');
    }
  }

  static void _snack(BuildContext context, String msg) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _RuleCard extends StatelessWidget {
  const _RuleCard({
    required this.rule,
    required this.canDelete,
    required this.onTap,
    required this.onDelete,
  });

  final RecurringRule rule;
  final bool canDelete;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.surfaceCard,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    rule.summary,
                    style: AppTypography.mono(
                        fontSize: 14, fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    rule.roomName,
                    style: const TextStyle(
                        fontSize: 12.5, color: AppColors.textSecondary),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    '${rule.startDate} 부터'
                    '${rule.endDate != null ? ' · ${rule.endDate} 까지' : ''}',
                    style: const TextStyle(
                        fontSize: 11, color: AppColors.textFaint),
                  ),
                  if ((rule.note ?? '').trim().isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Text(
                      rule.note!.trim(),
                      style: const TextStyle(
                          fontSize: 11.5, color: AppColors.textDim),
                    ),
                  ],
                ],
              ),
            ),
            if (canDelete)
              IconButton(
                onPressed: onDelete,
                icon: const Icon(Icons.delete_outline,
                    size: 20, color: AppColors.textDim),
                visualDensity: VisualDensity.compact,
              ),
          ],
        ),
      ),
    );
  }
}

class _Scrollable extends StatelessWidget {
  const _Scrollable({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, c) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: SizedBox(
          height: c.maxHeight,
          child: Center(
            child: Padding(padding: const EdgeInsets.all(32), child: child),
          ),
        ),
      ),
    );
  }
}

class _Message extends StatelessWidget {
  const _Message(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Text(
        text,
        textAlign: TextAlign.center,
        style: const TextStyle(color: AppColors.textDim, height: 1.6),
      );
}

/// FREE 밴드에 보여주는 잠금 안내. 기능을 감추지 않고 무엇이 잠겼는지 알린 뒤 요금제
/// 화면으로 보낸다 — 폼을 다 채우고 저장할 때 403 을 만나는 것보다 낫다.
class _PremiumLock extends StatelessWidget {
  const _PremiumLock();

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 14, 16, 0),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.primary.withOpacity(0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.lock_outline,
                  size: 15, color: AppColors.primary),
              const SizedBox(width: 6),
              Text(
                '프리미엄 전용',
                style: AppTypography.display(
                  fontSize: 11,
                  letterSpacing: 2,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          const Text(
            '정기 일정 등록은 프리미엄 밴드만 쓸 수 있어요.',
            style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 5),
          const Text(
            '한 번만 등록해 두면 앞으로 8주분 합주가 캘린더에 자동으로 생겨요.',
            style:
                TextStyle(fontSize: 12, color: AppColors.textDim, height: 1.5),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              onPressed: () => context.push(Routes.plan),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.primary,
                side: const BorderSide(color: AppColors.primary),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
              child: const Text(
                '요금제 보기',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
