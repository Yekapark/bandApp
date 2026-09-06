import 'package:dio/dio.dart';

/// 백엔드 공통 실패 응답(`{ success:false, error:{ code, message, fieldErrors } }`)을
/// 앱에서 다루기 쉬운 예외로 변환한 것.
class ApiException implements Exception {
  ApiException({
    required this.code,
    required this.message,
    this.statusCode,
    this.fieldErrors = const {},
  });

  /// `ErrorCode` enum 이름. 네트워크 오류 등 서버 코드가 없으면 `UNKNOWN` / `NETWORK`.
  final String code;
  final String message;
  final int? statusCode;

  /// 필드명 → 사유.
  final Map<String, String> fieldErrors;

  bool get isNetwork => code == 'NETWORK';
  bool get isUnauthorized => statusCode == 401;

  @override
  String toString() => 'ApiException($code, $statusCode): $message';

  factory ApiException.fromDio(DioException e) {
    final res = e.response;
    if (res != null && res.data is Map) {
      final data = res.data as Map;
      final error = data['error'];
      if (error is Map) {
        final rawFields = error['fieldErrors'];
        final fields = <String, String>{};
        if (rawFields is List) {
          for (final f in rawFields) {
            if (f is Map && f['field'] != null) {
              fields[f['field'].toString()] = (f['reason'] ?? '').toString();
            }
          }
        }
        return ApiException(
          code: (error['code'] ?? 'UNKNOWN').toString(),
          message: (error['message'] ?? '요청을 처리하지 못했습니다.').toString(),
          statusCode: res.statusCode,
          fieldErrors: fields,
        );
      }
      return ApiException(
        code: 'UNKNOWN',
        message: '요청을 처리하지 못했습니다. (${res.statusCode})',
        statusCode: res.statusCode,
      );
    }

    // 응답은 왔는데 우리 형식이 아닌 경우 — Nginx 가 낸 502/503/504 HTML 이 대표적이다.
    // 여기서 걸러내지 않으면 dio 의 내부 설명문("This exception was thrown because the
    // response has a status code of 502 and RequestOptions.validateStatus was configured
    // to throw…")이 그대로 사용자 화면에 뜬다. 실제로 배포 중에 그 화면을 봤다.
    if (res != null) {
      final status = res.statusCode ?? 0;
      return ApiException(
        code: status >= 500 ? 'SERVER_UNAVAILABLE' : 'UNKNOWN',
        message: _messageForStatus(status),
        statusCode: status,
      );
    }

    final isTimeout = e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.sendTimeout ||
        e.type == DioExceptionType.connectionError;
    return ApiException(
      code: 'NETWORK',
      message: isTimeout
          ? '서버에 연결하지 못했습니다. 네트워크를 확인해 주세요.'
          : '통신 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
    );
  }

  /// 서버가 우리 형식으로 답하지 못했을 때 보여줄 말.
  /// 원인을 사용자가 고칠 수 있는지에 따라 문구를 나눈다.
  static String _messageForStatus(int status) {
    if (status == 502 || status == 503 || status == 504) {
      // 배포 중(앱 재기동 40초쯤)이거나 서버가 잠깐 멈춘 때. 기다리면 풀린다.
      return '서버가 잠시 응답하지 않아요. 30초쯤 뒤에 다시 시도해 주세요.';
    }
    if (status >= 500) return '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.';
    if (status == 429) return '요청이 너무 잦아요. 잠시 후 다시 시도해 주세요.';
    if (status == 413) return '파일이 너무 커요.';
    return '요청을 처리하지 못했습니다. ($status)';
  }
}
