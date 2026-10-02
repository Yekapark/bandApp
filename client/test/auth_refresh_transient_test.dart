import 'dart:convert';
import 'dart:typed_data';

import 'package:bandapp_client/core/network/dio_client.dart';
import 'package:bandapp_client/core/storage/token_storage.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

class _MemStorage extends TokenStorage {
  _MemStorage() : super(const FlutterSecureStorage());

  Tokens? _t = const Tokens(accessToken: 'old', refreshToken: 'r1');

  @override
  Tokens? get current => _t;

  @override
  Future<void> save(Tokens tokens) async => _t = tokens;
}

/// 정해 둔 함수로 응답(또는 예외)을 만드는 가짜 서버.
class _Fake implements HttpClientAdapter {
  _Fake(this.handle);
  final Future<ResponseBody> Function(RequestOptions o) handle;

  @override
  Future<ResponseBody> fetch(
          RequestOptions o, Stream<Uint8List>? _, Future<void>? __) =>
      handle(o);

  @override
  void close({bool force = false}) {}
}

ResponseBody _json(int status, Object body) => ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );

Dio _dio(HttpClientAdapter a) => Dio(BaseOptions(
      baseUrl: 'http://test',
      validateStatus: (c) => c != null && c < 500,
    ))
      ..httpClientAdapter = a;

void main() {
  // 401 → 토큰 갱신이 네트워크 끊김·배포 중 502 로 실패하면, 예전에는 세션 만료로 보고 로그아웃시켰다.
  late List<String?> expired;
  late Dio api;

  void setUpWith(Future<ResponseBody> Function(RequestOptions) refreshServer) {
    expired = [];
    api = _dio(_Fake((o) async => _json(401, {'success': false})));
    api.interceptors.add(AuthInterceptor(
      storage: _MemStorage(),
      onSessionExpired: expired.add,
      refreshDio: _dio(_Fake(refreshServer)),
    ));
  }

  test('갱신 요청이 네트워크 오류면 로그아웃하지 않고 이 요청만 실패', () async {
    setUpWith((o) async => throw DioException.connectionError(
        requestOptions: o, reason: 'offline'));
    await expectLater(api.get<dynamic>('/bands'), throwsA(isA<DioException>()));
    expect(expired, isEmpty);
  });

  test('갱신 요청이 502 면 로그아웃하지 않는다', () async {
    setUpWith((o) async => ResponseBody.fromString('<html>502</html>', 502));
    await expectLater(api.get<dynamic>('/bands'), throwsA(isA<DioException>()));
    expect(expired, isEmpty);
  });

  test('refresh 토큰이 거절(401)되면 그때는 세션 만료', () async {
    setUpWith((o) async => _json(401, {
          'success': false,
          'error': {'code': 'INVALID_TOKEN', 'message': 'x'}
        }));
    final res = await api.get<dynamic>('/bands');
    expect(res.statusCode, 401);
    expect(expired, [null]);
  });
}
