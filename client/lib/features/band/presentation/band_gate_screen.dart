import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../auth/application/auth_controller.dart';
import '../application/band_providers.dart';

/// 밴드 미소속 상태 — 만들거나, 초대코드로 합류.
class BandGateScreen extends ConsumerWidget {
  const BandGateScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // 밴드가 생기면 홈으로.
    ref.listen(myBandsProvider, (_, next) {
      if ((next.valueOrNull ?? const []).isNotEmpty) {
        context.go(Routes.home);
      }
    });

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 60, 24, 34),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('STEP 3 / 3 · 밴드 설정',
                  style: AppTypography.display(
                      fontSize: 11,
                      letterSpacing: 3,
                      color: AppColors.primary)),
              const SizedBox(height: 10),
              Text('밴드를 만들까요,\n들어갈까요?',
                  style: Theme.of(context).textTheme.headlineLarge),
              const SizedBox(height: 9),
              const Text(
                '나중에 다른 밴드도 추가할 수 있어요.',
                style: TextStyle(
                    fontSize: 12.5, height: 1.6, color: AppColors.textDim),
              ),
              const SizedBox(height: 26),
              _GateCard(
                title: '밴드 만들기',
                subtitle: '내가 밴드장이 되고, 초대코드를 멤버에게 보냅니다',
                filled: true,
                onTap: () => context.push(Routes.createBand),
              ),
              const SizedBox(height: 11),
              _GateCard(
                title: '밴드 가입하기',
                subtitle: '밴드장이 준 초대코드 8자를 입력합니다',
                filled: false,
                onTap: () => context.push(Routes.joinBand),
              ),
              const Spacer(),
              Center(
                child: TextButton(
                  onPressed: () =>
                      ref.read(authControllerProvider.notifier).logout(),
                  child: const Text('다른 계정으로 로그인',
                      style: TextStyle(color: AppColors.textDim)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GateCard extends StatelessWidget {
  const _GateCard({
    required this.title,
    required this.subtitle,
    required this.filled,
    required this.onTap,
  });

  final String title;
  final String subtitle;
  final bool filled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(18),
          gradient: filled
              ? const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [AppColors.primary, Color(0xFFC8391F)],
                )
              : null,
          color: filled ? null : AppColors.surfaceRaised,
          border: filled ? null : Border.all(color: AppColors.borderStrong),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w900,
                color: filled ? AppColors.onPrimary : AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              subtitle,
              style: TextStyle(
                fontSize: 12.5,
                height: 1.6,
                color: filled
                    ? AppColors.onPrimary.withOpacity(0.7)
                    : AppColors.textDim,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
