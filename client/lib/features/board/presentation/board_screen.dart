import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../band/application/band_providers.dart';
import '../application/board_providers.dart';
import '../data/board_models.dart';

/// 밴드 게시판 피드 — 합주 사진·영상을 공유하는 글 목록(커서 페이징).
class BoardScreen extends ConsumerStatefulWidget {
  const BoardScreen({super.key});

  @override
  ConsumerState<BoardScreen> createState() => _BoardScreenState();
}

class _BoardScreenState extends ConsumerState<BoardScreen> {
  final _scroll = ScrollController();
  int? _bandId;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scroll.removeListener(_onScroll);
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_bandId == null) return;
    if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 320) {
      ref.read(boardFeedProvider(_bandId!).notifier).loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text(
            '밴드를 먼저 선택해 주세요.',
            style: TextStyle(color: AppColors.textDim),
          ),
        ),
      );
    }
    _bandId = band.id;
    final feedAsync = ref.watch(boardFeedProvider(band.id));

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '게시판',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final created = await context.push<bool>(Routes.newPost);
          if (created == true) {
            ref.read(boardFeedProvider(band.id).notifier).refresh();
          }
        },
        backgroundColor: AppColors.primary,
        foregroundColor: AppColors.onPrimary,
        icon: const Icon(Icons.edit_outlined, size: 18),
        label: const Text('글쓰기', style: TextStyle(fontWeight: FontWeight.w700)),
      ),
      body: RefreshIndicator(
        color: AppColors.primary,
        backgroundColor: AppColors.surface,
        onRefresh: () =>
            ref.read(boardFeedProvider(band.id).notifier).refresh(),
        child: feedAsync.when(
          loading: () =>
              const _CenterInScroll(child: CircularProgressIndicator()),
          error: (e, _) => _CenterInScroll(
            child: _ErrorBody(
              message: e is ApiException ? e.message : '게시글을 불러오지 못했습니다.',
              onRetry: () =>
                  ref.read(boardFeedProvider(band.id).notifier).refresh(),
            ),
          ),
          data: (feed) {
            if (feed.posts.isEmpty) {
              return const _CenterInScroll(
                child: Text(
                  '아직 게시글이 없어요.\n첫 합주 사진을 올려보세요.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppColors.textDim, height: 1.6),
                ),
              );
            }
            return ListView.separated(
              controller: _scroll,
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
              itemCount: feed.posts.length + (feed.hasNext ? 1 : 0),
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, i) {
                if (i >= feed.posts.length) {
                  return const Padding(
                    padding: EdgeInsets.symmetric(vertical: 20),
                    child: Center(
                      child: SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    ),
                  );
                }
                final post = feed.posts[i];
                return _PostCard(
                  post: post,
                  onTap: () async {
                    await context.push(Routes.post(post.id));
                    ref.invalidate(
                      postDetailProvider(
                        (bandId: band.id, postId: post.id),
                      ),
                    );
                    ref.read(boardFeedProvider(band.id).notifier).refresh();
                  },
                );
              },
            );
          },
        ),
      ),
    );
  }
}

class _PostCard extends StatelessWidget {
  const _PostCard({required this.post, required this.onTap});

  final PostSummary post;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surfaceCard,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      post.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  if (post.mediaCount > 0) ...[
                    const SizedBox(width: 8),
                    _MediaBadge(count: post.mediaCount),
                  ],
                ],
              ),
              const SizedBox(height: 6),
              Text(
                '${post.authorName} · ${_relativeKo(post.createdAt)}',
                style: const TextStyle(
                  fontSize: 11.5,
                  color: AppColors.textDim,
                ),
              ),
              if (post.preview.trim().isNotEmpty) ...[
                const SizedBox(height: 10),
                Text(
                  post.preview,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 13,
                    height: 1.55,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
              if (post.thumbnailUrl != null) ...[
                const SizedBox(height: 12),
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: AspectRatio(
                    aspectRatio: 16 / 10,
                    child: Image.network(
                      post.thumbnailUrl!,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        color: AppColors.surfaceAlt,
                        alignment: Alignment.center,
                        child: const Icon(Icons.broken_image_outlined,
                            color: AppColors.textFaint),
                      ),
                      loadingBuilder: (context, child, progress) {
                        if (progress == null) return child;
                        return Container(
                          color: AppColors.surfaceAlt,
                          alignment: Alignment.center,
                          child: const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        );
                      },
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _MediaBadge extends StatelessWidget {
  const _MediaBadge({required this.count});
  final int count;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.surfaceAlt,
        borderRadius: BorderRadius.circular(99),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.photo_library_outlined,
              size: 12, color: AppColors.textSecondary),
          const SizedBox(width: 4),
          Text('$count',
              style: AppTypography.mono(
                  fontSize: 10, color: AppColors.textSecondary)),
        ],
      ),
    );
  }
}

/// 스크롤 가능한 영역 안에서 가운데 정렬 — RefreshIndicator 가 항상 동작하도록.
class _CenterInScroll extends StatelessWidget {
  const _CenterInScroll({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: SizedBox(
          height: constraints.maxHeight,
          child: Center(
              child: Padding(
            padding: const EdgeInsets.all(32),
            child: child,
          )),
        ),
      ),
    );
  }
}

class _ErrorBody extends StatelessWidget {
  const _ErrorBody({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(message,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.textDim)),
        const SizedBox(height: 12),
        TextButton(onPressed: onRetry, child: const Text('다시 시도')),
      ],
    );
  }
}

/// "3분 전" / "2시간 전" / "3일 전" / "9월 2일".
String _relativeKo(DateTime utc) {
  final now = DateTime.now();
  final d = now.difference(utc.toLocal());
  if (d.inMinutes < 1) return '방금';
  if (d.inMinutes < 60) return '${d.inMinutes}분 전';
  if (d.inHours < 24) return '${d.inHours}시간 전';
  if (d.inDays < 7) return '${d.inDays}일 전';
  final local = utc.toLocal();
  return '${local.month}월 ${local.day}일';
}
