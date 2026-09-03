import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/app_config.dart';
import '../storage/token_storage.dart';
import 'api_exception.dart';

/// 인증 헤더 부착 + access 토큰 만료 시 1회 refresh 후 재시도까지 처리하는 Dio.
final dioProvider = Provider<Dio>((ref) {
  final storage = ref.watch(tokenStorageProvider);

  final dio = Dio(
    BaseOptions(
      baseUrl: '${AppConfig.apiBaseUrl}${AppConfig.apiPrefix}',
      connectTimeout: const Duration(seconds: 5),
      receiveTimeout: const Duration(seconds: 10),
      contentType: Headers.jsonContentType,
      // 4xx 도 정상 흐름으로 받아 ApiException 으로 변환한다.
      validateStatus: (code) => code != null && code < 500,
    ),
  );

  dio.interceptors.add(
    _AuthInterceptor(
      storage: storage,
      onSessionExpired: () {
        // 지연 read — dio 생성 시점에는 authController 를 건드리지 않는다(순환 방지).
        ref.read(sessionExpiredSignalProvider).fire();
      },
    ),
  );

  if (kDebugMode) {
    dio.interceptors.add(
      LogInterceptor(requestBody: true, responseBody: true, requestHeader: false),
    );
  }

  return dio;
});

/// refresh 실패로 세션이 끊겼음을 알리는 신호. AuthController 가 구독한다.
final sessionExpiredSignalProvider = Provider<SessionExpiredSignal>((ref) {
  final signal = SessionExpiredSignal();
  ref.onDispose(signal.dispose);
  return signal;
});

class SessionExpiredSignal extends ChangeNotifier {
  void fire() => notifyListeners();
}

class _AuthInterceptor extends Interceptor {
  _AuthInterceptor({required this.storage, required this.onSessionExpired});

  final TokenStorage storage;
  final VoidCallback onSessionExpired;

  /// refresh 및 재시도 전용 Dio (인터셉터 없음 — 재귀 방지).
  final Dio _refreshDio = Dio(
    BaseOptions(
      baseUrl: '${AppConfig.apiBaseUrl}${AppConfig.apiPrefix}',
      connectTimeout: const Duration(seconds: 5),
      receiveTimeout: const Duration(seconds: 10),
      contentType: Headers.jsonContentType,
      validateStatus: (code) => code != null && code < 500,
    ),
  );

  Future<void>? _refreshing;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = storage.current?.accessToken;
    if (token != null && options.headers['Authorization'] == null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  Future<void> onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) async {
    final isAuthPath = response.requestOptions.path.startsWith('/auth/');
    final alreadyRetried = response.requestOptions.extra['__retried'] == true;

    if (response.statusCode == 401 && !isAuthPath && !alreadyRetried) {
      try {
        await _ensureRefreshed();
        final retried = await _retry(response.requestOptions);
        return handler.resolve(retried);
      } catch (_) {
        onSessionExpired();
        return handler.next(response);
      }
    }
    handler.next(response);
  }

  Future<void> _ensureRefreshed() {
    return _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
  }

  Future<void> _doRefresh() async {
    final refresh = storage.current?.refreshToken;
    if (refresh == null) throw StateError('no refresh token');

    final res = await _refreshDio.post<Map<String, dynamic>>(
      '/auth/refresh',
      data: {'refreshToken': refresh},
    );
    final data = res.data?['data'] as Map<String, dynamic>?;
    if (data == null) throw StateError('refresh: empty body');

    await storage.save(
      Tokens(
        accessToken: data['accessToken'] as String,
        refreshToken: data['refreshToken'] as String,
      ),
    );
  }

  Future<Response<dynamic>> _retry(RequestOptions options) {
    final token = storage.current?.accessToken;
    return _refreshDio.fetch<dynamic>(
      options.copyWith(
        headers: {
          ...options.headers,
          'Authorization': 'Bearer $token',
        },
        extra: {...options.extra, '__retried': true},
      ),
    );
  }
}

/// `Response` → 원하는 타입으로. 실패 응답이면 [ApiException] 을 던진다.
T unwrap<T>(Response<dynamic> res, T Function(Object? data) parse) {
  final body = res.data;
  if (body is Map && body['success'] == true) {
    return parse(body['data']);
  }
  // 4xx 는 validateStatus 로 통과되므로 여기서 변환.
  final err = (body is Map ? body['error'] : null);
  if (err is Map) {
    final rawFields = err['fieldErrors'];
    final fields = <String, String>{};
    if (rawFields is List) {
      for (final f in rawFields) {
        if (f is Map && f['field'] != null) {
          fields[f['field'].toString()] = (f['reason'] ?? '').toString();
        }
      }
    }
    throw ApiException(
      code: (err['code'] ?? 'UNKNOWN').toString(),
      message: (err['message'] ?? '요청을 처리하지 못했습니다.').toString(),
      statusCode: res.statusCode,
      fieldErrors: fields,
    );
  }
  throw ApiException(
    code: 'UNKNOWN',
    message: '예상치 못한 응답 (${res.statusCode})',
    statusCode: res.statusCode,
  );
}
