import 'package:bandapp_client/features/board/data/board_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('PostPage.fromJson', () {
    test('maps posts and paging fields', () {
      final page = PostPage.fromJson({
        'bandId': 1,
        'count': 2,
        'posts': [
          {
            'id': 10,
            'authorId': 3,
            'authorName': '홍길동',
            'title': '3월 2일 합주',
            'preview': '드럼 새 방에서 첫 합주',
            'createdAt': '2026-03-02T12:00:00Z',
            'mediaCount': 2,
            'thumbnailUrl': 'https://r2.example/thumb.jpg',
          },
          {
            'id': 9,
            'authorId': 4,
            'authorName': '김영희',
            'title': '지난주',
            'preview': '',
            'createdAt': '2026-02-24T09:00:00Z',
            'mediaCount': 0,
            'thumbnailUrl': null,
          },
        ],
        'nextCursor': 'abc123',
        'hasNext': true,
      });

      expect(page.posts, hasLength(2));
      expect(page.posts.first.id, 10);
      expect(page.posts.first.thumbnailUrl, isNotNull);
      expect(page.posts[1].thumbnailUrl, isNull);
      expect(page.nextCursor, 'abc123');
      expect(page.hasNext, isTrue);
    });

    test('defaults when optional keys missing', () {
      final page = PostPage.fromJson({'posts': <dynamic>[]});
      expect(page.posts, isEmpty);
      expect(page.nextCursor, isNull);
      expect(page.hasNext, isFalse);
    });
  });

  group('PostDetail.fromJson', () {
    test('parses media and editable flag', () {
      final detail = PostDetail.fromJson({
        'id': 10,
        'bandId': 1,
        'authorId': 3,
        'authorName': '홍길동',
        'title': '합주 사진',
        'content': '본문',
        'createdAt': '2026-03-02T12:00:00Z',
        'editable': true,
        'media': [
          {
            'id': 100,
            'type': 'IMAGE',
            'status': 'READY',
            'contentType': 'image/jpeg',
            'sizeBytes': 2048,
            'downloadUrl': 'https://r2.example/full.jpg',
          },
          {
            'id': 101,
            'type': 'VIDEO',
            'status': 'PENDING',
            'contentType': 'video/mp4',
            'sizeBytes': 5000,
            'downloadUrl': null,
          },
        ],
      });

      expect(detail.editable, isTrue);
      expect(detail.media, hasLength(2));
      expect(detail.readyMedia, hasLength(1));
      expect(detail.media.first.isImage, isTrue);
      expect(detail.media.first.isReady, isTrue);
      expect(detail.media[1].isVideo, isTrue);
      expect(detail.media[1].isReady, isFalse);
    });
  });

  group('UploadTicket.fromJson', () {
    test('stringifies required headers and defaults method to PUT', () {
      final ticket = UploadTicket.fromJson({
        'mediaId': 7,
        'uploadUrl': 'https://r2.example/put',
        'requiredHeaders': {'Content-Type': 'image/png'},
        'maxSizeBytes': 10485760,
      });

      expect(ticket.mediaId, 7);
      expect(ticket.method, 'PUT');
      expect(ticket.requiredHeaders['Content-Type'], 'image/png');
      expect(ticket.maxSizeBytes, 10485760);
    });
  });
}
