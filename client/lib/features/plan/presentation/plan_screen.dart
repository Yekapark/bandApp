import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../band/application/band_providers.dart';
import '../application/plan_providers.dart';
import '../data/plan_models.dart';
import '../data/plan_repository.dart';

/// 밴드 요금제 — FREE/PREMIUM 조회와 전환(밴드장). 실제 결제 연동은 없다.
class PlanScreen extends ConsumerStatefulWidget {
  const PlanScreen({super.key});

  @override
  ConsumerState<PlanScreen> createState() => _PlanScreenState();
}

class _PlanScreenState extends ConsumerState<PlanScreen> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text('밴드를 먼저 선택해 주세요.',
              style: TextStyle(color: AppColors.textDim)),
        ),
      );
    }
    final planAsync = ref.watch(bandPlanProvider(band.id));

    return Scaffold(
      appBar: AppBar(
        title: const Text('요금제',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: planAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  e is ApiException ? e.message : '요금제를 불러오지 못했습니다.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.textDim),
                ),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: () => ref.invalidate(bandPlanProvider(band.id)),
                  child: const Text('다시 시도'),
                ),
              ],
            ),
          ),
        ),
        data: (plan) => ListView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 40),
          children: [
            _CurrentCard(plan: plan),
            const SizedBox(height: 20),
            const _CompareTable(),
            const SizedBox(height: 24),
            if (!band.isLeader)
              const Text(
                '요금제 변경은 밴드장만 할 수 있어요.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 11.5, color: AppColors.textFaint),
              )
            else if (plan.isPremium) ...[
              _ActionButton(
                label: '구독기간 연장',
                busy: _busy,
                onTap: () => _run(band.id, 'renew'),
              ),
              const SizedBox(height: 10),
              OutlinedButton(
                onPressed: _busy ? null : () => _confirmCancel(band.id),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(50),
                  side: const BorderSide(color: AppColors.danger),
                  foregroundColor: AppColors.danger,
                ),
                child: const Text('PREMIUM 해지'),
              ),
            ] else
              _ActionButton(
                label: 'PREMIUM 시작',
                busy: _busy,
                onTap: () => _confirmSubscribe(band.id),
              ),
            if (band.isLeader) ...[
              const SizedBox(height: 10),
              TextButton(
                onPressed: _busy ? null : () => _promptCoupon(band.id),
                child: const Text('쿠폰 코드 입력',
                    style: TextStyle(fontSize: 13, color: AppColors.textDim)),
              ),
            ],
            const SizedBox(height: 12),
            const Text(
              '이 릴리스에서는 실제 결제 없이 요금제가 전환됩니다(스토어 결제 연동 예정).',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 10.5, color: AppColors.textFaint),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmSubscribe(int bandId) async {
    final ok = await _confirm(
      title: 'PREMIUM 을 시작할까요?',
      body: '첨부한 사진·영상의 보관기한이 무제한이 됩니다. '
          '(이미 만료·삭제된 미디어는 복구되지 않아요.)',
      action: '시작',
    );
    if (ok) _run(bandId, 'subscribe');
  }

  Future<void> _confirmCancel(int bandId) async {
    final ok = await _confirm(
      title: 'PREMIUM 을 해지할까요?',
      body: '해지 시점부터 30일이 지나면 첨부 미디어가 순차적으로 만료됩니다.',
      action: '해지',
      danger: true,
    );
    if (ok) _run(bandId, 'cancel');
  }

  Future<bool> _confirm({
    required String title,
    required String body,
    required String action,
    bool danger = false,
  }) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: Text(title, style: const TextStyle(fontSize: 16)),
        content: Text(body,
            style: const TextStyle(
                fontSize: 12.5, color: AppColors.textDim, height: 1.5)),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(action,
                style:
                    danger ? const TextStyle(color: AppColors.danger) : null),
          ),
        ],
      ),
    );
    return ok ?? false;
  }

  /// 쿠폰 코드를 받아 사용한다. 코드를 넣어야만 버튼이 살아난다.
  Future<void> _promptCoupon(int bandId) async {
    final controller = TextEditingController();
    final code = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('쿠폰 코드 입력', style: TextStyle(fontSize: 16)),
        // 키보드가 올라오면 다이얼로그가 눌리므로 스크롤을 열어 둔다.
        scrollable: true,
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '받은 쿠폰 코드를 넣으면 PREMIUM 기간이 더해집니다.',
              style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              autofocus: true,
              textCapitalization: TextCapitalization.characters,
              maxLength: 8,
              decoration: const InputDecoration(
                hintText: '예: BANDULE7',
                counterText: '',
              ),
              onSubmitted: (v) => Navigator.pop(ctx, v.trim()),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('취소')),
          ValueListenableBuilder<TextEditingValue>(
            valueListenable: controller,
            builder: (_, value, __) => TextButton(
              onPressed: value.text.trim().isEmpty
                  ? null
                  : () => Navigator.pop(ctx, value.text.trim()),
              child: const Text('사용'),
            ),
          ),
        ],
      ),
    );
    if (code == null || code.isEmpty) return;
    await _run(bandId, 'coupon', code: code);
  }

  Future<void> _run(int bandId, String op, {String? code}) async {
    setState(() => _busy = true);
    try {
      final repo = ref.read(planRepositoryProvider);
      switch (op) {
        case 'subscribe':
          await repo.subscribe(bandId);
        case 'cancel':
          await repo.cancel(bandId);
        case 'renew':
          await repo.renew(bandId);
        case 'coupon':
          await repo.redeemCoupon(bandId, code!);
      }
      ref.invalidate(bandPlanProvider(bandId));
      _toast(op == 'coupon' ? '쿠폰을 사용했어요.' : '요금제를 변경했어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast(op == 'coupon' ? '쿠폰을 사용하지 못했습니다.' : '요금제를 변경하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _CurrentCard extends StatelessWidget {
  const _CurrentCard({required this.plan});
  final BandPlan plan;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: plan.isPremium
            ? AppColors.primary.withValues(alpha: 0.1)
            : AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: plan.isPremium ? AppColors.primary : AppColors.borderStrong,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('현재 요금제',
              style: TextStyle(fontSize: 11, color: AppColors.textDim)),
          const SizedBox(height: 6),
          Text(
            plan.tier,
            style: AppTypography.display(
              fontSize: 30,
              color: plan.isPremium ? AppColors.primary : AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          Text('첨부 미디어 보관: ${plan.retentionLabel}',
              style: const TextStyle(
                  fontSize: 12, color: AppColors.textSecondary)),
          if (plan.isPremium && plan.expiresAt != null) ...[
            const SizedBox(height: 3),
            Text('구독기간 종료: ${Fmt.dateKoUtc(plan.expiresAt!)}',
                style:
                    const TextStyle(fontSize: 11, color: AppColors.textFaint)),
          ],
        ],
      ),
    );
  }
}

class _CompareTable extends StatelessWidget {
  const _CompareTable();

  @override
  Widget build(BuildContext context) {
    Widget row(String label, String free, String premium) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Row(
            children: [
              Expanded(
                flex: 3,
                child: Text(label,
                    style: const TextStyle(
                        fontSize: 12, color: AppColors.textSecondary)),
              ),
              Expanded(
                flex: 2,
                child: Text(free,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                        fontSize: 12, color: AppColors.textDim)),
              ),
              Expanded(
                flex: 2,
                child: Text(premium,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: AppColors.primary)),
              ),
            ],
          ),
        );

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(13),
        border: Border.all(color: AppColors.borderFaint),
      ),
      child: Column(
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: [
                Expanded(flex: 3, child: SizedBox()),
                Expanded(
                  flex: 2,
                  child: Text('FREE',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 11, color: AppColors.textDim)),
                ),
                Expanded(
                  flex: 2,
                  child: Text('PREMIUM',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: AppColors.primary)),
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AppColors.borderFaint),
          row('미디어 보관기한', '30일', '무제한'),
          const Divider(height: 1, color: AppColors.borderFaint),
          row('게시판·일정·정산', '제한 없음', '제한 없음'),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.label,
    required this.busy,
    required this.onTap,
  });

  final String label;
  final bool busy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return FilledButton(
      onPressed: busy ? null : onTap,
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: AppColors.onPrimary,
        minimumSize: const Size.fromHeight(52),
      ),
      child: busy
          ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(
                  strokeWidth: 2, color: AppColors.onPrimary),
            )
          : Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
    );
  }
}
