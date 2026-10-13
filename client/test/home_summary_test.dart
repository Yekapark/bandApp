import 'package:bandapp_client/features/home/presentation/widgets/home_sections.dart';
import 'package:bandapp_client/features/settlement/data/settlement_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// 홈 요약 카드 — 예전 "이번 달 정산" 은 값이 늘 "—" 였다. 지금은 서버가 전부 더한 내 미납을 보인다.
void main() {
  Future<void> pump(WidgetTester tester, AsyncValue<MyOutstanding> outstanding) {
    return tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SummaryRow(outstandingAsync: outstanding, upcomingCount: 3),
        ),
      ),
    );
  }

  test('MyOutstanding.fromJson — 값이 없으면 0', () {
    final o = MyOutstanding.fromJson({'amount': 45000, 'count': 2});
    expect(o.amount, 45000);
    expect(o.count, 2);
    expect(MyOutstanding.fromJson(const {}).amount, 0);
  });

  testWidgets('낼 돈이 있으면 금액, 예정 합주 수를 보인다', (tester) async {
    await pump(tester, const AsyncData(MyOutstanding(amount: 30000, count: 1)));

    expect(find.text('내가 안 낸 돈'), findsOneWidget);
    expect(find.text('₩ 30,000'), findsOneWidget);
    expect(find.text('3회'), findsOneWidget);
    expect(find.text('앞으로 60일'), findsNothing);
  });

  testWidgets('낼 돈이 없으면 "없음", 못 불러오면 "—"', (tester) async {
    await pump(tester, const AsyncData(MyOutstanding(amount: 0, count: 0)));
    expect(find.text('없음'), findsOneWidget);

    await pump(tester, AsyncError<MyOutstanding>(Exception('x'), StackTrace.empty));
    expect(find.text('—'), findsOneWidget);
  });
}
