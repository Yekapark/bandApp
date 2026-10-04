import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/features/auth/data/auth_models.dart';
import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/plan/application/plan_providers.dart';
import 'package:bandapp_client/features/plan/data/plan_models.dart';
import 'package:bandapp_client/features/recurring/application/recurring_providers.dart';
import 'package:bandapp_client/features/recurring/presentation/recurring_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// QA REC-03·REC-06 — 무료 밴드의 정기 일정 화면: 추가 버튼 없이 "새 회차를 만들지 않는다" 와 요금제 안내.
void main() {
  Future<void> pump(WidgetTester tester, String tier) async {
    tester.view.physicalSize = const Size(800, 2000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(ProviderScope(
      overrides: [
        authControllerProvider.overrideWith(_FakeAuth.new),
        currentBandProvider.overrideWithValue(MyBand(
            id: 1, name: '밴드', myRole: 'LEADER', memberCount: 3, joinedAt: DateTime(2026))),
        bandPlanProvider.overrideWith((ref, id) async => BandPlan(tier: tier)),
        recurringRulesProvider.overrideWith((ref, id) async => const []),
      ],
      child: const MaterialApp(home: RecurringListScreen()),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('무료면 추가 버튼 대신 일시정지 안내와 요금제 버튼', (tester) async {
    await pump(tester, 'FREE');

    expect(find.text('정기 일정 추가'), findsNothing);
    expect(find.textContaining('새 회차를 만들지 않아요'), findsOneWidget);
    expect(find.text('요금제 보기'), findsOneWidget);
  });

  testWidgets('프리미엄이면 추가 버튼, 안내 없음', (tester) async {
    await pump(tester, 'PREMIUM');

    expect(find.text('정기 일정 추가'), findsOneWidget);
    expect(find.textContaining('새 회차를 만들지 않아요'), findsNothing);
  });
}

class _FakeAuth extends AuthController {
  @override
  AuthState build() => const AuthState(
        status: AuthStatus.authenticated,
        user: AppUser(id: 1, name: 'u1'),
      );
}
