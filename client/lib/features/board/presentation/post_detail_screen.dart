import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../routing/app_router.dart';
import '../../auth/application/auth_controller.dart';
import '../../band/application/band_providers.dart';
import '../application/board_providers.dart';
import '../data/board_models.dart';
import '../data/board_repository.dart';

/// 게시글 상세 — 본문 + 첨부 갤러리. 작성자/밴드장은 수정·삭제, 그 외에는 신고·차단.
class PostDetailScreen extends ConsumerWidget {
  const PostDetailScreen({super.key, required this.postId});

  final int postId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final band = ref.watch(currentBandProvider);
    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text('밴드를 먼저 선택해 주세요.',
              style: TextStyle(color: AppColors.textDim)),
        ),
      );
    }

    final key = (bandId: band.id, postId: postId);
    final detailAsync = ref.watch(postDetailProvider(key));
    final meId = ref.watch(authControllerProvider).user?.id;

    return Scaffold(
      appBar: AppBar(
        title: const Text('게시글',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
        actions: [
          detailAsync.maybeWhen(
            data: (d) => _OverflowMenu(
              detail: d,
              isMine: meId != null && meId == d.authorId,
              onEdit: () async {
                final changed =
                    await context.push<bool>(Routes.editPost(postId));
                if (changed == true) ref.invalidate(postDetailProvider(key));
              },
              onDelete: () => _delete(context, ref, band.id),
              onReport: () => _report(context, ref, 'POST', d.id),
              onBlock: () => _block(context, ref, d.authorId, d.authorName),
            ),
            orElse: () => const SizedBox.shrink(),
          ),
        ],
      ),
      body: detailAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  e is ApiException ? e.message : '게시글을 불러오지 못했습니다.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.textDim),
                ),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: () => ref.invalidate(postDetailProvider(key)),
                  child: const Text('다시 시도'),
                ),
              ],
            ),
          ),
        ),
        data: (d) => RefreshIndicator(
          color: AppColors.primary,
          backgroundColor: AppColors.surface,
          onRefresh: () async {
            ref.invalidate(postDetailProvider(key));
            await ref.read(postDetailProvider(key).future);
          },
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 14, 20, 44),
            children: [
              Text(
                d.title,
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                  height: 1.35,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '${d.authorName} · ${Fmt.dateTimeKo(d.createdAt)}',
                style: const TextStyle(fontSize: 12, color: AppColors.textDim),
              ),
              const SizedBox(height: 18),
              SelectableText(
                d.content,
                style: const TextStyle(
                  fontSize: 14,
                  height: 1.7,
                  color: AppColors.textPrimary,
                ),
              ),
              if (d.media.isNotEmpty) ...[
                const SizedBox(height: 22),
                for (final m in d.media) ...[
                  _MediaBlock(
                    media: m,
                    onReport: () => _report(context, ref, 'MEDIA', m.id),
                  ),
                  const SizedBox(height: 10),
                ],
                const SizedBox(height: 2),
                const Text(
                  '첨부를 길게 누르면 신고할 수 있어요.',
                  style: TextStyle(fontSize: 10.5, color: AppColors.textFaint),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _delete(BuildContext context, WidgetRef ref, int bandId) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('게시글을 삭제할까요?', style: TextStyle(fontSize: 16)),
        content: const Text(
          '삭제하면 첨부한 사진·영상도 함께 사라집니다.',
          style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('삭제', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref
          .read(boardRepositoryProvider)
          .delete(bandId: bandId, postId: postId);
      ref.read(boardFeedProvider(bandId).notifier).refresh();
      if (context.mounted) {
        context.pop();
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('삭제됐어요.')));
      }
    } on ApiException catch (e) {
      _snack(context, e.message);
    } catch (_) {
      _snack(context, '삭제하지 못했습니다.');
    }
  }

  Future<void> _report(
    BuildContext context,
    WidgetRef ref,
    String targetType,
    int targetId,
  ) async {
    final controller = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('신고', style: TextStyle(fontSize: 16)),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 500,
          minLines: 2,
          maxLines: 4,
          decoration: const InputDecoration(hintText: '신고 사유를 적어주세요.'),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, controller.text.trim()),
            child: const Text('접수'),
          ),
        ],
      ),
    );
    if (reason == null || reason.isEmpty) return;
    try {
      await ref.read(boardRepositoryProvider).report(
            targetType: targetType,
            targetId: targetId,
            reason: reason,
          );
      _snack(context, '신고가 접수됐어요.');
    } on ApiException catch (e) {
      _snack(context, e.message);
    } catch (_) {
      _snack(context, '신고하지 못했습니다.');
    }
  }

  Future<void> _block(
    BuildContext context,
    WidgetRef ref,
    int userId,
    String name,
  ) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: Text('$name 님을 차단할까요?', style: const TextStyle(fontSize: 16)),
        content: const Text(
          '차단하면 게시판에서 서로의 글이 보이지 않습니다. 설정에서 해제할 수 있어요.',
          style:
              TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.5),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('차단', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;
    final band = ref.read(currentBandProvider);
    try {
      await ref.read(boardRepositoryProvider).blockUser(userId);
      if (band != null) {
        ref.read(boardFeedProvider(band.id).notifier).refresh();
      }
      if (context.mounted) {
        context.pop();
        _snack(context, '차단했어요.');
      }
    } on ApiException catch (e) {
      _snack(context, e.message);
    } catch (_) {
      _snack(context, '차단하지 못했습니다.');
    }
  }

  static void _snack(BuildContext context, String msg) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _OverflowMenu extends StatelessWidget {
  const _OverflowMenu({
    required this.detail,
    required this.isMine,
    required this.onEdit,
    required this.onDelete,
    required this.onReport,
    required this.onBlock,
  });

  final PostDetail detail;
  final bool isMine;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  final VoidCallback onReport;
  final VoidCallback onBlock;

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      color: AppColors.surface,
      icon: const Icon(Icons.more_vert, color: AppColors.textSecondary),
      onSelected: (v) {
        switch (v) {
          case 'edit':
            onEdit();
          case 'delete':
            onDelete();
          case 'report':
            onReport();
          case 'block':
            onBlock();
        }
      },
      itemBuilder: (context) => [
        if (detail.editable) ...[
          const PopupMenuItem(value: 'edit', child: Text('수정')),
          const PopupMenuItem(
            value: 'delete',
            child: Text('삭제', style: TextStyle(color: AppColors.danger)),
          ),
        ],
        if (!isMine) ...[
          const PopupMenuItem(value: 'report', child: Text('신고')),
          const PopupMenuItem(value: 'block', child: Text('작성자 차단')),
        ],
      ],
    );
  }
}

class _MediaBlock extends StatelessWidget {
  const _MediaBlock({required this.media, required this.onReport});

  final PostMedia media;
  final VoidCallback onReport;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onLongPress: onReport,
      child: _content(context),
    );
  }

  Widget _content(BuildContext context) {
    if (media.state == MediaState.expired) {
      return _frame(
        const _Note(icon: Icons.schedule, text: '보관기한이 지나 더 이상 볼 수 없는 첨부예요.'),
      );
    }
    if (!media.isReady) {
      return _frame(
        const _Note(icon: Icons.hourglass_empty, text: '업로드 처리 중인 첨부예요.'),
      );
    }
    if (media.isVideo) {
      // 한 글에 영상이 여러 개일 수 있어 인라인으로 다 초기화하지 않는다.
      // 이미지와 같은 방식으로, 탭하면 전체화면에서 재생한다.
      return GestureDetector(
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => _FullVideo(url: media.downloadUrl!),
          ),
        ),
        child: _frame(
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: Colors.black45,
                  borderRadius: BorderRadius.circular(26),
                  border: Border.all(color: AppColors.borderStrong),
                ),
                child: const Icon(
                  Icons.play_arrow,
                  color: Colors.white,
                  size: 30,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '영상 첨부 · ${_sizeKo(media.sizeBytes)}',
                style: const TextStyle(fontSize: 12, color: AppColors.textDim),
              ),
            ],
          ),
        ),
      );
    }
    return GestureDetector(
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => _FullImage(url: media.downloadUrl!),
        ),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(14),
        child: Image.network(
          media.downloadUrl!,
          fit: BoxFit.cover,
          width: double.infinity,
          errorBuilder: (_, __, ___) => _frame(const _Note(
              icon: Icons.broken_image_outlined, text: '이미지를 불러오지 못했어요.')),
          loadingBuilder: (context, child, progress) {
            if (progress == null) return child;
            return _frame(const Center(
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ));
          },
        ),
      ),
    );
  }

  Widget _frame(Widget child) {
    return Container(
      height: 160,
      decoration: BoxDecoration(
        color: AppColors.surfaceAlt,
        borderRadius: BorderRadius.circular(14),
      ),
      alignment: Alignment.center,
      padding: const EdgeInsets.all(16),
      child: child,
    );
  }
}

class _Note extends StatelessWidget {
  const _Note({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, color: AppColors.textFaint),
        const SizedBox(height: 8),
        Text(
          text,
          textAlign: TextAlign.center,
          style: const TextStyle(
              fontSize: 12, color: AppColors.textDim, height: 1.5),
        ),
      ],
    );
  }
}

/// 전체화면 영상 재생.
///
/// 컨트롤은 video_player 가 주는 [VideoProgressIndicator] 로 충분해서 별도 UI 패키지를
/// 붙이지 않았다. 화면을 탭하면 재생/일시정지.
///
/// [url] 은 만료가 짧은 presigned URL 이라 재생 중 만료될 수 있다 — 그 경우 에러 안내를
/// 보여주고, 사용자는 뒤로 갔다 다시 열면 새 URL 을 받는다.
class _FullVideo extends StatefulWidget {
  const _FullVideo({required this.url});

  final String url;

  @override
  State<_FullVideo> createState() => _FullVideoState();
}

class _FullVideoState extends State<_FullVideo> {
  late final VideoPlayerController _controller;
  bool _ready = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _controller = VideoPlayerController.networkUrl(Uri.parse(widget.url));
    _controller.initialize().then((_) {
      if (!mounted) return;
      setState(() => _ready = true);
      _controller.play();
    }).catchError((Object e) {
      if (!mounted) return;
      setState(() => _error = '영상을 재생할 수 없어요.');
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(backgroundColor: Colors.black, foregroundColor: Colors.white),
      body: Center(child: _body()),
    );
  }

  Widget _body() {
    if (_error != null) {
      return Text(
        _error!,
        style: const TextStyle(fontSize: 13, color: AppColors.textDim),
      );
    }
    if (!_ready) {
      return const CircularProgressIndicator();
    }
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        GestureDetector(
          onTap: () => setState(() {
            _controller.value.isPlaying ? _controller.pause() : _controller.play();
          }),
          child: AspectRatio(
            aspectRatio: _controller.value.aspectRatio,
            child: Stack(
              alignment: Alignment.center,
              children: [
                VideoPlayer(_controller),
                // 일시정지 상태에서만 큰 재생 아이콘을 덮어 보여준다.
                if (!_controller.value.isPlaying)
                  Container(
                    width: 64,
                    height: 64,
                    decoration: const BoxDecoration(
                      color: Colors.black54,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.play_arrow,
                        color: Colors.white, size: 38),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: VideoProgressIndicator(
            _controller,
            allowScrubbing: true,
            colors: const VideoProgressColors(playedColor: AppColors.primary),
          ),
        ),
      ],
    );
  }
}

class _FullImage extends StatelessWidget {
  const _FullImage({required this.url});
  final String url;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(backgroundColor: Colors.black),
      body: Center(
        child: InteractiveViewer(
          maxScale: 5,
          child: Image.network(url),
        ),
      ),
    );
  }
}

String _sizeKo(int bytes) {
  if (bytes >= 1024 * 1024) {
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)}MB';
  }
  if (bytes >= 1024) return '${(bytes / 1024).toStringAsFixed(0)}KB';
  return '$bytes B';
}
