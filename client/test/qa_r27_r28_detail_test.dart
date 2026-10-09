import 'dart:async';

import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/features/auth/data/auth_models.dart';
import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/reservation/data/reservation_models.dart';
import 'package:bandapp_client/features/reservation/data/reservation_repository.dart';
import 'package:bandapp_client/features/reservation/presentation/reservation_detail_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// 2026-10-09 클로드 S24 Play+42 실기기 QA(CAL-16)에서 찾은 QA-R27·R28.
void main() {
  Map<String, dynamic> member(String status) =>
      {'userId': 1, 'name': '나', 'role': 'LEADER', 'status': status};

  ReservationDetail detail(String status, List<String> songs) => ReservationDetail.fromJson({
        'id': 5,
        'roomName': '방',
        'requestedBy': 1,
        'status': 'CONFIRMED',
        'startAt': '2030-01-01T10:00:00Z',
        'endAt': '2030-01-01T13:00:00Z',
        'attendance': {
          'attendingCount': status == 'ATTENDING' ? 1 : 0,
          'memberCount': 1,
          'members': [member(status)],
        },
        'setlist': {
          'items': [
            for (var i = 0; i < songs.length; i++) {'id': i + 1, 'title': songs[i], 'orderNo': i + 1},
          ],
        },
      });

  Future<_Repo> open(WidgetTester tester, _Repo repo) async {
    tester.view.physicalSize = const Size(800, 3000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final c = ProviderContainer(overrides: [
      authControllerProvider.overrideWith(() => _FakeAuth(1)),
      currentBandProvider.overrideWithValue(MyBand(
          id: 9, name: '밴드', myRole: 'LEADER', memberCount: 1, joinedAt: DateTime(2026))),
      reservationRepositoryProvider.overrideWithValue(repo),
    ]);
    addTearDown(c.dispose);
    await tester.pumpWidget(UncontrolledProviderScope(
      container: c,
      child: const MaterialApp(home: ReservationDetailScreen(reservationId: 5)),
    ));
    await tester.pumpAndSettle();
    return repo;
  }

  // QA-R28 — 저장 중 누른 마지막 선택이 버려졌다(참석 → 0.1~0.2초 안에 불참 → 참석으로 저장).
  testWidgets('QA-R28 저장 중에 바꾼 마지막 참석 선택을 이어서 보낸다', (tester) async {
    final repo = await open(tester, _Repo(detail('PENDING', const [])));

    await tester.tap(find.text('참석'));
    await tester.pump();
    await tester.tap(find.text('불참')); // 첫 요청이 아직 날고 있다
    await tester.pump();
    expect(repo.sent, ['ATTENDING']); // 동시에 두 개를 보내지 않는다(#21)

    repo.finish();
    await tester.pumpAndSettle();
    expect(repo.sent, ['ATTENDING', 'ABSENT']);
    repo.finish();
    await tester.pumpAndSettle();
    expect(repo.server, 'ABSENT');
  });

  // QA-R27 — 액션(되돌리기)이 있는 스낵바는 Flutter 기본이 "안 닫힘" 이라 다른 화면까지 남아 버튼을 가렸다.
  testWidgets('QA-R27 곡 삭제 되돌리기 안내는 몇 초 뒤 닫힌다', (tester) async {
    await open(tester, _Repo(detail('PENDING', const ['A', 'B'])));

    await tester.tap(find.bySemanticsLabel('곡 삭제').first);
    await tester.pumpAndSettle();
    expect(find.text('곡을 지웠어요'), findsOneWidget);

    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    expect(find.text('곡을 지웠어요'), findsNothing);
  });
}

class _Repo extends ReservationRepository {
  _Repo(this._detail) : super(Dio());
  ReservationDetail _detail;
  final sent = <String>[];
  String server = 'PENDING';
  Completer<void>? _flight;

  void finish() => _flight?.complete();

  @override
  Future<ReservationDetail> detail({required int bandId, required int reservationId}) async =>
      _detail;

  @override
  Future<AttendanceBoard> respondAttendance({
    required int bandId,
    required int reservationId,
    required int userId,
    required AttendanceStatus status,
  }) async {
    final wire = attendanceStatusWire(status);
    sent.add(wire);
    _flight = Completer<void>();
    await _flight!.future;
    server = wire;
    return AttendanceBoard.fromJson({
      'attendingCount': wire == 'ATTENDING' ? 1 : 0,
      'memberCount': 1,
      'members': [
        {'userId': 1, 'name': '나', 'role': 'LEADER', 'status': wire},
      ],
    });
  }

  @override
  Future<void> deleteSetlistItem({
    required int bandId,
    required int reservationId,
    required int itemId,
  }) async {
    _detail = ReservationDetail(
      reservation: _detail.reservation,
      attendance: _detail.attendance,
      setlist: Setlist(items: _detail.setlist.items.where((i) => i.id != itemId).toList()),
    );
  }
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
