import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bandapp_client/core/network/api_exception.dart';

// 서버가 우리 형식(JSON 봉투)으로 답하지 못했을 때, dio 의 내부 설명문이 사용자 화면에
// 새어 나가지 않아야 한다. 실제로 배포 중 502 를 맞아 그 긴 영문이 로그인 화면에 떴다.
void main() {
  DioException withResponse(int status, dynamic body) => DioException(
        requestOptions: RequestOptions(path: '/api/v1/auth/kakao'),
        response: Response(
          requestOptions: RequestOptions(path: '/api/v1/auth/kakao'),
          statusCode: status,
          data: body,
        ),
        type: DioExceptionType.badResponse,
        message: 'This exception was thrown because the response has a status '
            'code of $status and RequestOptions.validateStatus was configured…',
      );

  test('nginx 가 낸 502 HTML 은 안내 문구로 바뀐다', () {
    final e = ApiException.fromDio(
        withResponse(502, '<html><head><title>502 Bad Gateway</title></head></html>'));
    expect(e.statusCode, 502);
    expect(e.code, 'SERVER_UNAVAILABLE');
    expect(e.message, contains('잠시 응답하지 않아요'));
    expect(e.message, isNot(contains('validateStatus')));
    expect(e.message, isNot(contains('RequestOptions')));
  });

  test('503·504 도 같은 안내', () {
    for (final s in [503, 504]) {
      expect(ApiException.fromDio(withResponse(s, 'nginx')).message,
          contains('잠시 응답하지 않아요'));
    }
  });

  test('서버가 우리 형식으로 답하면 그 메시지를 그대로 쓴다', () {
    final e = ApiException.fromDio(withResponse(400, {
      'success': false,
      'error': {'code': 'INVALID_INPUT', 'message': '이메일 형식이 올바르지 않습니다.'},
    }));
    expect(e.code, 'INVALID_INPUT');
    expect(e.message, '이메일 형식이 올바르지 않습니다.');
  });

  test('응답 자체가 없으면(연결 실패) 네트워크 안내', () {
    final e = ApiException.fromDio(DioException(
      requestOptions: RequestOptions(path: '/x'),
      type: DioExceptionType.connectionError,
      message: 'SocketException: Connection refused …',
    ));
    expect(e.code, 'NETWORK');
    expect(e.message, contains('네트워크를 확인'));
    expect(e.message, isNot(contains('SocketException')));
  });
}
