import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../application/band_providers.dart';
import '../application/invite_providers.dart';
import '../data/invite_models.dart';
import '../data/invite_repository.dart';

/// 멤버 초대 — 밴드장이 초대코드·공유 링크를 발급/재발급/무효화한다.
class InviteScreen extends ConsumerStatefulWidget {
  const InviteScreen({super.key});

  @override
  ConsumerState<InviteScreen> createState() => _InviteScreenState();
}

class _InviteScreenState extends ConsumerState<InviteScreen> {
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

    return Scaffold(
      appBar: AppBar(
        title: const Text('멤버 초대',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: !band.isLeader
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Text(
                  '초대코드는 밴드장만 발급할 수 있어요.\n밴드장에게 코드를 요청하세요.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppColors.textDim, height: 1.6),
                ),
              ),
            )
          : _LeaderBody(
              bandId: band.id,
              busy: _busy,
              onIssue: () => _issue(band.id),
              onRevoke: () => _revoke(band.id),
            ),
    );
  }

  Future<void> _issue(int bandId) async {
    final regenerate =
        ref.read(currentInviteProvider(bandId)).valueOrNull != null;
    if (regenerate) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: AppColors.surface,
          title: const Text('코드를 새로 발급할까요?', style: TextStyle(fontSize: 16)),
          content: const Text(
            '지금 코드는 즉시 무효화되고, 이미 공유한 링크는 더 이상 쓸 수 없어요.',
            style: TextStyle(
                fontSize: 12.5, color: AppColors.textDim, height: 1.5),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('취소')),
            TextButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('새로 발급')),
          ],
        ),
      );
      if (ok != true) return;
    }

    setState(() => _busy = true);
    try {
      await ref.read(inviteRepositoryProvider).issue(bandId: bandId);
      ref.invalidate(currentInviteProvider(bandId));
      _toast(regenerate ? '새 코드를 발급했어요.' : '초대코드를 만들었어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('코드를 발급하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _revoke(int bandId) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('초대코드를 무효화할까요?', style: TextStyle(fontSize: 16)),
        content: const Text(
          '무효화하면 지금 공유된 링크로는 아무도 들어올 수 없어요. 다시 초대하려면 새로 발급하세요.',
          style:
              TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.5),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('무효화', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;

    setState(() => _busy = true);
    try {
      await ref.read(inviteRepositoryProvider).revoke(bandId);
      ref.invalidate(currentInviteProvider(bandId));
      _toast('초대코드를 무효화했어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('무효화하지 못했습니다.');
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

class _LeaderBody extends ConsumerWidget {
  const _LeaderBody({
    required this.bandId,
    required this.busy,
    required this.onIssue,
    required this.onRevoke,
  });

  final int bandId;
  final bool busy;
  final VoidCallback onIssue;
  final VoidCallback onRevoke;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inviteAsync = ref.watch(currentInviteProvider(bandId));

    return RefreshIndicator(
      color: AppColors.primary,
      backgroundColor: AppColors.surface,
      onRefresh: () async {
        ref.invalidate(currentInviteProvider(bandId));
        await ref.read(currentInviteProvider(bandId).future);
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 40),
        children: [
          inviteAsync.when(
            loading: () => const Padding(
              padding: EdgeInsets.only(top: 60),
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (e, _) => Padding(
              padding: const EdgeInsets.only(top: 40),
              child: Column(
                children: [
                  Text(
                    e is ApiException ? e.message : '초대코드를 불러오지 못했습니다.',
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: AppColors.textDim),
                  ),
                  const SizedBox(height: 12),
                  TextButton(
                    onPressed: () =>
                        ref.invalidate(currentInviteProvider(bandId)),
                    child: const Text('다시 시도'),
                  ),
                ],
              ),
            ),
            data: (invite) => invite == null
                ? _Empty(busy: busy, onIssue: onIssue)
                : _CodeCard(
                    invite: invite,
                    busy: busy,
                    onRegenerate: onIssue,
                    onRevoke: onRevoke,
                  ),
          ),
        ],
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty({required this.busy, required this.onIssue});
  final bool busy;
  final VoidCallback onIssue;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const SizedBox(height: 40),
        const Icon(Icons.mail_outline, size: 40, color: AppColors.textFaint),
        const SizedBox(height: 14),
        const Text(
          '아직 초대코드가 없어요.\n코드를 만들어 멤버에게 공유하세요.',
          textAlign: TextAlign.center,
          style: TextStyle(color: AppColors.textDim, height: 1.6),
        ),
        const SizedBox(height: 20),
        SizedBox(
          width: 220,
          child: FilledButton(
            onPressed: busy ? null : onIssue,
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.primary,
              foregroundColor: AppColors.onPrimary,
              minimumSize: const Size.fromHeight(50),
            ),
            child: busy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: AppColors.onPrimary),
                  )
                : const Text('초대코드 만들기'),
          ),
        ),
      ],
    );
  }
}

class _CodeCard extends StatelessWidget {
  const _CodeCard({
    required this.invite,
    required this.busy,
    required this.onRegenerate,
    required this.onRevoke,
  });

  final BandInvite invite;
  final bool busy;
  final VoidCallback onRegenerate;
  final VoidCallback onRevoke;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(vertical: 26, horizontal: 20),
          decoration: BoxDecoration(
            color: AppColors.surfaceCard,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.borderStrong),
          ),
          child: Column(
            children: [
              const Text('초대코드',
                  style: TextStyle(fontSize: 11, color: AppColors.textDim)),
              const SizedBox(height: 10),
              SelectableText(
                invite.code,
                style: AppTypography.mono(
                  fontSize: 30,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 4,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                [
                  invite.isUnlimited
                      ? '사용 횟수 무제한'
                      : '남은 사용 ${invite.remainingUses}회',
                  if (invite.expiresAt != null)
                    '${Fmt.dateKoUtc(invite.expiresAt!)} 만료',
                ].join(' · '),
                style:
                    const TextStyle(fontSize: 11, color: AppColors.textFaint),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _ActionButton(
                icon: Icons.copy,
                label: '코드 복사',
                onTap: () => _copy(context, invite.code, '코드를 복사했어요.'),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _ActionButton(
                icon: Icons.link,
                label: '링크 복사',
                onTap: () => _copy(
                  context,
                  invite.link.isNotEmpty ? invite.link : invite.code,
                  '초대 링크를 복사했어요.',
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),
        OutlinedButton.icon(
          onPressed: busy ? null : onRegenerate,
          icon: const Icon(Icons.refresh, size: 16),
          style: OutlinedButton.styleFrom(
            minimumSize: const Size.fromHeight(48),
            side: const BorderSide(color: AppColors.borderStrong),
            foregroundColor: AppColors.textSecondary,
          ),
          label: const Text('코드 새로 발급'),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: busy ? null : onRevoke,
          child:
              const Text('초대코드 무효화', style: TextStyle(color: AppColors.danger)),
        ),
      ],
    );
  }

  void _copy(BuildContext context, String text, String toast) {
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(toast)));
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 13),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.borderStrong),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 15, color: AppColors.textSecondary),
            const SizedBox(width: 7),
            Text(label,
                style: const TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textSecondary)),
          ],
        ),
      ),
    );
  }
}
