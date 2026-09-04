import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/config/app_config.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/application/auth_controller.dart';
import 'features/notification/data/push_service.dart';
import 'routing/app_router.dart';

class BandApp extends ConsumerWidget {
  const BandApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);

    // 로그인 상태에 따라 FCM 디바이스 토큰 등록/해제 (설정 없으면 조용히 no-op).
    ref.listen(authControllerProvider.select((s) => s.status), (_, status) {
      final push = ref.read(pushServiceProvider);
      if (status == AuthStatus.authenticated) {
        push.start();
      } else if (status == AuthStatus.unauthenticated) {
        push.stop();
      }
    });

    return MaterialApp.router(
      title: AppConfig.appName,
      debugShowCheckedModeBanner: false,
      scaffoldMessengerKey: scaffoldMessengerKey,
      theme: AppTheme.dark(),
      routerConfig: router,
      locale: const Locale('ko'),
      supportedLocales: const [Locale('ko'), Locale('en')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      builder: (context, child) {
        // 시스템 폰트 스케일이 과하게 커도 레이아웃이 깨지지 않게 상한.
        final mq = MediaQuery.of(context);
        return MediaQuery(
          data: mq.copyWith(
            textScaler: mq.textScaler.clamp(maxScaleFactor: 1.3),
          ),
          child: child!,
        );
      },
    );
  }
}
