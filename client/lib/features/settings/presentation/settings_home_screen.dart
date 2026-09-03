import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../routing/app_router.dart';
import '../../auth/application/auth_controller.dart';
import '../../band/application/band_providers.dart';

/// 설정 허브 — 알림·밴드·계정·차단 목록으로 가는 입구.
class SettingsHomeScreen extends ConsumerWidget {
  const SettingsHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authControllerProvider).user;
    final band = ref.watch(currentBandProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('설정',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          if (user != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 4),
              child: Text(
                user.name,
                style:
                    const TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
              ),
            ),
          if (user?.email != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
              child: Text(user!.email!,
                  style:
                      const TextStyle(fontSize: 12, color: AppColors.textDim)),
            ),
          const SizedBox(height: 8),
          _Tile(
            icon: Icons.notifications_none,
            label: '알림 설정',
            sub: '푸시 on/off · 리마인더 시점',
            onTap: () => context.push(Routes.notificationSettings),
          ),
          _Tile(
            icon: Icons.groups_outlined,
            label: '밴드 설정',
            sub: band == null
                ? '밴드를 먼저 선택해 주세요'
                : '${band.name} · 일정 권한 · 멤버 관리',
            onTap:
                band == null ? null : () => context.push(Routes.bandSettings),
          ),
          _Tile(
            icon: Icons.person_add_alt,
            label: '멤버 초대',
            sub: '초대코드·링크 발급',
            onTap: band == null ? null : () => context.push(Routes.invite),
          ),
          _Tile(
            icon: Icons.block_outlined,
            label: '차단한 사용자',
            sub: '게시판에서 서로의 글이 보이지 않는 사용자',
            onTap: () => context.push(Routes.blockedUsers),
          ),
          _Tile(
            icon: Icons.person_outline,
            label: '계정',
            sub: '내 정보 · 회원 탈퇴',
            onTap: () => context.push(Routes.account),
          ),
          const SizedBox(height: 12),
          const Divider(height: 1, color: AppColors.border),
          _Tile(
            icon: Icons.logout,
            label: '로그아웃',
            danger: true,
            onTap: () => _logout(context, ref),
          ),
        ],
      ),
    );
  }

  Future<void> _logout(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('로그아웃할까요?', style: TextStyle(fontSize: 16)),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child:
                const Text('로그아웃', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(authControllerProvider.notifier).logout();
      // redirect 가 로그인 화면으로 보낸다.
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }
}

class _Tile extends StatelessWidget {
  const _Tile({
    required this.icon,
    required this.label,
    this.sub,
    required this.onTap,
    this.danger = false,
  });

  final IconData icon;
  final String label;
  final String? sub;
  final VoidCallback? onTap;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final color = danger ? AppColors.danger : AppColors.textPrimary;
    return ListTile(
      enabled: onTap != null,
      onTap: onTap,
      leading: Icon(icon,
          size: 20, color: danger ? AppColors.danger : AppColors.textSecondary),
      title: Text(label,
          style: TextStyle(
              fontSize: 14, fontWeight: FontWeight.w700, color: color)),
      subtitle: sub == null
          ? null
          : Text(sub!,
              style: const TextStyle(fontSize: 11.5, color: AppColors.textDim)),
      trailing: danger
          ? null
          : const Icon(Icons.chevron_right,
              size: 18, color: AppColors.textFaint),
    );
  }
}
