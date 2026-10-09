import 'dart:convert';
import 'dart:typed_data';

import 'package:bandapp_client/core/network/dio_client.dart';
import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/features/auth/data/auth_models.dart';
import 'package:bandapp_client/features/reservation/application/calendar_providers.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// AUTH-09 — 같은 밴드의 다른 계정으로 바꿔 로그인하면 앞 계정이 받아 둔 캐시(일정 상세 등)를 버리고 새로 받는다.
void main() {
  test('계정이 바뀌면 일정 상세를 새 계정으로 다시 받는다', () async {
    final auth = _Auth();
    final adapter = _Adapter();
    final c = ProviderContainer(overrides: [
      authControllerProvider.overrideWith(() => auth),
      dioProvider.overrideWithValue(Dio()..httpClientAdapter = adapter),
    ]);
    addTearDown(c.dispose);
    c.read(authControllerProvider); // 로그인 상태부터
    const key = (bandId: 9, reservationId: 5);
    c.listen(reservationDetailProvider(key), (_, __) {});

    await c.read(reservationDetailProvider(key).future);
    expect(adapter.calls, 1);

    auth.signInAs(2); // 로그아웃 뒤 같은 밴드의 다른 계정으로
    await c.read(reservationDetailProvider(key).future);
    expect(adapter.calls, 2);
  });
}

class _Auth extends AuthController {
  @override
  AuthState build() => AuthState(status: AuthStatus.authenticated, user: AppUser(id: 1, name: 'A'));

  void signInAs(int id) =>
      state = AuthState(status: AuthStatus.authenticated, user: AppUser(id: id, name: 'B'));
}

class _Adapter implements HttpClientAdapter {
  int calls = 0;

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    calls++;
    final body = jsonEncode({
      'success': true,
      'data': {
        'id': 5,
        'requestedBy': 1,
        'status': 'CONFIRMED',
        'startAt': '2030-01-01T10:00:00Z',
        'endAt': '2030-01-01T13:00:00Z',
      },
    });
    return ResponseBody.fromString(body, 200, headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    });
  }

  @override
  void close({bool force = false}) {}
}
