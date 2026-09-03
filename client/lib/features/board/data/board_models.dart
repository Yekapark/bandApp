// 게시판 도메인 모델 — 백엔드 `12. 게시판` / `13. 첨부 미디어` 응답 매핑.

/// 미디어 종류 (백엔드 MediaType).
enum MediaKind { image, video, unknown }

MediaKind _mediaKindFrom(String? raw) {
  switch (raw) {
    case 'IMAGE':
      return MediaKind.image;
    case 'VIDEO':
      return MediaKind.video;
    default:
      return MediaKind.unknown;
  }
}

/// 첨부 미디어 생애 (백엔드 MediaStatus).
enum MediaState { pending, ready, expired, unknown }

MediaState _mediaStateFrom(String? raw) {
  switch (raw) {
    case 'PENDING':
      return MediaState.pending;
    case 'READY':
      return MediaState.ready;
    case 'EXPIRED':
      return MediaState.expired;
    default:
      return MediaState.unknown;
  }
}

/// GET /bands/{id}/posts 목록의 한 줄.
class PostSummary {
  const PostSummary({
    required this.id,
    required this.authorId,
    required this.authorName,
    required this.title,
    required this.preview,
    required this.createdAt,
    required this.mediaCount,
    this.thumbnailUrl,
  });

  final int id;
  final int authorId;
  final String authorName;
  final String title;
  final String preview;
  final DateTime createdAt;
  final int mediaCount;

  /// 대표 이미지가 있으면 짧은 만료의 presigned GET URL. 없으면 null.
  final String? thumbnailUrl;

  factory PostSummary.fromJson(Map<String, dynamic> json) {
    return PostSummary(
      id: (json['id'] as num).toInt(),
      authorId: (json['authorId'] as num?)?.toInt() ?? 0,
      authorName: json['authorName'] as String? ?? '탈퇴한 사용자',
      title: json['title'] as String? ?? '',
      preview: json['preview'] as String? ?? '',
      createdAt: DateTime.parse(json['createdAt'] as String),
      mediaCount: (json['mediaCount'] as num?)?.toInt() ?? 0,
      thumbnailUrl: json['thumbnailUrl'] as String?,
    );
  }
}

/// GET /bands/{id}/posts 한 페이지 (커서 페이징).
class PostPage {
  const PostPage({
    required this.posts,
    required this.nextCursor,
    required this.hasNext,
  });

  final List<PostSummary> posts;
  final String? nextCursor;
  final bool hasNext;

  factory PostPage.fromJson(Map<String, dynamic> json) {
    final list = json['posts'] as List<dynamic>? ?? const [];
    return PostPage(
      posts: list
          .map((e) => PostSummary.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      nextCursor: json['nextCursor'] as String?,
      hasNext: json['hasNext'] as bool? ?? false,
    );
  }
}

/// 게시글에 딸린 첨부 하나.
class PostMedia {
  const PostMedia({
    required this.id,
    required this.kind,
    required this.state,
    required this.contentType,
    required this.sizeBytes,
    this.downloadUrl,
  });

  final int id;
  final MediaKind kind;
  final MediaState state;
  final String contentType;
  final int sizeBytes;

  /// status=READY 일 때만 채워지는 짧은 만료의 presigned GET URL.
  final String? downloadUrl;

  bool get isReady => state == MediaState.ready && downloadUrl != null;
  bool get isImage => kind == MediaKind.image;
  bool get isVideo => kind == MediaKind.video;

  factory PostMedia.fromJson(Map<String, dynamic> json) {
    return PostMedia(
      id: (json['id'] as num).toInt(),
      kind: _mediaKindFrom(json['type'] as String?),
      state: _mediaStateFrom(json['status'] as String?),
      contentType: json['contentType'] as String? ?? '',
      sizeBytes: (json['sizeBytes'] as num?)?.toInt() ?? 0,
      downloadUrl: json['downloadUrl'] as String?,
    );
  }
}

/// GET/POST/PUT /bands/{id}/posts/{postId} — 게시글 상세.
class PostDetail {
  const PostDetail({
    required this.id,
    required this.bandId,
    required this.authorId,
    required this.authorName,
    required this.title,
    required this.content,
    required this.createdAt,
    required this.editable,
    required this.media,
  });

  final int id;
  final int bandId;
  final int authorId;
  final String authorName;
  final String title;
  final String content;
  final DateTime createdAt;

  /// 요청자가 작성자 본인이거나 밴드장이라 수정·삭제할 수 있는지.
  final bool editable;
  final List<PostMedia> media;

  int get mediaCount => media.length;
  List<PostMedia> get readyMedia =>
      media.where((m) => m.isReady).toList(growable: false);

  factory PostDetail.fromJson(Map<String, dynamic> json) {
    final list = json['media'] as List<dynamic>? ?? const [];
    return PostDetail(
      id: (json['id'] as num).toInt(),
      bandId: (json['bandId'] as num?)?.toInt() ?? 0,
      authorId: (json['authorId'] as num?)?.toInt() ?? 0,
      authorName: json['authorName'] as String? ?? '탈퇴한 사용자',
      title: json['title'] as String? ?? '',
      content: json['content'] as String? ?? '',
      createdAt: DateTime.parse(json['createdAt'] as String),
      editable: json['editable'] as bool? ?? false,
      media: list
          .map((e) => PostMedia.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
    );
  }
}

/// POST .../media/upload-url 응답 — presigned PUT URL 발급 결과.
class UploadTicket {
  const UploadTicket({
    required this.mediaId,
    required this.uploadUrl,
    required this.method,
    required this.requiredHeaders,
    required this.maxSizeBytes,
  });

  final int mediaId;
  final String uploadUrl;
  final String method;
  final Map<String, String> requiredHeaders;
  final int maxSizeBytes;

  factory UploadTicket.fromJson(Map<String, dynamic> json) {
    final raw = json['requiredHeaders'] as Map<String, dynamic>? ?? const {};
    return UploadTicket(
      mediaId: (json['mediaId'] as num).toInt(),
      uploadUrl: json['uploadUrl'] as String,
      method: json['method'] as String? ?? 'PUT',
      requiredHeaders: raw.map((k, v) => MapEntry(k, v.toString())),
      maxSizeBytes: (json['maxSizeBytes'] as num?)?.toInt() ?? 0,
    );
  }
}
