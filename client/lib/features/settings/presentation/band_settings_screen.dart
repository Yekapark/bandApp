import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../auth/application/auth_controller.dart';
import '../../band/application/band_providers.dart';
import '../../band/data/band_models.dart';
import '../../band/data/band_repository.dart';

const _permModes = ['LEADER_ONLY', 'ANYONE', 'APPROVAL_REQUIRED'];

/// 밴드 설정 — 일정 등록 권한 모드, 멤버 관리(위임·추방), 밴드 나가기.
class BandSettingsScreen extends ConsumerStatefulWidget {
  const BandSettingsScreen({super.key});

  @override
  ConsumerState<BandSettingsScreen> createState() => _BandSettingsScreenState();
}

class _BandSettingsScreenState extends ConsumerState<BandSettingsScreen> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    final meId = ref.watch(authControllerProvider).user?.id;
    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text('밴드를 먼저 선택해 주세요.',
              style: TextStyle(color: AppColors.textDim)),
        ),
      );
    }

    final detailAsync = ref.watch(bandDetailProvider(band.id));
    final membersAsync = ref.watch(bandMembersProvider(band.id));

    return Scaffold(
      appBar: AppBar(
        title: const Text('밴드 설정',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 14, 20, 40),
        children: [
          Text(band.name,
              style:
                  const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
          const SizedBox(height: 20),
          const _SectionTitle('일정 등록 권한'),
          const SizedBox(height: 8),
          detailAsync.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (e, _) => _InlineError(
              e is ApiException ? e.message : '설정을 불러오지 못했습니다.',
              () => ref.invalidate(bandDetailProvider(band.id)),
            ),
            data: (detail) => Column(
              children: [
                for (final mode in _permModes)
                  _PermTile(
                    mode: mode,
                    selected: detail.reservationPermission == mode,
                    enabled: band.isLeader && !_busy,
                    onTap: () => _changePermission(band.id, mode),
                  ),
                if (!band.isLeader)
                  const Padding(
                    padding: EdgeInsets.only(top: 6),
                    child: Text('권한 모드는 밴드장만 바꿀 수 있어요.',
                        style: TextStyle(
                            fontSize: 11, color: AppColors.textFaint)),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 26),
          const _SectionTitle('멤버'),
          const SizedBox(height: 8),
          membersAsync.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (e, _) => _InlineError(
              e is ApiException ? e.message : '멤버를 불러오지 못했습니다.',
              () => ref.invalidate(bandMembersProvider(band.id)),
            ),
            data: (members) => Column(
              children: [
                for (final m in members)
                  _MemberRow(
                    member: m,
                    isMe: m.userId == meId,
                    canManage: band.isLeader && m.userId != meId,
                    busy: _busy,
                    onDelegate: () => _delegate(band.id, m),
                    onKick: () => _kick(band.id, m),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 28),
          OutlinedButton(
            onPressed: _busy ? null : () => _leave(band.id, band.isLeader),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(50),
              side: const BorderSide(color: AppColors.danger),
              foregroundColor: AppColors.danger,
            ),
            child: const Text('이 밴드에서 나가기'),
          ),
          if (band.isLeader) ...[
            const SizedBox(height: 8),
            const Text(
              '밴드장은 먼저 다른 멤버에게 밴드장을 위임해야 나갈 수 있어요.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 11, color: AppColors.textFaint),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _changePermission(int bandId, String mode) async {
    setState(() => _busy = true);
    try {
      await ref
          .read(bandRepositoryProvider)
          .updateSettings(bandId: bandId, permission: mode);
      ref.invalidate(bandDetailProvider(bandId));
      _toast('일정 등록 권한을 바꿨어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('권한을 바꾸지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _delegate(int bandId, BandMember m) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: Text('${m.name} 님에게 밴드장을 넘길까요?',
            style: const TextStyle(fontSize: 16)),
        content: const Text(
          '위임하면 나는 일반 멤버가 되고, 되돌리려면 새 밴드장이 다시 위임해야 해요.',
          style:
              TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.5),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('위임'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      await ref.read(bandRepositoryProvider).delegateLeadership(
            bandId: bandId,
            newLeaderUserId: m.userId,
          );
      ref.invalidate(bandMembersProvider(bandId));
      ref.invalidate(bandDetailProvider(bandId));
      ref.invalidate(myBandsProvider);
      _toast('${m.name} 님이 밴드장이 됐어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('위임하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _kick(int bandId, BandMember m) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title:
            Text('${m.name} 님을 내보낼까요?', style: const TextStyle(fontSize: 16)),
        content: const Text(
          '추방된 멤버는 초대코드로 다시 들어올 수 있어요.',
          style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child:
                const Text('내보내기', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      await ref
          .read(bandRepositoryProvider)
          .kickMember(bandId: bandId, targetUserId: m.userId);
      ref.invalidate(bandMembersProvider(bandId));
      ref.invalidate(myBandsProvider);
      _toast('${m.name} 님을 내보냈어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('내보내지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _leave(int bandId, bool isLeader) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('이 밴드에서 나갈까요?', style: TextStyle(fontSize: 16)),
        content: Text(
          isLeader
              ? '밴드장은 먼저 다른 멤버에게 밴드장을 위임해야 나갈 수 있어요.'
              : '다시 들어오려면 초대코드가 필요해요.',
          style: const TextStyle(
              fontSize: 12.5, color: AppColors.textDim, height: 1.5),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('나가기', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      await ref.read(bandRepositoryProvider).leaveBand(bandId);
      ref.read(selectedBandIdProvider.notifier).clear();
      ref.invalidate(myBandsProvider);
      if (mounted) {
        _toast('밴드에서 나왔어요.');
        context.go('/home');
      }
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('나가지 못했습니다.');
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

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Text(text,
      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700));
}

class _PermTile extends StatelessWidget {
  const _PermTile({
    required this.mode,
    required this.selected,
    required this.enabled,
    required this.onTap,
  });

  final String mode;
  final bool selected;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: enabled ? onTap : null,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.primary.withValues(alpha: 0.1)
              : AppColors.surface,
          borderRadius: BorderRadius.circular(13),
          border: Border.all(
            color: selected ? AppColors.primary : AppColors.borderStrong,
          ),
        ),
        child: Row(
          children: [
            Icon(
              selected
                  ? Icons.radio_button_checked
                  : Icons.radio_button_unchecked,
              size: 18,
              color: selected ? AppColors.primary : AppColors.textFaint,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(reservationPermissionLabel(mode),
                      style: const TextStyle(
                          fontSize: 13.5, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 3),
                  Text(reservationPermissionHint(mode),
                      style: const TextStyle(
                          fontSize: 11, color: AppColors.textDim)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MemberRow extends StatelessWidget {
  const _MemberRow({
    required this.member,
    required this.isMe,
    required this.canManage,
    required this.busy,
    required this.onDelegate,
    required this.onKick,
  });

  final BandMember member;
  final bool isMe;
  final bool canManage;
  final bool busy;
  final VoidCallback onDelegate;
  final VoidCallback onKick;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      margin: const EdgeInsets.only(bottom: 6),
      decoration: BoxDecoration(
        color: AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 15,
            backgroundColor: AppColors.surfaceAlt,
            child: Text(member.initial,
                style: const TextStyle(
                    fontSize: 12, color: AppColors.textSecondary)),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              '${member.name}${isMe ? ' (나)' : ''}',
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ),
          if (member.isLeader)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(99),
              ),
              child: const Text('밴드장',
                  style: TextStyle(fontSize: 10, color: AppColors.primary)),
            ),
          if (canManage)
            PopupMenuButton<String>(
              enabled: !busy,
              color: AppColors.surface,
              icon: const Icon(Icons.more_horiz,
                  size: 18, color: AppColors.textDim),
              onSelected: (v) => v == 'delegate' ? onDelegate() : onKick(),
              itemBuilder: (_) => [
                const PopupMenuItem(value: 'delegate', child: Text('밴드장 위임')),
                const PopupMenuItem(
                  value: 'kick',
                  child:
                      Text('내보내기', style: TextStyle(color: AppColors.danger)),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

class _InlineError extends StatelessWidget {
  const _InlineError(this.message, this.onRetry);
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(message,
              style: const TextStyle(fontSize: 12, color: AppColors.textDim)),
        ),
        TextButton(onPressed: onRetry, child: const Text('다시 시도')),
      ],
    );
  }
}
