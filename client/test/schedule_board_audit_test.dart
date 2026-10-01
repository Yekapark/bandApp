import 'dart:convert';
import 'dart:typed_data';

import 'package:bandapp_client/core/network/api_exception.dart';
import 'package:bandapp_client/features/board/data/board_repository.dart';
import 'package:bandapp_client/features/settlement/data/settlement_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

class _Fake implements HttpClientAdapter {
  _Fake(this.status, this.body);
  final int status;
  final Object body;

  @override
  Future<ResponseBody> fetch(
          RequestOptions o, Stream<Uint8List>? _, Future<void>? __) async =>
      ResponseBody.fromString(jsonEncode(body), status, headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      });

  @override
  void close({bool force = false}) {}
}

SettlementRepository _repo(int status, Object body) => SettlementRepository(
      Dio(BaseOptions(
          baseUrl: 'http://test', validateStatus: (c) => c != null && c < 500))
        ..httpClientAdapter = _Fake(status, body),
    );

Map<String, Object> _err(String code) => {
      'success': false,
      'error': {'code': code, 'message': '$code 문구'},
    };

void main() {
  // 60초 고정이면 200MB 영상은 27Mbps 넘는 회선에서만 올라갔다.
  test('저장소 업로드 제한 시간은 파일 크기에 비례한다', () {
    expect(uploadSendTimeout(0), const Duration(seconds: 60));
    // 200MB 를 초당 64KB 로도 보낼 수 있는 시간 + 60초.
    expect(uploadSendTimeout(200 * 1024 * 1024).inSeconds, 60 + 3200);
  });

  test('정산이 없을 때(SETTLEMENT_NOT_FOUND)만 null', () async {
    expect(
        await _repo(404, _err('SETTLEMENT_NOT_FOUND'))
            .get(bandId: 1, reservationId: 2),
        isNull);
  });

  // 지워진 일정에 "아직 정산이 없어요" + 만들기 폼이 뜨던 것.
  test('일정이 없으면(RESERVATION_NOT_FOUND) 오류 그대로', () async {
    await expectLater(
      _repo(404, _err('RESERVATION_NOT_FOUND'))
          .get(bandId: 1, reservationId: 2),
      throwsA(isA<ApiException>()
          .having((e) => e.code, 'code', 'RESERVATION_NOT_FOUND')),
    );
  });
}
