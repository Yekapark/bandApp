import 'package:bandapp_client/core/diagnostics/crash_reporting.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('presigned URL 은 쿼리스트링을 지운다', () {
    final s = scrubSensitive(
        'DioException PUT https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com/b/1/a.jpg'
        '?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIA%2F2026&X-Amz-Signature=deadbeef status 403');
    expect(s, contains('/b/1/a.jpg?<redacted>'));
    expect(s, isNot(contains('X-Amz')));
    expect(s, isNot(contains('deadbeef')));
    expect(s, endsWith('status 403'));
  });

  test('Bearer·JWT·이메일·구매 토큰을 가린다', () {
    const jwt = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl';
    final s = scrubSensitive('Authorization: Bearer $jwt raw=$jwt user a.b+c@example.co.kr '
        '{"purchaseToken":"opaque123"} lone ${'x' * 50}');
    expect(s, isNot(contains('eyJ')));
    expect(s, contains('Bearer <redacted>'));
    expect(s, contains('<email>'));
    expect(s, isNot(contains('example.co.kr')));
    expect(s, isNot(contains('opaque123')));
    expect(s, isNot(contains('x' * 40)));
  });

  test('평범한 오류 문자열은 그대로 둔다', () {
    const msg = 'Null check operator used on a null value';
    expect(scrubSensitive(msg), msg);
  });

  test('FlutterErrorDetails 의 예외와 부가 정보를 가린다', () {
    final d = scrubDetails(FlutterErrorDetails(
      exception: Exception('load https://x.com/a.png?X-Amz-Signature=s'),
      informationCollector: () =>
          [DiagnosticsNode.message('url https://x.com/a.png?sig=1')],
    ),);
    expect(d.exceptionAsString(), isNot(contains('X-Amz')));
    expect(d.informationCollector!().single.toString(), isNot(contains('sig=1')));
  });
}
