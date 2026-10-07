import 'dart:convert';
import 'dart:io';

import 'package:bandapp_client/core/network/api_exception.dart';
import 'package:bandapp_client/features/board/data/board_models.dart';
import 'package:bandapp_client/features/board/data/board_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
      'retry cleans failed ticket before issuing another; cleanup failure keeps it',
      () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(() => server.close(force: true));
    final root = 'http://127.0.0.1:${server.port}';
    final calls = <String>[];
    var issued = 54;
    var deletes = 0;
    server.listen((request) async {
      final path = request.uri.path;
      calls.add('${request.method} $path');
      await request.drain<void>();
      Object? data;
      if (path.endsWith('/upload-url')) {
        issued++;
        data = {
          'mediaId': issued,
          'uploadUrl': '$root/upload/$issued',
          'requiredHeaders': <String, String>{},
        };
      } else if (request.method == 'PUT') {
        request.response.statusCode = path.endsWith('/55') ? 503 : 200;
      } else if (request.method == 'DELETE') {
        deletes++;
        request.response.statusCode = deletes < 3 ? 503 : 204;
      } else if (path.endsWith('/complete')) {
        data = {
          'id': issued,
          'type': 'IMAGE',
          'status': 'READY',
          'contentType': 'image/png',
          'sizeBytes': 3,
        };
      } else {
        request.response.statusCode = 404;
      }
      if (data != null) {
        request.response.headers.contentType = ContentType.json;
        request.response.write(jsonEncode({'success': true, 'data': data}));
      }
      await request.response.close();
    });
    final dio = Dio(BaseOptions(baseUrl: root));
    addTearDown(() => dio.close(force: true));
    final repo = BoardRepository(dio);
    Future<PostMedia> upload() => repo.uploadMedia(
          bandId: 111,
          postId: 89,
          contentType: 'image/png',
          sizeBytes: 3,
          data: Stream.value([1, 2, 3]),
        );

    // Storage fails and offline cleanup fails. No second ticket is issued
    // while cleanup is still failing, and an existing successful media ID
    // (54) must never be deleted.
    await expectLater(upload(), throwsA(isA<ApiException>()));
    await expectLater(upload(), throwsA(isA<ApiException>()));
    expect(issued, 55);
    expect((await upload()).id, 56);
    expect(calls, [
      'POST /bands/111/posts/89/media/upload-url',
      'PUT /upload/55',
      'DELETE /bands/111/posts/89/media/55',
      'DELETE /bands/111/posts/89/media/55',
      'DELETE /bands/111/posts/89/media/55',
      'POST /bands/111/posts/89/media/upload-url',
      'PUT /upload/56',
      'POST /bands/111/posts/89/media/56/complete',
    ]);
    expect((await upload()).id, 57);
    expect(deletes, 3); // Successful cleanup was removed from the retry queue.
  });
}
