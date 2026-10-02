import 'package:bandapp_client/features/auth/presentation/signup_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

Future<void> _pump(WidgetTester tester) => tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: SignupScreen())),
    );

void main() {
  testWidgets('비밀번호 확인이 다르면 "비밀번호가 서로 달라요" 를 보여 준다', (tester) async {
    await _pump(tester);
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), '밴드원');
    await tester.enterText(fields.at(1), 'me@test.app');
    await tester.enterText(fields.at(2), 'password123');
    await tester.enterText(fields.at(3), 'password124');
    await tester.tap(find.text('가입하고 시작하기'));
    await tester.pumpAndSettle();

    expect(find.text('비밀번호가 서로 달라요'), findsOneWidget);
  });

  testWidgets('눈 아이콘을 누르면 비밀번호가 보인다', (tester) async {
    await _pump(tester);
    bool obscured() => tester
        .widget<TextField>(find.descendant(
            of: find.byType(TextFormField).at(2),
            matching: find.byType(TextField)))
        .obscureText;

    expect(obscured(), isTrue);
    await tester.tap(find.byTooltip('비밀번호 보기').first);
    await tester.pump();
    expect(obscured(), isFalse);
  });
}
