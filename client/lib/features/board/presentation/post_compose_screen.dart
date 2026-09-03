import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../band/application/band_providers.dart';
import '../application/board_providers.dart';
import '../data/board_models.dart';
import '../data/board_repository.dart';

const _allowedTypes = {
  'image/jpeg',
  'image/png',
  'image/webp',
  'video/mp4',
  'video/quicktime',
};
const _imageMaxBytes = 10 * 1024 * 1024;
const _videoMaxBytes = 50 * 1024 * 1024;

/// 게시글 작성/수정. [postId] 가 없으면 새 글, 있으면 수정.
///
/// 새 글은 본문 등록 직후 같은 화면에서 첨부를 이어서 올릴 수 있도록 수정 모드로 전환된다.
class PostComposeScreen extends ConsumerStatefulWidget {
  const PostComposeScreen({super.key, this.postId});

  final int? postId;

  @override
  ConsumerState<PostComposeScreen> createState() => _PostComposeScreenState();
}

class _PostComposeScreenState extends ConsumerState<PostComposeScreen> {
  final _title = TextEditingController();
  final _content = TextEditingController();
  final _picker = ImagePicker();

  int? _postId;
  bool _busy = false;
  bool _prefilled = false;
  bool _dirty = false;
  List<PostMedia> _media = const [];

  bool get _isEdit => _postId != null;

  @override
  void initState() {
    super.initState();
    _postId = widget.postId;
    _title.addListener(() => _dirty = true);
    _content.addListener(() => _dirty = true);
  }

  @override
  void dispose() {
    _title.dispose();
    _content.dispose();
    super.dispose();
  }

  bool get _canSubmit =>
      _title.text.trim().isNotEmpty && _content.text.trim().isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text('밴드를 먼저 선택해 주세요.',
              style: TextStyle(color: AppColors.textDim)),
        ),
      );
    }

    // 수정 모드 최초 진입 시 기존 값 채우기.
    if (widget.postId != null && !_prefilled) {
      final detailAsync = ref
          .watch(postDetailProvider((bandId: band.id, postId: widget.postId!)));
      return detailAsync.when(
        loading: () => const Scaffold(
          body: Center(child: CircularProgressIndicator()),
        ),
        error: (e, _) => Scaffold(
          appBar: AppBar(),
          body: Center(
            child: Text(
              e is ApiException ? e.message : '게시글을 불러오지 못했습니다.',
              style: const TextStyle(color: AppColors.textDim),
            ),
          ),
        ),
        data: (detail) {
          _title.text = detail.title;
          _content.text = detail.content;
          _media = detail.media;
          _prefilled = true;
          _dirty = false;
          return _form(band.id);
        },
      );
    }

    return _form(band.id);
  }

  Widget _form(int bandId) {
    return PopScope(
      canPop: !_dirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final leave = await _confirmDiscard();
        if (leave && mounted) Navigator.of(context).pop(_isEdit ? true : false);
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            widget.postId != null ? '게시글 수정' : (_isEdit ? '사진 추가' : '새 게시글'),
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
          ),
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(18, 12, 18, 40),
          children: [
            TextField(
              controller: _title,
              maxLength: 100,
              decoration: const InputDecoration(
                hintText: '제목',
                counterText: '',
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _content,
              maxLength: 4000,
              minLines: 6,
              maxLines: 14,
              decoration: const InputDecoration(
                hintText: '합주는 어땠나요? 사진 설명을 적어보세요.',
                alignLabelWithHint: true,
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 20),
            if (_isEdit) ...[
              const Text(
                '첨부',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 4),
              const Text(
                '이미지 최대 10MB · 영상 최대 50MB · 글당 10개',
                style: TextStyle(fontSize: 11, color: AppColors.textFaint),
              ),
              const SizedBox(height: 10),
              _MediaStrip(
                media: _media,
                busy: _busy,
                onAdd: () => _pickAndUpload(bandId),
                onRemove: (m) => _removeMedia(bandId, m),
              ),
              const SizedBox(height: 24),
            ],
            if (widget.postId != null)
              PrimaryButton(
                label: '저장',
                loading: _busy,
                enabled: _canSubmit,
                onPressed: () => _saveEdit(bandId),
              )
            else if (_isEdit)
              PrimaryButton(
                label: '완료',
                loading: _busy,
                onPressed: () => Navigator.of(context).pop(true),
              )
            else
              PrimaryButton(
                label: '등록',
                loading: _busy,
                enabled: _canSubmit,
                onPressed: () => _createThenAttach(bandId),
              ),
          ],
        ),
      ),
    );
  }

  Future<bool> _confirmDiscard() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('작성을 취소할까요?', style: TextStyle(fontSize: 16)),
        content: const Text(
          '입력한 내용은 저장되지 않아요.',
          style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('계속 작성'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('나가기', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    return ok ?? false;
  }

  Future<void> _createThenAttach(int bandId) async {
    setState(() => _busy = true);
    try {
      final detail = await ref.read(boardRepositoryProvider).create(
            bandId: bandId,
            title: _title.text.trim(),
            content: _content.text.trim(),
          );
      ref.read(boardFeedProvider(bandId).notifier).refresh();
      if (!mounted) return;
      setState(() {
        _postId = detail.id;
        _media = detail.media;
        _dirty = false;
      });
      _toast('등록됐어요. 사진·영상을 추가할 수 있어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('게시글을 등록하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _saveEdit(int bandId) async {
    setState(() => _busy = true);
    try {
      await ref.read(boardRepositoryProvider).update(
            bandId: bandId,
            postId: _postId!,
            title: _title.text.trim(),
            content: _content.text.trim(),
          );
      ref.read(boardFeedProvider(bandId).notifier).refresh();
      ref.invalidate(
        postDetailProvider((bandId: bandId, postId: _postId!)),
      );
      if (mounted) {
        _dirty = false;
        Navigator.of(context).pop(true);
      }
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('수정하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _pickAndUpload(int bandId) async {
    if (_media.length >= 10) {
      _toast('첨부는 글당 10개까지예요.');
      return;
    }
    final kind = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_outlined),
              title: const Text('사진'),
              onTap: () => Navigator.pop(ctx, 'image'),
            ),
            ListTile(
              leading: const Icon(Icons.videocam_outlined),
              title: const Text('영상'),
              onTap: () => Navigator.pop(ctx, 'video'),
            ),
          ],
        ),
      ),
    );
    if (kind == null) return;

    final XFile? file = kind == 'video'
        ? await _picker.pickVideo(source: ImageSource.gallery)
        : await _picker.pickImage(
            source: ImageSource.gallery, imageQuality: 88);
    if (file == null) return;

    final contentType = _resolveContentType(file, kind);
    if (contentType == null || !_allowedTypes.contains(contentType)) {
      _toast('지원하지 않는 형식이에요. (JPG·PNG·WEBP·MP4·MOV)');
      return;
    }

    final bytes = await file.readAsBytes();
    final limit =
        contentType.startsWith('video/') ? _videoMaxBytes : _imageMaxBytes;
    if (bytes.length > limit) {
      _toast(contentType.startsWith('video/')
          ? '영상은 최대 50MB까지예요.'
          : '이미지는 최대 10MB까지예요.');
      return;
    }

    setState(() => _busy = true);
    try {
      final media = await ref.read(boardRepositoryProvider).uploadMedia(
            bandId: bandId,
            postId: _postId!,
            contentType: contentType,
            bytes: bytes,
          );
      if (mounted) setState(() => _media = [..._media, media]);
      ref.invalidate(
        postDetailProvider((bandId: bandId, postId: _postId!)),
      );
      ref.read(boardFeedProvider(bandId).notifier).refresh();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('첨부를 올리지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _removeMedia(int bandId, PostMedia m) async {
    setState(() => _busy = true);
    try {
      await ref.read(boardRepositoryProvider).deleteMedia(
            bandId: bandId,
            postId: _postId!,
            mediaId: m.id,
          );
      if (mounted) {
        setState(() => _media = _media.where((x) => x.id != m.id).toList());
      }
      ref.invalidate(
        postDetailProvider((bandId: bandId, postId: _postId!)),
      );
      ref.read(boardFeedProvider(bandId).notifier).refresh();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('첨부를 지우지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

String? _resolveContentType(XFile file, String kind) {
  final mt = file.mimeType?.toLowerCase();
  if (mt != null && _allowedTypes.contains(mt)) return mt;
  final name = file.name.toLowerCase();
  if (name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'image/jpeg';
  if (name.endsWith('.png')) return 'image/png';
  if (name.endsWith('.webp')) return 'image/webp';
  if (name.endsWith('.mp4')) return 'video/mp4';
  if (name.endsWith('.mov')) return 'video/quicktime';
  // 확장자를 못 읽는 플랫폼: 선택 종류로 최선의 추정.
  return kind == 'video' ? 'video/mp4' : 'image/jpeg';
}

class _MediaStrip extends StatelessWidget {
  const _MediaStrip({
    required this.media,
    required this.busy,
    required this.onAdd,
    required this.onRemove,
  });

  final List<PostMedia> media;
  final bool busy;
  final VoidCallback onAdd;
  final void Function(PostMedia) onRemove;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 92,
      child: ListView(
        scrollDirection: Axis.horizontal,
        children: [
          for (final m in media)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: _MediaThumb(media: m, onRemove: () => onRemove(m)),
            ),
          GestureDetector(
            onTap: busy ? null : onAdd,
            child: Container(
              width: 92,
              height: 92,
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.borderStrong),
              ),
              child: busy
                  ? const Center(
                      child: SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    )
                  : const Icon(Icons.add, color: AppColors.textSecondary),
            ),
          ),
        ],
      ),
    );
  }
}

class _MediaThumb extends StatelessWidget {
  const _MediaThumb({required this.media, required this.onRemove});

  final PostMedia media;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: SizedBox(
            width: 92,
            height: 92,
            child: media.isImage && media.isReady
                ? Image.network(media.downloadUrl!,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => _placeholder(media))
                : _placeholder(media),
          ),
        ),
        Positioned(
          top: 2,
          right: 2,
          child: GestureDetector(
            onTap: onRemove,
            child: Container(
              decoration: const BoxDecoration(
                color: Colors.black54,
                shape: BoxShape.circle,
              ),
              padding: const EdgeInsets.all(3),
              child: const Icon(Icons.close, size: 14, color: Colors.white),
            ),
          ),
        ),
      ],
    );
  }

  Widget _placeholder(PostMedia m) {
    return Container(
      color: AppColors.surfaceAlt,
      alignment: Alignment.center,
      child: Icon(
        m.isVideo ? Icons.movie_outlined : Icons.image_outlined,
        color: AppColors.textFaint,
      ),
    );
  }
}
