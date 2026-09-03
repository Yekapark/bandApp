import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../auth/application/auth_controller.dart';
import '../../band/application/band_providers.dart';
import '../../reservation/application/calendar_providers.dart';
import '../application/settlement_providers.dart';
import '../data/settlement_models.dart';
import '../data/settlement_repository.dart';

/// 일정 정산(N빵) — 총액을 멤버 몫으로 나누고, 각자 납부 여부를 셀프 체크한다.
class SettlementScreen extends ConsumerStatefulWidget {
  const SettlementScreen({super.key, required this.reservationId});

  final int reservationId;

  @override
  ConsumerState<SettlementScreen> createState() => _SettlementScreenState();
}

class _SettlementScreenState extends ConsumerState<SettlementScreen> {
  Settlement? _override;
  bool _busy = false;

  SettlementKey _key(int bandId) =>
      (bandId: bandId, reservationId: widget.reservationId);

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    final meId = ref.watch(authControllerProvider).user?.id;

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

    final key = _key(band.id);
    final settlementAsync = ref.watch(settlementProvider(key));
    final detail = ref
        .watch(
          reservationDetailProvider(
            (bandId: band.id, reservationId: widget.reservationId),
          ),
        )
        .valueOrNull;
    final canManage = detail != null &&
        (band.isLeader ||
            (meId != null && detail.reservation.requestedBy == meId));

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '정산',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
        ),
      ),
      body: settlementAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorBody(
          message: e is ApiException ? e.message : '정산 정보를 불러오지 못했습니다.',
          onRetry: () => ref.invalidate(settlementProvider(key)),
        ),
        data: (loaded) {
          final settlement = _override ?? loaded;
          if (settlement == null) {
            return _CreateForm(
              canManage: canManage,
              initialAmount: detail?.reservation.cost,
              busy: _busy,
              onCreate: (amount, type) => _create(band.id, amount, type),
            );
          }
          return _Board(
            settlement: settlement,
            meId: meId,
            canManage: canManage,
            busy: _busy,
            onTogglePaid: (share) => _togglePaid(band.id, share),
            onRecalculate: () => _recalculate(band.id),
          );
        },
      ),
    );
  }

  Future<void> _create(int bandId, int amount, SplitType type) async {
    setState(() => _busy = true);
    try {
      final s = await ref.read(settlementRepositoryProvider).create(
            bandId: bandId,
            reservationId: widget.reservationId,
            totalAmount: amount,
            splitType: type,
          );
      if (mounted) setState(() => _override = s);
      ref.invalidate(settlementProvider(_key(bandId)));
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('정산을 만들지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _togglePaid(int bandId, SettlementShare share) async {
    setState(() => _busy = true);
    try {
      final s = await ref.read(settlementRepositoryProvider).markPaid(
            bandId: bandId,
            reservationId: widget.reservationId,
            userId: share.userId,
            paid: !share.paid,
          );
      if (mounted) setState(() => _override = s);
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('납부 상태를 바꾸지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _recalculate(int bandId) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('정산을 다시 계산할까요?', style: TextStyle(fontSize: 16)),
        content: const Text(
          '현재 밴드 멤버·참석자 기준으로 몫을 다시 나눕니다. 총액과 분배 방식은 그대로 두고, '
          '이미 납부 체크한 멤버의 상태는 유지돼요.',
          style:
              TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.5),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('그대로 두기'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('다시 계산'),
          ),
        ],
      ),
    );
    if (ok != true) return;

    setState(() => _busy = true);
    try {
      final s = await ref.read(settlementRepositoryProvider).recalculate(
            bandId: bandId,
            reservationId: widget.reservationId,
          );
      if (mounted) setState(() => _override = s);
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('재계산하지 못했습니다.');
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

// ── 정산 생성 폼 ────────────────────────────────────────────────────────

class _CreateForm extends StatefulWidget {
  const _CreateForm({
    required this.canManage,
    required this.initialAmount,
    required this.busy,
    required this.onCreate,
  });

  final bool canManage;
  final int? initialAmount;
  final bool busy;
  final void Function(int amount, SplitType type) onCreate;

  @override
  State<_CreateForm> createState() => _CreateFormState();
}

class _CreateFormState extends State<_CreateForm> {
  late final TextEditingController _amount;
  SplitType _type = SplitType.equal;

  @override
  void initState() {
    super.initState();
    _amount = TextEditingController(
      text: widget.initialAmount?.toString() ?? '',
    );
  }

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  int? get _amountValue {
    final raw = _amount.text.replaceAll(RegExp(r'[^0-9]'), '');
    if (raw.isEmpty) return null;
    return int.tryParse(raw);
  }

  @override
  Widget build(BuildContext context) {
    final amount = _amountValue;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 30),
      children: [
        const Text(
          '아직 정산이 없어요',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 6),
        const Text(
          '합주 총비용을 입력하면 멤버별 몫으로 나눠 드려요. '
          '나눠 떨어지지 않는 나머지는 밴드장이 먼저 부담합니다.',
          style: TextStyle(fontSize: 12, height: 1.6, color: AppColors.textDim),
        ),
        const SizedBox(height: 22),
        const Text(
          '정산 총액',
          style: TextStyle(fontSize: 11.5, color: AppColors.textDim),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _amount,
          keyboardType: TextInputType.number,
          inputFormatters: [
            FilteringTextInputFormatter.digitsOnly,
            LengthLimitingTextInputFormatter(9),
          ],
          enabled: widget.canManage && !widget.busy,
          onChanged: (_) => setState(() {}),
          decoration:
              const InputDecoration(hintText: '예: 90000', prefixText: '₩ '),
        ),
        const SizedBox(height: 18),
        const Text(
          '분배 방식',
          style: TextStyle(fontSize: 11.5, color: AppColors.textDim),
        ),
        const SizedBox(height: 8),
        _SplitToggle(
          value: _type,
          enabled: widget.canManage && !widget.busy,
          onChanged: (t) => setState(() => _type = t),
        ),
        const SizedBox(height: 24),
        if (!widget.canManage)
          const Text(
            '정산은 일정을 등록한 사람이나 밴드장이 만들 수 있어요.',
            style: TextStyle(fontSize: 12, color: AppColors.textFaint),
          )
        else
          _PrimaryAction(
            label: '정산 만들기',
            busy: widget.busy,
            enabled: amount != null && amount > 0,
            onTap: () => widget.onCreate(amount!, _type),
          ),
      ],
    );
  }
}

class _SplitToggle extends StatelessWidget {
  const _SplitToggle({
    required this.value,
    required this.enabled,
    required this.onChanged,
  });

  final SplitType value;
  final bool enabled;
  final ValueChanged<SplitType> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (final t in const [SplitType.equal, SplitType.attendeesOnly]) ...[
          Expanded(
            child: GestureDetector(
              onTap: enabled ? () => onChanged(t) : null,
              child: Container(
                height: 46,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: t == value ? AppColors.purple : AppColors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color:
                        t == value ? AppColors.purple : AppColors.borderStrong,
                  ),
                ),
                child: Text(
                  t == SplitType.equal ? '멤버 전원' : '참석자만',
                  style: TextStyle(
                    fontSize: 13.5,
                    fontWeight: FontWeight.w700,
                    color: t == value
                        ? AppColors.onPurple
                        : AppColors.textSecondary,
                  ),
                ),
              ),
            ),
          ),
          if (t == SplitType.equal) const SizedBox(width: 8),
        ],
      ],
    );
  }
}

// ── 정산 현황 ──────────────────────────────────────────────────────────

class _Board extends StatelessWidget {
  const _Board({
    required this.settlement,
    required this.meId,
    required this.canManage,
    required this.busy,
    required this.onTogglePaid,
    required this.onRecalculate,
  });

  final Settlement settlement;
  final int? meId;
  final bool canManage;
  final bool busy;
  final ValueChanged<SettlementShare> onTogglePaid;
  final VoidCallback onRecalculate;

  @override
  Widget build(BuildContext context) {
    final s = settlement;
    final perPerson = s.shareCount == 0 ? 0 : s.totalAmount ~/ s.shareCount;
    final hasRemainder = s.shareCount != 0 && s.totalAmount % s.shareCount != 0;

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 30),
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [AppColors.purple, Color(0xFF6C34C9)],
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '1인당 (${splitTypeLabel(s.splitType)})',
                style: AppTypography.display(
                  fontSize: 12,
                  letterSpacing: 2,
                  color: AppColors.onPurple.withValues(alpha: 0.6),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                '${Fmt.won(perPerson)}${hasRemainder ? ' ~' : ''}',
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.w900,
                  letterSpacing: -0.5,
                  color: AppColors.onPurple,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                '총 ${Fmt.won(s.totalAmount)} · ${s.shareCount}명',
                style: TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w600,
                  color: AppColors.onPurple.withValues(alpha: 0.72),
                ),
              ),
              const SizedBox(height: 16),
              ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  value: s.paidRatio,
                  minHeight: 6,
                  backgroundColor: AppColors.onPurple.withValues(alpha: 0.25),
                  valueColor: const AlwaysStoppedAnimation(AppColors.onPurple),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '${s.paidCount}/${s.shareCount}명 납부 · 남은 ${Fmt.won(s.outstandingAmount)}',
                style: const TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w700,
                  color: AppColors.onPurple,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        const Text(
          '납부 체크리스트',
          style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 4),
        const Text(
          '본인 몫만 체크할 수 있어요.',
          style: TextStyle(fontSize: 10.5, color: AppColors.textFaint),
        ),
        const SizedBox(height: 10),
        for (final share in s.shares)
          Padding(
            padding: const EdgeInsets.only(bottom: 7),
            child: _ShareRow(
              share: share,
              isMe: meId != null && share.userId == meId,
              busy: busy,
              onTap: () => onTogglePaid(share),
            ),
          ),
        if (canManage) ...[
          const SizedBox(height: 12),
          _SecondaryAction(
            label: '멤버·참석자 바뀜 → 재계산',
            busy: busy,
            onTap: onRecalculate,
          ),
        ],
      ],
    );
  }
}

class _ShareRow extends StatelessWidget {
  const _ShareRow({
    required this.share,
    required this.isMe,
    required this.busy,
    required this.onTap,
  });

  final SettlementShare share;
  final bool isMe;
  final bool busy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final tappable = isMe && !busy;
    return GestureDetector(
      onTap: tappable ? onTap : null,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        decoration: BoxDecoration(
          color: share.paid
              ? AppColors.success.withValues(alpha: 0.08)
              : AppColors.surface,
          borderRadius: BorderRadius.circular(13),
          border: Border.all(
            color: isMe
                ? AppColors.purple.withValues(alpha: 0.4)
                : AppColors.borderFaint,
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 22,
              height: 22,
              decoration: BoxDecoration(
                color: share.paid ? AppColors.success : Colors.transparent,
                borderRadius: BorderRadius.circular(7),
                border: Border.all(
                  color:
                      share.paid ? AppColors.success : AppColors.borderStrong,
                  width: 1.5,
                ),
              ),
              child: share.paid
                  ? const Icon(
                      Icons.check,
                      size: 14,
                      color: AppColors.onPrimary,
                    )
                  : null,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Row(
                children: [
                  Flexible(
                    child: Text(
                      share.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  if (isMe)
                    const Padding(
                      padding: EdgeInsets.only(left: 6),
                      child: Text(
                        '나',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w800,
                          color: AppColors.purple,
                        ),
                      ),
                    ),
                ],
              ),
            ),
            Text(
              Fmt.won(share.amount),
              style: AppTypography.mono(
                fontSize: 12.5,
                color: share.paid ? AppColors.success : AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PrimaryAction extends StatelessWidget {
  const _PrimaryAction({
    required this.label,
    required this.busy,
    required this.enabled,
    required this.onTap,
  });

  final String label;
  final bool busy;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final active = enabled && !busy;
    return GestureDetector(
      onTap: active ? onTap : null,
      child: Container(
        height: 52,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: active ? AppColors.purple : AppColors.surfaceRaised,
          borderRadius: BorderRadius.circular(14),
        ),
        child: busy
            ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : Text(
                label,
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w800,
                  color: active ? AppColors.onPurple : AppColors.textFaint,
                ),
              ),
      ),
    );
  }
}

class _SecondaryAction extends StatelessWidget {
  const _SecondaryAction({
    required this.label,
    required this.busy,
    required this.onTap,
  });

  final String label;
  final bool busy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: busy ? null : onTap,
      child: Container(
        height: 48,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: AppColors.surfaceAlt,
          borderRadius: BorderRadius.circular(13),
        ),
        child: Text(
          label,
          style: const TextStyle(
            fontSize: 12.5,
            fontWeight: FontWeight.w600,
            color: AppColors.textSecondary,
          ),
        ),
      ),
    );
  }
}

class _ErrorBody extends StatelessWidget {
  const _ErrorBody({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, style: const TextStyle(color: AppColors.textDim)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    );
  }
}
