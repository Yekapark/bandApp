import 'dart:async';

import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/features/auth/data/auth_models.dart';
import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/reservation/application/calendar_providers.dart';
import 'package:bandapp_client/features/reservation/data/reservation_models.dart';
import 'package:bandapp_client/features/settlement/application/settlement_providers.dart';
import 'package:bandapp_client/features/settlement/data/settlement_models.dart';
import 'package:bandapp_client/features/settlement/presentation/settlement_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// 결정 #25 — 밴드장은 다른 멤버 몫도 체크하고(밴드장 확인), 나간 멤버의 미납은 면제한다.
void main() {
  // 1 = 밴드장, 2 = 멤버(밴드장이 대신 체크), 3·4 = 나간 멤버(3 미납, 4 면제).
  final settlement = Settlement.fromJson({
    'settlementId': 1,
    'totalAmount': 40000,
    'splitType': 'EQUAL',
    'paidCount': 1,
    'paidAmount': 10000,
    'outstandingAmount': 20000,
    'exemptCount': 1,
    'exemptAmount': 10000,
    'shares': [
      {'userId': 1, 'name': '장', 'role': 'LEADER', 'amount': 10000, 'paid': false},
      {'userId': 2, 'name': '멤', 'amount': 10000, 'paid': true, 'paidByLeader': true},
      {'userId': 3, 'name': '나간이', 'amount': 10000, 'paid': false},
      {'userId': 4, 'name': '면제됨', 'amount': 10000, 'paid': false, 'exempt': true},
    ],
  });

  Future<void> pump(WidgetTester tester, {required int me}) async {
    tester.view.physicalSize = const Size(800, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(ProviderScope(
      overrides: [
        authControllerProvider.overrideWith(() => _FakeAuth(me)),
        currentBandProvider.overrideWithValue(MyBand(
            id: 9,
            name: '밴드',
            myRole: me == 1 ? 'LEADER' : 'MEMBER',
            memberCount: 2,
            joinedAt: DateTime(2026))),
        settlementProvider.overrideWith((ref, key) async => settlement),
        reservationDetailProvider.overrideWith(
            (ref, key) => Completer<ReservationDetail>().future),
        bandMembersProvider.overrideWith((ref, id) async => [
              for (final u in [1, 2])
                BandMember(
                    userId: u,
                    name: 'u$u',
                    role: u == 1 ? 'LEADER' : 'MEMBER',
                    joinedAt: DateTime(2026)),
            ]),
      ],
      child: const MaterialApp(home: SettlementScreen(reservationId: 5)),
    ));
    await tester.pump();
    await tester.pump();
  }

  testWidgets('밴드장: 밴드장 확인 표시, 나간 멤버 미납에 면제, 면제된 몫엔 면제 취소', (tester) async {
    await pump(tester, me: 1);

    expect(find.text('밴드장 확인'), findsOneWidget);
    expect(find.widgetWithText(TextButton, '면제'), findsOneWidget); // 3
    expect(find.widgetWithText(TextButton, '면제 취소'), findsOneWidget); // 4
    expect(find.textContaining('면제 1명'), findsOneWidget);

    // 다른 사람 몫은 확인 창을 거친다.
    await tester.tap(find.text('멤'));
    await tester.pump();
    expect(find.text('체크 풀기'), findsOneWidget);
  });

  testWidgets('멤버: 면제 버튼이 없고 남의 몫은 누를 수 없다', (tester) async {
    await pump(tester, me: 2);

    expect(find.widgetWithText(TextButton, '면제'), findsNothing);
    expect(find.widgetWithText(TextButton, '면제 취소'), findsNothing);
    expect(find.text('면제'), findsOneWidget); // 4 의 표시만

    await tester.tap(find.text('나간이'));
    await tester.pump();
    expect(find.byType(AlertDialog), findsNothing);
  });

  test('목록: 낸 사람 + 면제된 사람이 전원이면 다 정리됨', () {
    final item = BandSettlementItem.fromJson({
      'settlementId': 1,
      'reservationId': 2,
      'startAt': '2026-10-01T10:00:00Z',
      'shareCount': 3,
      'paidCount': 2,
      'exemptCount': 1,
    });
    expect(item.allPaid, isTrue);
  });
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
