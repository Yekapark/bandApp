import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// 목업의 radial gradient 배경을 옵션으로 얹는 공통 Scaffold.
class GradientBackground extends StatelessWidget {
  const GradientBackground({
    super.key,
    required this.child,
    this.glow = AppColors.purple,
    this.alignment = const Alignment(0.7, -1),
  });

  final Widget child;
  final Color glow;
  final Alignment alignment;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          center: alignment,
          radius: 1.1,
          colors: [glow.withOpacity(0.18), AppColors.background],
          stops: const [0, 0.6],
        ),
      ),
      child: child,
    );
  }
}

/// 뒤로가기 텍스트 링크 ("← 라벨").
class BackLink extends StatelessWidget {
  const BackLink({super.key, required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Text(
          '←  $label',
          style: const TextStyle(fontSize: 13, color: AppColors.textDim),
        ),
      ),
    );
  }
}
