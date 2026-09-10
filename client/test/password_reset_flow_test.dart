import 'package:bandapp_client/core/network/api_exception.dart';
import 'package:bandapp_client/features/auth/data/auth_repository.dart';
import 'package:bandapp_client/features/auth/presentation/password_reset_screen.dart';
import 'package:bandapp_client/routing/app_router.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

/// 네트워크 없이 두 엔드포인트만 흉내낸다.
class _FakeAuthRepo extends AuthRepository {
  _FakeAuthRepo() : super(Dio());

  int requestCalls = 0;
  String? confirmedCode;
  bool failConfirm = false;

  @override
  Future<void> requestPasswordReset({required String email}) async {
    requestCalls++;
  }

  @override
  Future<void> confirmPasswordReset({
    required String email,
    required String code,
    required String newPassword,
  }) async {
    confirmedCode = code;
    if (failConfirm) {
      throw ApiException(
        code: 'PASSWORD_RESET_CODE_INVALID',
        message: '서버 메시지',
        statusCode: 400,
      );
    }
  }
}

Future<void> _pump(WidgetTester tester, _FakeAuthRepo repo) async {
  final router = GoRouter(
    initialLocation: Routes.passwordReset,
    routes: [
      GoRoute(
        path: Routes.passwordReset,
        builder: (_, __) => const PasswordResetScreen(),
      ),
      GoRoute(
        path: Routes.login,
        builder: (_, __) => const Scaffold(body: Text('LOGIN')),
      ),
    ],
  );
  await tester.pumpWidget(
    ProviderScope(
      overrides: [authRepositoryProvider.overrideWithValue(repo)],
      child: MaterialApp.router(routerConfig: router),
    ),
  );
}

void main() {
  testWidgets('이메일 입력 → 인증번호 발송 → 2단계로 넘어간다', (tester) async {
    final repo = _FakeAuthRepo();
    await _pump(tester, repo);

    await tester.enterText(find.byType(TextFormField).first, 'me@test.app');
    await tester.tap(find.text('인증번호 받기'));
    await tester.pumpAndSettle();

    expect(repo.requestCalls, 1);
    expect(find.text('비밀번호 바꾸기'), findsOneWidget);
  });

  testWidgets('6자리 인증번호 + 새 비밀번호로 재설정하면 로그인 화면으로 간다', (tester) async {
    final repo = _FakeAuthRepo();
    await _pump(tester, repo);

    await tester.enterText(find.byType(TextFormField).first, 'me@test.app');
    await tester.tap(find.text('인증번호 받기'));
    await tester.pumpAndSettle();

    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), '123456');
    await tester.enterText(fields.at(1), 'newpass123');
    await tester.tap(find.text('비밀번호 바꾸기'));
    await tester.pumpAndSettle();

    expect(repo.confirmedCode, '123456');
    expect(find.text('LOGIN'), findsOneWidget);
  });

  testWidgets('인증번호가 틀리면 에러 문구를 보여주고 화면에 머문다', (tester) async {
    final repo = _FakeAuthRepo()..failConfirm = true;
    await _pump(tester, repo);

    await tester.enterText(find.byType(TextFormField).first, 'me@test.app');
    await tester.tap(find.text('인증번호 받기'));
    await tester.pumpAndSettle();

    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), '000000');
    await tester.enterText(fields.at(1), 'newpass123');
    await tester.tap(find.text('비밀번호 바꾸기'));
    await tester.pumpAndSettle();

    expect(find.textContaining('인증번호가 맞지 않'), findsOneWidget);
    expect(find.text('LOGIN'), findsNothing);
  });

  testWidgets('인증번호 6자리가 아니면 확인 요청을 보내지 않는다', (tester) async {
    final repo = _FakeAuthRepo();
    await _pump(tester, repo);

    await tester.enterText(find.byType(TextFormField).first, 'me@test.app');
    await tester.tap(find.text('인증번호 받기'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), '123');
    await tester.enterText(find.byType(TextFormField).at(1), 'newpass123');
    await tester.tap(find.text('비밀번호 바꾸기'));
    await tester.pumpAndSettle();

    expect(repo.confirmedCode, isNull);
    expect(find.text('인증번호는 숫자 6자리예요.'), findsOneWidget);
  });
}
