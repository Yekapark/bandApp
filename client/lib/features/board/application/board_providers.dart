import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/board_models.dart';
import '../data/board_repository.dart';

/// 게시판 피드 한 화면 분량의 상태 (커서 페이징).
class BoardFeedState {
  const BoardFeedState({
    required this.posts,
    required this.nextCursor,
    required this.hasNext,
    this.loadingMore = false,
  });

  final List<PostSummary> posts;
  final String? nextCursor;
  final bool hasNext;
  final bool loadingMore;

  BoardFeedState copyWith({
    List<PostSummary>? posts,
    String? nextCursor,
    bool? hasNext,
    bool? loadingMore,
  }) {
    return BoardFeedState(
      posts: posts ?? this.posts,
      nextCursor: nextCursor ?? this.nextCursor,
      hasNext: hasNext ?? this.hasNext,
      loadingMore: loadingMore ?? this.loadingMore,
    );
  }
}

/// 밴드별 게시판 피드. 최초 로드는 [build], 이후 [loadMore] 로 다음 페이지를 이어 붙인다.
final boardFeedProvider =
    AsyncNotifierProvider.family<BoardFeed, BoardFeedState, int>(BoardFeed.new);

class BoardFeed extends FamilyAsyncNotifier<BoardFeedState, int> {
  int get _bandId => arg;

  @override
  Future<BoardFeedState> build(int bandId) async {
    final page = await ref.watch(boardRepositoryProvider).list(bandId: bandId);
    return BoardFeedState(
      posts: page.posts,
      nextCursor: page.nextCursor,
      hasNext: page.hasNext,
    );
  }

  /// 다음 페이지를 받아 현재 목록 뒤에 붙인다. 이미 로딩 중이거나 끝이면 무시.
  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasNext || current.loadingMore) return;

    state = AsyncData(current.copyWith(loadingMore: true));
    try {
      final page = await ref.read(boardRepositoryProvider).list(
            bandId: _bandId,
            cursor: current.nextCursor,
          );
      state = AsyncData(
        current.copyWith(
          posts: [...current.posts, ...page.posts],
          nextCursor: page.nextCursor,
          hasNext: page.hasNext,
          loadingMore: false,
        ),
      );
    } catch (_) {
      state = AsyncData(current.copyWith(loadingMore: false));
      rethrow;
    }
  }

  /// 첫 페이지부터 다시 로드 (당겨서 새로고침).
  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() => build(_bandId));
  }
}

typedef PostKey = ({int bandId, int postId});

/// 게시글 상세(본문 + 첨부).
final postDetailProvider =
    FutureProvider.family<PostDetail, PostKey>((ref, key) async {
  return ref.watch(boardRepositoryProvider).detail(
        bandId: key.bandId,
        postId: key.postId,
      );
});
