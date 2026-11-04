import 'dart:async';

import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/home/presentation/home_screen.dart';
import 'package:bandapp_client/routing/app_router.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

/// 2026-11-04 — 새로 가입해 밴드를 만들고 홈으로 오면 홈이 검은 화면이었다.
///
/// 밴드를 만들면 목록을 다시 받는다(invalidate). 다시 받는 동안 Riverpod 은 **옛 값(빈 목록)** 을
/// data 로 그대로 보여 주는데, 홈은 그걸 "밴드 없음" 으로 읽고 게이트로 돌려보냈다. 곧 새 목록이
/// 오면 게이트가 다시 홈으로 보내 화면 전환이 겹쳤다. 다시 받는 중의 빈 목록은 기다려야 한다.
void main() {
  late Completer<List<MyBand>> next;
  late GoRouter router;
  late ProviderContainer container;

  Future<void> boot(WidgetTester tester) async {
    next = Completer();
    var first = true;
    container = ProviderContainer(overrides: [
      myBandsProvider.overrideWith((ref) {
        if (first) {
          first = false;
          return const []; // 가입 직후 — 밴드 없음
        }
        return next.future;
      }),
    ]);
    addTearDown(container.dispose);
    router = GoRouter(
      initialLocation: '/create',
      routes: [
        GoRoute(path: '/create', builder: (_, __) => const Scaffold(body: Text('CREATE'))),
        GoRoute(path: Routes.bandGate, builder: (_, __) => const Scaffold(body: Text('GATE'))),
        GoRoute(path: Routes.home, builder: (_, __) => const HomeScreen()),
      ],
    );
    await container.read(myBandsProvider.future);
    await tester.pumpWidget(UncontrolledProviderScope(
      container: container,
      child: MaterialApp.router(routerConfig: router),
    ));
  }

  String location() => router.routerDelegate.currentConfiguration.uri.path;

  testWidgets('밴드를 만든 직후(목록 다시 받는 중)에는 게이트로 가지 않는다', (tester) async {
    await boot(tester);
    // create_band_screen._submit 과 같은 순서
    container.invalidate(myBandsProvider);
    router.go(Routes.home);
    await tester.pump();
    await tester.pump();

    expect(location(), Routes.home);
    expect(find.text('GATE'), findsNothing);
  });

  testWidgets('다시 받아도 정말 비어 있으면 게이트로 간다', (tester) async {
    await boot(tester);
    container.invalidate(myBandsProvider);
    router.go(Routes.home);
    await tester.pump();

    next.complete(const []);
    await tester.pumpAndSettle();
    expect(location(), Routes.bandGate);
  });
}
