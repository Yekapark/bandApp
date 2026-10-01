import 'package:bandapp_client/core/deeplink/invite_link_handler.dart';
import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/routing/app_router.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

/// QA-F02 — 앱이 꺼진 채 초대 링크로 열리면 코드가 사라지고 홈으로 갔다.
///
/// 링크는 스플래시가 세션을 확인하는 **도중에** 온다. 그때 합류 화면을 밀어 넣으면 redirect 가
/// "부팅 전에는 스플래시" 규칙으로 바꿔 버렸다. 진짜 라우터 규칙([appRedirect])에 화면만
/// 가짜로 끼워, 링크 → 로그인 확정 순서를 그대로 재현한다.
void main() {
  late AuthStatus status;
  late ValueNotifier<int> refresh;
  late GoRouter router;
  late InviteLinkHandler handler;

  Widget page(String name) => Scaffold(body: Text(name));

  Future<void> boot(WidgetTester tester) async {
    InviteLinkHandler.pendingCode = null;
    status = AuthStatus.unknown;
    refresh = ValueNotifier(0);
    router = GoRouter(
      initialLocation: Routes.splash,
      refreshListenable: refresh,
      redirect: (_, s) => appRedirect(status, s.matchedLocation),
      routes: [
        GoRoute(path: Routes.splash, builder: (_, __) => page('SPLASH')),
        GoRoute(path: Routes.login, builder: (_, __) => page('LOGIN')),
        GoRoute(path: Routes.home, builder: (_, __) => page('HOME')),
        GoRoute(
          path: Routes.joinBand,
          builder: (_, s) => page('JOIN:${s.uri.queryParameters['code']}'),
        ),
      ],
    );
    handler = InviteLinkHandler(
      isAuthenticated: () => status == AuthStatus.authenticated,
      push: router.push,
    );
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
  }

  Future<void> become(WidgetTester tester, AuthStatus next) async {
    status = next;
    refresh.value++;
    await tester.pumpAndSettle();
  }

  final link = Uri.parse('bandule://invite/ABCD2345');

  testWidgets('로그인된 채 꺼져 있었으면: 부팅이 끝나면 합류 화면, 코드 그대로', (tester) async {
    await boot(tester);
    handler.handle(link); // 부팅 확인 중에 링크 도착
    await tester.pumpAndSettle();
    expect(find.text('SPLASH'), findsOneWidget);

    await become(tester, AuthStatus.authenticated);
    expect(find.text('JOIN:ABCD2345'), findsOneWidget);
  });

  testWidgets('로그아웃 상태면: 로그인 화면 → 로그인 뒤 합류 화면', (tester) async {
    await boot(tester);
    handler.handle(link);
    await become(tester, AuthStatus.unauthenticated);
    expect(find.text('LOGIN'), findsOneWidget);

    await become(tester, AuthStatus.authenticated);
    expect(find.text('JOIN:ABCD2345'), findsOneWidget);

    // 한 번만 — 다음 로그인 때 또 합류 화면이 뜨면 안 된다.
    await become(tester, AuthStatus.unauthenticated);
    await become(tester, AuthStatus.authenticated);
    expect(find.text('HOME'), findsOneWidget);
  });

  testWidgets('앱이 떠 있고 로그인돼 있으면 바로 연다', (tester) async {
    await boot(tester);
    await become(tester, AuthStatus.authenticated);
    expect(find.text('HOME'), findsOneWidget);

    handler.handle(link);
    await tester.pumpAndSettle();
    expect(find.text('JOIN:ABCD2345'), findsOneWidget);
  });

  test('코드에 쿼리 글자가 섞여도 깨지지 않는다', () {
    expect(InviteLinkHandler.joinLocation('A&b=1'),
        '${Routes.joinBand}?code=A%26b%3D1');
    expect(InviteLinkHandler.joinLocation(''), Routes.joinBand);
  });
}
