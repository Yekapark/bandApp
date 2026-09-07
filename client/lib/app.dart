import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_refresh.dart';
import 'core/config/app_config.dart';
import 'core/deeplink/invite_link_handler.dart';
import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/application/auth_controller.dart';
import 'features/notification/data/push_service.dart';
import 'routing/app_router.dart';

class BandApp extends ConsumerWidget {
  const BandApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    // 초대 링크(bandule://invite/{code}) 수신 시작. 링크가 없으면 아무 일도 안 한다.
    ref.watch(inviteLinkHandlerProvider);
    // 앱으로 돌아왔을 때 밴드 데이터를 다시 불러온다(마지막 갱신 후 일정 시간 지났을 때만).
    ref.watch(foregroundRefreshProvider);

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
          // 시스템 내비게이션 바(제스처 바·3버튼) 위로 화면을 올린다. **여기 한 곳에서** 한다 —
          // 화면마다 챙기면 반드시 빠뜨리는 곳이 생긴다(실제로 알림 설정의 저장 버튼과
          // 일정 상세의 하단 버튼이 가려져 있었다).
          //
          // `top: false` 인 이유 — 위쪽은 AppBar 가 이미 처리한다. 여기서 또 띄우면 두 번 밀린다.
          // 화면이 자체 SafeArea 를 갖고 있어도 문제없다: SafeArea 는 자식의 MediaQuery.padding 을
          // 지우므로 안쪽 SafeArea 는 0 을 더한다.
          // ColoredBox 는 띄운 만큼 생기는 아래 여백을 앱 배경색으로 메운다.
          child: ColoredBox(
            color: AppColors.background,
            child: SafeArea(top: false, child: child!),
          ),
        );
      },
    );
  }
}
