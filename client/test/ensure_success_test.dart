import 'dart:io';

import 'package:bandapp_client/core/network/api_exception.dart';
import 'package:bandapp_client/core/network/dio_client.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

// 이 앱의 Dio 는 4xx 를 예외로 던지지 않는다(validateStatus: code < 500). 응답을 버리는 호출이 성공 확인을
// 건너뛰면 서버가 거절해도 성공으로 보였다 — 밴드장이 "나가기" 를 누르면 409 인데 "밴드에서 나왔어요",
// 틀린 비밀번호로 탈퇴해도 성공 화면(LAUNCH_REVIEW U14).
void main() {
  Response<dynamic> res(int status, [dynamic body]) => Response(
        requestOptions: RequestOptions(path: '/bands/1/members/leave'),
        statusCode: status,
        data: body,
      );

  test('2xx 는 본문이 없어도 성공', () {
    expect(() => ensureSuccess(res(204)), returnsNormally);
    expect(() => ensureSuccess(res(200, {'success': true, 'data': null})),
        returnsNormally);
  });

  test('409 는 서버의 코드와 문구 그대로 ApiException', () {
    expect(
      () => ensureSuccess(res(409, {
        'success': false,
        'error': {
          'code': 'LEADER_MUST_DELEGATE_BEFORE_LEAVING',
          'message': '밴드장은 다른 멤버에게 위임한 뒤에 탈퇴할 수 있습니다.',
        },
      })),
      throwsA(isA<ApiException>()
          .having((e) => e.code, 'code', 'LEADER_MUST_DELEGATE_BEFORE_LEAVING')
          .having((e) => e.statusCode, 'statusCode', 409)
          .having((e) => e.message, 'message', contains('위임'))),
    );
  });

  test('본문이 우리 형식이 아닌 4xx 도 실패', () {
    expect(() => ensureSuccess(res(404, '<html>not found</html>')),
        throwsA(isA<ApiException>()));
    expect(() => ensureSuccess(res(400, {'success': true})),
        throwsA(isA<ApiException>()));
  });

  // 같은 실수가 새 코드에서 되풀이되지 않게: 응답을 받지도 감싸지도 않는 `await _dio.xxx(` 문을 막는다.
  // 로그아웃만 예외 — 실패해도 로컬 토큰을 지우는 멱등 요청이라 결과를 보지 않는다.
  test('응답을 버리는 Dio 호출은 ensureSuccess 로 감싼다', () {
    final bare = RegExp(r'^\s*await _?\w*[dD]io\.(get|post|put|patch|delete)\b');
    final offenders = <String>[];
    for (final f in Directory('lib').listSync(recursive: true).whereType<File>()) {
      if (!f.path.endsWith('.dart')) continue;
      final lines = f.readAsLinesSync();
      for (var i = 0; i < lines.length; i++) {
        if (!bare.hasMatch(lines[i])) continue;
        final prev = i > 0 ? lines[i - 1].trimRight() : '';
        if (prev.endsWith('=')) continue; // `final res =` 다음 줄로 넘어간 대입
        if (lines[i].contains("'/auth/logout'")) continue;
        offenders.add('${f.path}:${i + 1}');
      }
    }
    expect(offenders, isEmpty,
        reason: '응답을 unwrap 하거나 ensureSuccess(await ...) 로 감싸야 4xx 가 성공으로 보이지 않는다');
  });
}
