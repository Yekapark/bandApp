import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/app_config.dart';
import '../../../core/diagnostics/crash_reporting.dart';
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
            sub: '알림 켜기·끄기 · 미리 알림 시점',
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
          // 초대코드는 밴드장만 만든다 — 멤버에게는 숨긴다(#43).
          if (band == null || band.isLeader)
            _Tile(
              icon: Icons.person_add_alt,
              label: '멤버 초대',
              sub: '초대코드·링크 발급',
              onTap: band == null ? null : () => context.push(Routes.invite),
            ),
          _Tile(
            icon: Icons.workspace_premium_outlined,
            label: '요금제',
            sub: 'FREE / PREMIUM · 미디어 보관기한',
            onTap: band == null ? null : () => context.push(Routes.plan),
          ),
          _Tile(
            icon: Icons.block_outlined,
            label: '차단한 사용자',
            sub: '차단 목록 · 해제',
            onTap: () => context.push(Routes.blockedUsers),
          ),
          _Tile(
            icon: Icons.person_outline,
            label: '계정',
            sub: '내 정보 · 회원 탈퇴',
            onTap: () => context.push(Routes.account),
          ),
          // 오류 기록은 국외(Google, 미국)로 보내므로 거부할 수 있어야 한다 — 개인정보처리방침 7. 릴리스 빌드에만 보인다.
          if (CrashReporting.available) const _CrashReportSwitch(),
          if (AppConfig.qaCrashTest) const _QaCrashButtons(),
          const SizedBox(height: 12),
          const Divider(height: 1, color: AppColors.border),
          _Tile(
            icon: Icons.logout,
            label: '로그아웃',
            danger: true,
            onTap: () => _logout(context, ref),
          ),
          // 어느 빌드가 깔려 있는지. 테스터가 "고쳤다는 게 안 보인다" 고 할 때
          // 먼저 물어볼 것이 이것이라, 화면에서 바로 읽히게 둔다.
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
            child: Text(
              AppConfig.versionLabel,
              style: const TextStyle(fontSize: 11, color: AppColors.textFaint),
            ),
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

/// PRIV-08 QA 빌드 전용 — `--dart-define=QA_CRASH_TEST=true` 일 때만 보인다.
/// Firebase 가 없거나 디버그 빌드여도 버튼은 보인다(그때 아무것도 안 보내고 앱이 계속 도는지 보는 것도 시험이다).
class _QaCrashButtons extends StatelessWidget {
  const _QaCrashButtons();

  @override
  Widget build(BuildContext context) {
    return Column(children: [
      _Tile(
        icon: Icons.error_outline,
        label: 'QA: 테스트 오류 내기',
        sub: '처리 안 된 Dart 오류를 하나 던져요(앱은 계속 돌아요).',
        onTap: () => Future<void>.microtask(() => throw StateError(
            'PRIV-08 QA 테스트 오류 ${DateTime.now().toIso8601String()}')),
      ),
      _Tile(
        icon: Icons.dangerous_outlined,
        label: 'QA: 테스트 비정상 종료',
        sub: '앱을 강제로 꺼요. 기록은 다음 실행 때 올라가요.',
        onTap: () {
          if (!CrashReporting.available) {
            ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('이 빌드는 오류 기록을 쓰지 않아요.')));
            return;
          }
          CrashReporting.crashForQa();
        },
      ),
    ]);
  }
}

/// 앱 오류 기록 보내기 켜기·끄기(U17). 값은 Crashlytics 가 기기에 저장한다.
class _CrashReportSwitch extends StatefulWidget {
  const _CrashReportSwitch();

  @override
  State<_CrashReportSwitch> createState() => _CrashReportSwitchState();
}

class _CrashReportSwitchState extends State<_CrashReportSwitch> {
  late bool _on = CrashReporting.enabled;

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      value: _on,
      onChanged: (v) async {
        setState(() => _on = v);
        try {
          await CrashReporting.setEnabled(v);
        } catch (_) {
          // 실제 값과 다르게 켜짐/꺼짐으로 남지 않게 되돌린다.
          if (!mounted) return;
          setState(() => _on = CrashReporting.enabled);
          ScaffoldMessenger.of(context)
            ..hideCurrentSnackBar()
            ..showSnackBar(const SnackBar(content: Text('설정을 바꾸지 못했어요. 다시 시도해 주세요.')));
        }
      },
      secondary: const Icon(Icons.bug_report_outlined,
          size: 20, color: AppColors.textSecondary),
      title: const Text('앱 오류 기록 보내기',
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
      subtitle: const Text(
        '앱이 꺼지거나 오류가 나면 원인을 찾을 수 있게 기기·앱 정보와 오류 내용을 보내요. 계정 정보는 보내지 않아요.',
        style: TextStyle(fontSize: 11.5, color: AppColors.textDim),
      ),
    );
  }
}
