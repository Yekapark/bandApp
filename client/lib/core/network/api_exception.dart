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

    final isTimeout = e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.sendTimeout ||
        e.type == DioExceptionType.connectionError;
    return ApiException(
      code: 'NETWORK',
      message: isTimeout
          ? '서버에 연결하지 못했습니다. 네트워크를 확인해 주세요.'
          : (e.message ?? '알 수 없는 네트워크 오류'),
    );
  }
}
