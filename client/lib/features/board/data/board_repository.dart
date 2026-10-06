
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'board_models.dart';

/// 저장소 PUT 의 전송 제한 시간. dio 의 sendTimeout 은 "본문 **전체**를 보내는 데" 걸리는 시간이라,
/// 60초 고정이면 200MB 영상은 초당 3.4MB(27Mbps) 이상 올라가는 회선에서만 성공한다 — 보통 LTE 업로드에서는
/// 매번 "서버에 연결하지 못했어요" 로 끝났다. 크기에 비례해 늘린다(초당 64KB 를 하한으로 잡고 60초 여유).
Duration uploadSendTimeout(int sizeBytes) =>
    Duration(seconds: 60 + sizeBytes ~/ (64 * 1024));

final boardRepositoryProvider = Provider<BoardRepository>((ref) {
  return BoardRepository(ref.watch(dioProvider));
});

/// 게시판 CRUD + 첨부 미디어(R2 presigned 업로드).
///
/// 파일 바이트는 백엔드를 지나지 않는다 — 서버가 발급한 presigned PUT URL 로 R2 에 직접 올린다.
/// 그 PUT 요청에는 Authorization 헤더가 붙으면 서명이 깨지므로 인터셉터 없는 별도 Dio 를 쓴다.
class BoardRepository {
  BoardRepository(this._dio);

  final Dio _dio;
  final Dio _plain = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 10),
      sendTimeout: const Duration(seconds: 60),
      receiveTimeout: const Duration(seconds: 30),
      validateStatus: (code) => code != null && code < 500,
    ),
  );

  /// 게시글 목록 한 페이지. [cursor] 는 직전 응답의 nextCursor.
  Future<PostPage> list({
    required int bandId,
    String? cursor,
    int limit = 20,
  }) async {
    try {
      final res = await _dio.get<dynamic>(
        '/bands/$bandId/posts',
        queryParameters: {
          'limit': limit,
          if (cursor != null && cursor.isNotEmpty) 'cursor': cursor,
        },
      );
      return unwrap(res, (d) => PostPage.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<PostDetail> detail({
    required int bandId,
    required int postId,
  }) async {
    try {
      final res = await _dio.get<dynamic>('/bands/$bandId/posts/$postId');
      return unwrap(
        res,
        (d) => PostDetail.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<PostDetail> create({
    required int bandId,
    required String title,
    required String content,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/posts',
        data: {'title': title, 'content': content},
      );
      return unwrap(
        res,
        (d) => PostDetail.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<PostDetail> update({
    required int bandId,
    required int postId,
    required String title,
    required String content,
  }) async {
    try {
      final res = await _dio.put<dynamic>(
        '/bands/$bandId/posts/$postId',
        data: {'title': title, 'content': content},
      );
      return unwrap(
        res,
        (d) => PostDetail.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<void> delete({required int bandId, required int postId}) async {
    try {
      ensureSuccess(await _dio.delete<dynamic>('/bands/$bandId/posts/$postId'));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 첨부 업로드: URL 발급 → R2 직접 PUT → 완료 콜백. 성공 시 READY 첨부를 돌려준다.
  /// 첨부 업로드 — presigned URL 발급 → R2 에 직접 PUT → 완료 통보.
  ///
  /// 파일을 [data] 스트림으로 받아 흘려보낸다. 바이트 배열로 받으면 영상 한 편이 통째로
  /// 메모리에 올라가 중저가 기기에서 앱이 죽는다. 호출자는 `file.openRead()` 를 넘긴다.
  ///
  /// [sizeBytes] 는 정확해야 한다 — 서버가 완료 처리에서 실제 업로드된 크기와 대조한다.
  Future<PostMedia> uploadMedia({
    required int bandId,
    required int postId,
    required String contentType,
    required int sizeBytes,
    required Stream<List<int>> data,
    /// 저장소로 보낸 바이트 수. 영상은 수백 MB 라 화면에 진행률을 보여줘야 한다.
    void Function(int sent, int total)? onProgress,
  }) async {
    final ticket = await _issueUploadUrl(
      bandId: bandId,
      postId: postId,
      contentType: contentType,
      sizeBytes: sizeBytes,
    );
    try {
      await _putToStorage(ticket, data, sizeBytes, contentType, onProgress);
      return await _completeMedia(
          bandId: bandId, postId: postId, mediaId: ticket.mediaId);
    } catch (_) {
      // 실패한 첨부의 PENDING 행을 치운다 — 남으면 재시도한 새 첨부와 함께 글에 "업로드 처리 중인 첨부예요" 와
      // 첨부 수 +1 이 서버 고아 정리(1시간)까지 모든 멤버에게 보였다(QA-R19). 오프라인이면 이것도 실패하니 무시한다.
      await deleteMedia(bandId: bandId, postId: postId, mediaId: ticket.mediaId)
          .catchError((_) {});
      rethrow;
    }
  }

  Future<UploadTicket> _issueUploadUrl({
    required int bandId,
    required int postId,
    required String contentType,
    required int sizeBytes,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/posts/$postId/media/upload-url',
        data: {'contentType': contentType, 'sizeBytes': sizeBytes},
      );
      return unwrap(
        res,
        (d) => UploadTicket.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<void> _putToStorage(
    UploadTicket ticket,
    Stream<List<int>> data,
    int sizeBytes,
    String contentType,
    void Function(int sent, int total)? onProgress,
  ) async {
    try {
      final res = await _plain.put<dynamic>(
        ticket.uploadUrl,
        data: data,
        // 스트림이라 dio 가 전체 크기를 모른다(total 이 -1 로 온다). 우리가 아는 값을 넘긴다.
        onSendProgress:
            onProgress == null ? null : (sent, _) => onProgress(sent, sizeBytes),
        options: Options(
          sendTimeout: uploadSendTimeout(sizeBytes),
          headers: {
            ...ticket.requiredHeaders,
            // 스트림 업로드라 dio 가 길이를 알 수 없다 — 직접 알려줘야 R2 가 받는다.
            Headers.contentLengthHeader: sizeBytes,
          },
          contentType: contentType,
        ),
      );
      final code = res.statusCode ?? 0;
      if (code < 200 || code >= 300) {
        throw ApiException(
          code: 'MEDIA_UPLOAD_FAILED',
          message: '파일을 저장소에 올리지 못했어요. ($code)',
          statusCode: code,
        );
      }
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<PostMedia> _completeMedia({
    required int bandId,
    required int postId,
    required int mediaId,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/posts/$postId/media/$mediaId/complete',
      );
      return unwrap(
        res,
        (d) => PostMedia.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<void> deleteMedia({
    required int bandId,
    required int postId,
    required int mediaId,
  }) async {
    try {
      ensureSuccess(await _dio.delete<dynamic>(
        '/bands/$bandId/posts/$postId/media/$mediaId',
      ));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 게시글·미디어·사용자 신고 접수. targetType: POST | MEDIA | USER.
  Future<void> report({
    required String targetType,
    required int targetId,
    required String reason,
  }) async {
    try {
      ensureSuccess(await _dio.post<dynamic>(
        '/reports',
        data: {
          'targetType': targetType,
          'targetId': targetId,
          'reason': reason,
        },
      ));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 사용자 차단(전역). 이후 게시판에서 서로의 글이 양방향으로 빠진다.
  Future<void> blockUser(int blockedUserId) async {
    try {
      ensureSuccess(await _dio.post<dynamic>(
        '/users/me/blocks',
        data: {'blockedUserId': blockedUserId},
      ));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 내가 차단한 사용자 목록(최근순).
  Future<List<BlockedUser>> listBlocks() async {
    try {
      final res = await _dio.get<dynamic>('/users/me/blocks');
      return unwrap(res, (d) {
        final list = (d! as Map<String, dynamic>)['blocks'] as List<dynamic>;
        return list
            .map((e) => BlockedUser.fromJson(e as Map<String, dynamic>))
            .toList(growable: false);
      });
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 차단 해제.
  Future<void> unblock(int blockedUserId) async {
    try {
      ensureSuccess(await _dio.delete<dynamic>('/users/me/blocks/$blockedUserId'));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
