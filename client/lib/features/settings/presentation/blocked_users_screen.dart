import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../board/data/board_models.dart';
import '../../board/data/board_repository.dart';
import '../application/settings_providers.dart';

/// 차단한 사용자 — 게시판에서 서로의 글이 보이지 않는 사용자 목록. 여기서 해제한다.
class BlockedUsersScreen extends ConsumerWidget {
  const BlockedUsersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final blocksAsync = ref.watch(blockedUsersProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('차단한 사용자',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: RefreshIndicator(
        color: AppColors.primary,
        backgroundColor: AppColors.surface,
        onRefresh: () async {
          ref.invalidate(blockedUsersProvider);
          await ref.read(blockedUsersProvider.future);
        },
        child: blocksAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => _Center(
            child: Text(
              e is ApiException ? e.message : '차단 목록을 불러오지 못했습니다.',
              style: const TextStyle(color: AppColors.textDim),
            ),
          ),
          data: (blocks) {
            if (blocks.isEmpty) {
              return const _Center(
                child: Text('차단한 사용자가 없어요.',
                    style: TextStyle(color: AppColors.textDim)),
              );
            }
            return ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 40),
              itemCount: blocks.length,
              separatorBuilder: (_, __) => const SizedBox(height: 8),
              itemBuilder: (context, i) => _BlockRow(
                blocked: blocks[i],
                onUnblock: () => _unblock(context, ref, blocks[i]),
              ),
            );
          },
        ),
      ),
    );
  }

  Future<void> _unblock(
    BuildContext context,
    WidgetRef ref,
    BlockedUser b,
  ) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: Text('${b.blockedUserName} 님 차단을 해제할까요?',
            style: const TextStyle(fontSize: 16)),
        content: const Text(
          '해제하면 게시판에서 서로의 글이 다시 보여요.',
          style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('해제'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(boardRepositoryProvider).unblock(b.blockedUserId);
      ref.invalidate(blockedUsersProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('차단을 해제했어요.')));
      }
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }
}

class _BlockRow extends StatelessWidget {
  const _BlockRow({required this.blocked, required this.onUnblock});

  final BlockedUser blocked;
  final VoidCallback onUnblock;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(blocked.blockedUserName,
                style: const TextStyle(
                    fontSize: 13.5, fontWeight: FontWeight.w600)),
          ),
          TextButton(onPressed: onUnblock, child: const Text('해제')),
        ],
      ),
    );
  }
}

class _Center extends StatelessWidget {
  const _Center({required this.child});
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
