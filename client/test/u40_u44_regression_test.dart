import 'dart:async';

import 'package:bandapp_client/core/deeplink/invite_link_handler.dart';
import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/features/auth/data/auth_models.dart';
import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/band/presentation/join_band_screen.dart';
import 'package:bandapp_client/features/home/presentation/widgets/band_switch_sheet.dart';
import 'package:bandapp_client/features/notification/application/notification_route.dart';
import 'package:bandapp_client/features/reservation/application/calendar_providers.dart';
import 'package:bandapp_client/features/reservation/data/reservation_models.dart';
import 'package:bandapp_client/features/settlement/application/settlement_providers.dart';
import 'package:bandapp_client/features/settlement/data/settlement_models.dart';
import 'package:bandapp_client/features/settlement/data/settlement_repository.dart';
import 'package:bandapp_client/features/settlement/presentation/settlement_screen.dart';
import 'package:bandapp_client/routing/app_router.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

/// Codex +37 실기기 QA 에서 찾은 QA-R07~R11(U40~U44) 회귀 테스트.
void main() {
  MyBand band(int id) => MyBand(
      id: id,
      name: '밴드$id',
      myRole: 'LEADER',
      memberCount: 2,
      joinedAt: DateTime(2026));

  // U40·QA-R07 — 합류 화면이 열린 채 다른 코드 링크가 오면 입력칸이 새 코드로 바뀐다.
  testWidgets('U40 합류 화면에서 다른 초대 링크를 받으면 새 코드로 바뀐다', (tester) async {
    final router = GoRouter(initialLocation: Routes.home, routes: [
      GoRoute(path: Routes.home, builder: (_, __) => const Scaffold(body: Text('HOME'))),
      GoRoute(
        path: Routes.joinBand,
        builder: (_, s) =>
            JoinBandScreen(initialCode: s.uri.queryParameters['code']),
      ),
    ]);
    await tester.pumpWidget(ProviderScope(
        child: MaterialApp.router(routerConfig: router)));

    openInvite(router, InviteLinkHandler.joinLocation('PCQ2PZPN'));
    await tester.pumpAndSettle();
    expect(find.text('PCQ2PZPN'), findsOneWidget);

    openInvite(router, InviteLinkHandler.joinLocation('QA37ABCD'));
    await tester.pumpAndSettle();
    expect(find.text('QA37ABCD'), findsOneWidget);
    expect(find.text('PCQ2PZPN'), findsNothing);
  });

  // U41·QA-R08 — 납부 체크로 생긴 화면의 직전 응답이, 새로 받은 정산(다른 멤버의 재계산)보다 우선했다.
  testWidgets('U41 정산이 새로 오면 이 화면의 직전 납부 응답 대신 새 금액을 보인다', (tester) async {
    Settlement s(int total, {required bool leaderPaid}) => Settlement.fromJson({
          'settlementId': 1,
          'totalAmount': total,
          'splitType': 'EQUAL',
          'paidCount': leaderPaid ? 1 : 0,
          'paidAmount': leaderPaid ? total ~/ 2 : 0,
          'outstandingAmount': leaderPaid ? total ~/ 2 : total,
          'shares': [
            {'userId': 1, 'name': '장', 'role': 'LEADER', 'amount': total ~/ 2, 'paid': leaderPaid},
            {'userId': 2, 'name': '멤', 'amount': total ~/ 2, 'paid': false},
          ],
        });
    var server = s(45000, leaderPaid: false);
    final repo = _PaidRepo(() => server = s(45000, leaderPaid: true));

    tester.view.physicalSize = const Size(800, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final c = ProviderContainer(overrides: [
      authControllerProvider.overrideWith(() => _FakeAuth(1)),
      currentBandProvider.overrideWithValue(band(9)),
      settlementProvider.overrideWith((ref, key) async => server),
      settlementRepositoryProvider.overrideWithValue(repo),
      reservationDetailProvider
          .overrideWith((ref, key) => Completer<ReservationDetail>().future),
      bandMembersProvider.overrideWith((ref, id) async => const []),
    ]);
    addTearDown(c.dispose);
    await tester.pumpWidget(UncontrolledProviderScope(
      container: c,
      child: const MaterialApp(home: SettlementScreen(reservationId: 5)),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('장')); // 내 몫 납부 체크 → 화면이 그 응답을 들고 있다
    await tester.pumpAndSettle();
    expect(find.textContaining('45,000'), findsWidgets);

    server = s(60000, leaderPaid: false); // 다른 멤버가 재계산
    c.invalidate(settlementProvider); // 알림을 눌러 열 때(openNotification)와 같다
    await tester.pumpAndSettle();
    expect(find.textContaining('60,000'), findsWidgets);
    expect(find.textContaining('45,000'), findsNothing);
  });

  // U42·QA-R09 — 다른 밴드 알림으로 현재 밴드가 바뀐 채 저장했으면, 저장한 밴드로 되돌린다.
  test('U42 저장한 밴드로 현재 밴드를 되돌린다', () async {
    final c = ProviderContainer(overrides: [
      myBandsProvider.overrideWith((ref) async => [band(1), band(2)]),
      selectedBandIdProvider.overrideWith(_Selected.new),
    ]);
    addTearDown(c.dispose);
    c.listen(currentBandProvider, (_, __) {});
    await c.read(myBandsProvider.future);
    c.read(selectedBandIdProvider.notifier).select(2); // 다른 밴드 알림을 눌렀다

    reselectSavedBand(c, 1); // 밴드 1 에서 쓰던 일정을 저장
    expect(c.read(currentBandProvider)?.id, 1);
  });

  // U43·QA-R10 — 탈퇴 판단은 이 폰의 캐시가 아니라 지금 서버 목록으로.
  test('U43 다른 기기에서 나간 밴드는 새 목록으로 판단한다, 못 받으면 캐시', () async {
    final fresh = await freshBandIds(() async => [band(1), band(2)], [1, 2, 65]);
    expect(notificationBandGone(65, fresh), isTrue);

    final failed =
        await freshBandIds(() async => throw Exception('offline'), [1, 2, 65]);
    expect(failed, [1, 2, 65]);

    final slow = await freshBandIds(
        () => Completer<List<MyBand>>().future, [1, 65],
        timeout: const Duration(milliseconds: 10));
    expect(slow, [1, 65]);
  });

  // U44·QA-R11 — 밴드가 많아도 전환 시트의 마지막 밴드와 버튼까지 스크롤해 누를 수 있다.
  testWidgets('U44 밴드 8개 전환 시트 — 마지막 밴드까지 스크롤해 고른다', (tester) async {
    tester.view.physicalSize = const Size(400, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final bands = [for (var i = 1; i <= 8; i++) band(i)];
    final c = ProviderContainer(overrides: [
      myBandsProvider.overrideWith((ref) async => bands),
      selectedBandIdProvider.overrideWith(_Selected.new),
    ]);
    addTearDown(c.dispose);
    await c.read(myBandsProvider.future);
    await tester.pumpWidget(UncontrolledProviderScope(
      container: c,
      child: MaterialApp(
        home: Consumer(
          builder: (context, ref, _) => Scaffold(
            body: TextButton(
              onPressed: () => showBandSwitchSheet(context, ref),
              child: const Text('열기'),
            ),
          ),
        ),
      ),
    ));
    await tester.tap(find.text('열기'));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(find.text('+ 초대코드로 가입'), 200,
        scrollable: find.byType(Scrollable).last);
    expect(find.text('+ 초대코드로 가입').hitTestable(), findsOneWidget);

    await tester.tap(find.text('밴드8'));
    await tester.pumpAndSettle();
    expect(c.read(selectedBandIdProvider), 8);
  });
}

class _PaidRepo extends SettlementRepository {
  _PaidRepo(this.onPaid) : super(Dio());
  final Settlement Function() onPaid;

  @override
  Future<Settlement> markPaid({
    required int bandId,
    required int reservationId,
    required int userId,
    required bool paid,
  }) async =>
      onPaid();
}

class _FakeAuth extends AuthController {
  _FakeAuth(this._me);
  final int _me;

  @override
  AuthState build() => AuthState(
        status: AuthStatus.authenticated,
        user: AppUser(id: _me, name: 'u$_me'),
      );
}

class _Selected extends SelectedBandId {
  @override
  int? build() => null;

  @override
  void select(int bandId) => state = bandId;
}
