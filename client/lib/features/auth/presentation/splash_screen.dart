import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/theme/app_colors.dart';
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
              Container(
                width: 82,
                height: 82,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(22),
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [AppColors.primary, Color(0xFFC8391F)],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.primary.withOpacity(0.28),
                      blurRadius: 48,
                      offset: const Offset(0, 18),
                    ),
                  ],
                ),
                child: Center(
                  child: Container(
                    width: 26,
                    height: 26,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border:
                          Border.all(color: const Color(0xFF120806), width: 6),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                AppConfig.appName,
                style: AppTypography.display(fontSize: 31, letterSpacing: 5),
              ),
              const SizedBox(height: 7),
              const Text(
                '앱 이름 미정 · 임시 워크네임',
                style: TextStyle(fontSize: 11, color: AppColors.textFaint),
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
