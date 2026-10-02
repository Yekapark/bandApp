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
      onSessionExpired: (notice) {
        // 지연 read — dio 생성 시점에는 authController 를 건드리지 않는다(순환 방지).
        ref.read(sessionExpiredSignalProvider).fire(notice: notice);
      },
    ),
  );

  if (kDebugMode) {
    dio.interceptors.add(
      LogInterceptor(
          requestBody: true, responseBody: true, requestHeader: false),
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
  String? _notice;

  /// [notice] — 로그인 화면에 보여 줄 까닭(예: 이용 정지 안내). 그냥 만료면 null.
  void fire({String? notice}) {
    _notice = notice;
    notifyListeners();
  }

  /// 마지막 신호의 안내를 꺼낸다(한 번만).
  String? takeNotice() {
    final n = _notice;
    _notice = null;
    return n;
  }
}

/// 토큰 갱신이 "이용 정지" 로 거절됐다(LAUNCH_REVIEW P7). 서버 문구(기간·문의처)를 그대로 싣는다.
class AccountSuspendedException implements Exception {
  AccountSuspendedException(this.message);
  final String message;
}

class _AuthInterceptor extends Interceptor {
  _AuthInterceptor({required this.storage, required this.onSessionExpired});

  final TokenStorage storage;
  final void Function(String? notice) onSessionExpired;

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
      } catch (e) {
        // 갱신(또는 재시도) 요청이 **서버에 닿지 못했거나**(인터넷 끊김·시간 초과) 서버가 5xx 를
        // 냈으면 토큰이 틀렸다는 뜻이 아니다. 예전에는 이것도 세션 만료로 쳐서, 배포 중(502)이나
        // 신호가 약한 곳에서 access 토큰이 만료되는 순간 로그아웃됐다. 이 요청만 "연결 실패" 로
        // 돌려주고 세션은 둔다 — 401 응답을 그대로 넘기면 부르는 쪽(부팅 확인 등)이 "토큰 무효" 로
        // 읽는다. (`_refreshDio` 는 4xx 를 응답으로 돌려주므로 DioException 은 연결 실패·5xx 뿐이다.)
        if (e is DioException) return handler.reject(e);
        onSessionExpired(e is AccountSuspendedException ? e.message : null);
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
    final error = res.data?['error'];
    if (error is Map && error['code'] == 'ACCOUNT_SUSPENDED') {
      throw AccountSuspendedException(
          (error['message'] ?? '이용이 정지된 계정이에요.').toString());
    }
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

/// 본문을 쓰지 않는 요청(삭제·나가기·확인 등)의 성공 확인. 2xx 가 아니면 [ApiException] 을 던진다.
///
/// 이 앱의 Dio 는 4xx 를 예외로 던지지 않고 응답으로 돌려준다(`validateStatus: code < 500`) — 실패 변환은
/// [unwrap] 이 한다. 그래서 응답을 버리는 호출이 [unwrap] 도 이것도 안 거치면 **서버가 거절해도 성공으로 보인다**
/// (밴드장 나가기 409 가 "밴드에서 나왔어요" 로, 틀린 비밀번호의 탈퇴가 성공으로 — LAUNCH_REVIEW U14).
/// 응답을 버리는 호출은 반드시 이것으로 감싼다: `ensureSuccess(await _dio.delete(...))`.
void ensureSuccess(Response<dynamic> res) {
  final code = res.statusCode ?? 0;
  if (code >= 200 && code < 300) return;
  unwrap<void>(res, (_) {});
  // 본문이 `success: true` 인 4xx 는 없지만, 있더라도 성공으로 넘기지 않는다.
  throw ApiException(
    code: 'UNKNOWN',
    message: '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
    statusCode: res.statusCode,
  );
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
      message: (err['message'] ?? '요청을 처리하지 못했어요.').toString(),
      statusCode: res.statusCode,
      fieldErrors: fields,
    );
  }
  throw ApiException(
    code: 'UNKNOWN',
    // 상태코드를 사용자에게 보여줘도 할 수 있는 게 없다. 원인은 로그로 남긴다.
    message: '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
    statusCode: res.statusCode,
  );
}
