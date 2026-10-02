import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_refresh.dart';
import 'core/config/app_config.dart';
import 'core/deeplink/invite_link_handler.dart';
import 'core/layout/readable_width.dart';
import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/application/auth_controller.dart';
import 'features/band/application/band_providers.dart';
import 'features/band/application/invite_providers.dart';
import 'features/board/application/board_providers.dart';
import 'features/home/application/home_providers.dart';
import 'features/notification/application/notification_providers.dart';
import 'features/plan/application/plan_providers.dart';
import 'features/recurring/application/recurring_providers.dart';
import 'features/reservation/application/calendar_providers.dart';
import 'features/settings/application/settings_providers.dart';
import 'features/settlement/application/settlement_providers.dart';
import 'features/notification/data/push_service.dart';
import 'features/plan/application/purchase_sync.dart';
import 'routing/app_router.dart';

/// 계정에 딸린 서버 데이터·화면 상태. **새 프로바이더를 만들면 여기에도 넣는다** — 빠지면 계정을
/// 바꿨을 때 그 화면만 앞 계정의 값이 남는다. (myBandsProvider 는 인증 상태를 직접 본다.)
final _userData = <ProviderOrFamily>[
  selectedBandIdProvider,
  bandMembersProvider,
  bandDetailProvider,
  currentInviteProvider,
  boardFeedProvider,
  upcomingReservationsProvider,
  notificationSettingProvider,
  notificationFeedProvider,
  unreadNotificationCountProvider,
  bandPlanProvider,
  recurringRulesProvider,
  recurringRuleDetailProvider,
  calendarMonthProvider,
  showCancelledReservationsProvider,
  monthReservationsProvider,
  roomsProvider,
  reservationDetailProvider,
  blockedUsersProvider,
  settlementProvider,
  bandSettlementsProvider,
];

class BandApp extends ConsumerWidget {
  const BandApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    // 초대 링크(bandule://invite/{code}) 수신 시작. 링크가 없으면 아무 일도 안 한다.
    ref.watch(inviteLinkHandlerProvider);
    // 앱으로 돌아왔을 때 밴드 데이터를 다시 불러온다(마지막 갱신 후 일정 시간 지났을 때만).
    ref.watch(foregroundRefreshProvider);

    // 로그인 상태에 따라 FCM 디바이스 토큰 등록/해제 (설정 없으면 조용히 no-op), 결제 스트림 열기/닫기.
    ref.listen(authControllerProvider.select((s) => s.status), (prev, status) {
      // 다른 계정으로 들어오면 앞 계정이 남긴 화면 데이터를 버린다. 이 프로바이더들은 autoDispose 가
      // 아니라 앱이 사는 동안 남아서, 로그아웃 → 다른 계정 로그인이면 앞 사람의 차단 목록·알림 설정·
      // 같은 밴드의 내 참석·정산 표시가 그대로 보였다(알림 설정은 그대로 저장하면 새 계정에 덮어써진다).
      if (prev == AuthStatus.unauthenticated &&
          status == AuthStatus.authenticated) {
        for (final p in _userData) {
          ref.invalidate(p);
        }
      }
      final push = ref.read(pushServiceProvider);
      // 결제 스트림도 로그인한 동안만 듣는다 — 검증 못 끝낸 구매를 서버에 보내려면 로그인이 필요하다.
      final purchases = ref.read(purchaseSyncProvider);
      if (status == AuthStatus.authenticated) {
        push.start();
        purchases.start();
      } else if (status == AuthStatus.unauthenticated) {
        push.stop();
        purchases.stop();
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
          // 큰 화면(태블릿·폴더블·가로)에서는 가운데 폰 폭 기둥으로 — 화면이 폰 세로 기준이라(P11).
          // 안전 영역보다 바깥이어야 가로 폰의 카메라 구멍 여백을 기둥 밖 여백으로 흡수한다.
          child: ColoredBox(
            color: AppColors.background,
            child: ReadableWidth(
              child: SafeArea(top: false, child: child!),
            ),
          ),
        );
      },
    );
  }
}
