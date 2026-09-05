import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/brand_mark.dart';
import '../../../core/theme/app_typography.dart';
import '../application/auth_controller.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _boot();
  }

  Future<void> _boot() async {
    // 목업의 로딩 바 노출 시간을 보장한 뒤 세션을 확인한다.
    await Future.delayed(AppConfig.splashMinDuration);
    if (!mounted) return;
    await ref.read(authControllerProvider.notifier).bootstrap();
    // 이후 이동은 라우터 redirect 가 status 변화를 보고 처리한다.
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: RadialGradient(
            center: Alignment(0, -0.2),
            radius: 0.9,
            colors: [Color(0xFF2A1420), AppColors.background],
            stops: [0, 0.68],
          ),
        ),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const BrandMark(size: 82),
              const SizedBox(height: 20),
              // 워드마크는 Bebas Neue(라틴 전용)라 한글 글리프가 없다 — 영문 표기를 쓴다.
              Text(
                AppConfig.appNameEn,
                style: AppTypography.display(fontSize: 31, letterSpacing: 5),
              ),
              const SizedBox(height: 7),
              Text(
                AppConfig.appName,
                style: const TextStyle(fontSize: 11, color: AppColors.textFaint),
              ),
              const SizedBox(height: 26),
              const SizedBox(
                width: 132,
                child: _LoadingBar(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LoadingBar extends StatefulWidget {
  const _LoadingBar();

  @override
  State<_LoadingBar> createState() => _LoadingBarState();
}

class _LoadingBarState extends State<_LoadingBar>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1900),
  )..forward();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(99),
      child: Container(
        height: 3,
        color: const Color(0x17FFFFFF),
        child: AnimatedBuilder(
          animation: _c,
          builder: (context, _) => Align(
            alignment: Alignment.centerLeft,
            child: FractionallySizedBox(
              widthFactor: 0.06 + 0.94 * Curves.easeInOut.transform(_c.value),
              child: Container(color: AppColors.primary),
            ),
          ),
        ),
      ),
    );
  }
}
