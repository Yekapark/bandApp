import 'package:flutter/foundation.dart' show Uint8List;
import 'dart:async' show unawaited;

import 'package:flutter/foundation.dart' show Uint8List, kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:video_compress/video_compress.dart';

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
const _videoMaxBytes = 200 * 1024 * 1024;

/// 게시글 작성/수정. [postId] 가 없으면 새 글, 있으면 수정.
///
/// 첨부는 글에 매달리는 구조라 글이 없으면 올릴 수 없다. 그래서 새 글에서는 고른 파일을
/// [_pending] 에 모아 뒀다가 **등록 버튼을 누를 때 글 생성 → 첨부 업로드**를 이어서 한다.
/// 사용자 입장에서는 쓰면서 사진을 고르고 한 번에 올리는 것으로 보인다.
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

  /// 아직 서버에 올리지 않은 첨부(새 글에서 고른 것). 글이 생긴 뒤 순서대로 올라간다.
  List<_PendingMedia> _pending = const [];

  /// 영상 압축 진행률(0~100). 압축 중이 아니면 null. 6분짜리는 30초 넘게 걸려서
  /// 스피너만 돌리면 멈춘 줄 안다.
  double? _compressPct;

  bool get _isEdit => _postId != null;

  /// 올라간 것 + 올릴 것. 10개 상한은 이 합계로 센다.
  int get _attachmentCount => _media.length + _pending.length;

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
            const Text(
              '첨부',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 4),
            Text(
              _isEdit
                  ? '이미지 최대 10MB · 영상 최대 200MB · 글당 10개'
                  : '이미지 최대 10MB · 영상 최대 200MB · 글당 10개 · 등록할 때 함께 올라가요',
              style: const TextStyle(fontSize: 11, color: AppColors.textFaint),
            ),
            const SizedBox(height: 10),
            _MediaStrip(
              media: _media,
              pending: _pending,
              busy: _busy,
              compressPct: _compressPct,
              onAdd: () => _addAttachment(bandId),
              onRemove: (m) => _removeMedia(bandId, m),
              onRemovePending: _removePending,
            ),
            const SizedBox(height: 24),
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

  /// 등록 버튼. 글을 만든 뒤, 쓰면서 골라 둔 첨부를 순서대로 올린다.
  ///
  /// 첨부가 하나라도 실패하면 화면을 닫지 않고 실패분만 대기 목록에 남긴다 — 글은 이미
  /// 저장됐으므로 사용자는 남은 것만 다시 시도하면 된다.
  Future<void> _createThenAttach(int bandId) async {
    setState(() => _busy = true);
    try {
      final detail = await ref.read(boardRepositoryProvider).create(
            bandId: bandId,
            title: _title.text.trim(),
            content: _content.text.trim(),
          );
      if (!mounted) return;
      setState(() {
        _postId = detail.id;
        _media = detail.media;
        _dirty = false;
      });

      final failed = <_PendingMedia>[];
      for (final item in _pending) {
        final ok = await _uploadOne(bandId, detail.id, item);
        if (!ok) failed.add(item);
        if (!mounted) return;
      }
      setState(() => _pending = failed);

      ref.read(boardFeedProvider(bandId).notifier).refresh();
      // 압축본은 앱 캐시에 쌓인다 — 다 올렸으면 치운다.
      unawaited(VideoCompress.deleteAllCache());
      if (!mounted) return;

      if (failed.isEmpty) {
        Navigator.of(context).pop(true);
      } else {
        _toast('글은 등록됐어요. 첨부 ${failed.length}개는 올리지 못했어요 — 다시 시도해 주세요.');
      }
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('게시글을 등록하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// 첨부 한 건 업로드. 성공하면 true. 실패는 토스트로 알리고 호출자가 대기 목록에 남긴다.
  Future<bool> _uploadOne(int bandId, int postId, _PendingMedia item) async {
    try {
      // 파일을 통째로 메모리에 올리지 않고 스트림으로 흘려보낸다 — 영상은 수백 MB 가 된다.
      final media = await ref.read(boardRepositoryProvider).uploadMedia(
            bandId: bandId,
            postId: postId,
            contentType: item.contentType,
            sizeBytes: await item.file.length(),
            data: item.file.openRead(),
          );
      if (mounted) setState(() => _media = [..._media, media]);
      return true;
    } on ApiException catch (e) {
      _toast(e.message);
      return false;
    } catch (_) {
      _toast('첨부를 올리지 못했습니다.');
      return false;
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

  /// 첨부 고르기. 글이 이미 있으면 바로 올리고, 새 글이면 대기 목록에 담아 둔다
  /// (등록 버튼을 누를 때 [_createThenAttach] 가 이어서 올린다).
  Future<void> _addAttachment(int bandId) async {
    if (_attachmentCount >= 10) {
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

    // 영상은 압축한 뒤에 재야 한다 — 폰 기본 촬영은 6분이면 700MB 를 넘지만 압축하면 들어온다.
    final item = _PendingMedia(
        await _compressIfVideo(file, contentType), contentType);

    // 길이만 확인한다 — 상한 검사하려고 파일을 통째로 메모리에 올릴 이유가 없다.
    final limit =
        contentType.startsWith('video/') ? _videoMaxBytes : _imageMaxBytes;
    if (await item.file.length() > limit) {
      _toast(contentType.startsWith('video/')
          ? '영상이 너무 길어요. 압축해도 200MB를 넘습니다.'
          : '이미지는 최대 10MB까지예요.');
      return;
    }

    // 새 글: 아직 글이 없어 매달 곳이 없다. 등록할 때 함께 올린다.
    if (!_isEdit) {
      if (!mounted) return;
      setState(() {
        _pending = [..._pending, item];
        _dirty = true;
      });
      return;
    }

    setState(() => _busy = true);
    try {
      if (await _uploadOne(bandId, _postId!, item)) {
        ref.invalidate(
          postDetailProvider((bandId: bandId, postId: _postId!)),
        );
        ref.read(boardFeedProvider(bandId).notifier).refresh();
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// 영상이면 720p 로 압축해 돌려준다. 이미지·웹이거나 실패하면 원본 그대로.
  ///
  /// 폰 기본 촬영(1080p 17Mbps)은 6분이면 700MB 가 넘어 상한(200MB)에 들어가지 않는다.
  /// 720p 로 줄이면 6분이 대략 90MB 다. 합주 영상은 소리가 본체라 이 정도면 충분하다.
  ///
  /// 압축은 30초 넘게 걸릴 수 있어 진행률을 보여준다. 실패하면 원본으로 진행하고,
  /// 상한을 넘으면 호출한 쪽의 크기 검사에서 걸린다.
  Future<XFile> _compressIfVideo(XFile file, String contentType) async {
    if (kIsWeb || !contentType.startsWith('video/')) return file;

    final sub = VideoCompress.compressProgress$.subscribe((p) {
      if (mounted) setState(() => _compressPct = p);
    });
    setState(() => _compressPct = 0);
    try {
      final info = await VideoCompress.compressVideo(
        file.path,
        quality: VideoQuality.Res1280x720Quality,
      );
      final path = info?.path;
      return path == null ? file : XFile(path);
    } catch (_) {
      return file;
    } finally {
      sub.unsubscribe();
      if (mounted) setState(() => _compressPct = null);
    }
  }

  /// 아직 안 올라간 첨부 빼기 — 서버에 아무것도 없으니 목록에서만 지운다.
  void _removePending(_PendingMedia item) {
    setState(() => _pending = _pending.where((x) => x != item).toList());
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

/// 아직 서버에 올리지 않은 첨부.
///
/// 바이트를 들고 있지 않고 [XFile] 참조만 둔다 — 큰 영상을 10개까지 메모리에 쥐고 있을
/// 이유가 없다. 실제 읽기는 업로드 직전에 한 번만 한다.
class _PendingMedia {
  const _PendingMedia(this.file, this.contentType);

  final XFile file;
  final String contentType;

  bool get isVideo => contentType.startsWith('video/');
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
    required this.pending,
    required this.busy,
    required this.compressPct,
    required this.onAdd,
    required this.onRemove,
    required this.onRemovePending,
  });

  final List<PostMedia> media;

  /// 아직 안 올라간 것들. 올라간 첨부 뒤에 흐리게 붙는다.
  final List<_PendingMedia> pending;
  final bool busy;

  /// 영상 압축 진행률(0~100). 압축 중이 아니면 null.
  final double? compressPct;
  final VoidCallback onAdd;
  final void Function(PostMedia) onRemove;
  final void Function(_PendingMedia) onRemovePending;

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
          for (final p in pending)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: _PendingThumb(
                item: p,
                onRemove: () => onRemovePending(p),
              ),
            ),
          GestureDetector(
            onTap: (busy || compressPct != null) ? null : onAdd,
            child: Container(
              width: 92,
              height: 92,
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.borderStrong),
              ),
              child: compressPct != null
                  // 압축은 30초 넘게 걸릴 수 있어 진행률을 보여준다.
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              value: compressPct! / 100,
                            ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '압축 ${compressPct!.round()}%',
                            style: const TextStyle(
                                fontSize: 9.5, color: AppColors.textFaint),
                          ),
                        ],
                      ),
                    )
                  : busy
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

/// 대기 중인 첨부 미리보기. 아직 서버에 없으므로 "등록 시 올라감"을 알 수 있게 표시한다.
class _PendingThumb extends StatelessWidget {
  const _PendingThumb({required this.item, required this.onRemove});

  final _PendingMedia item;
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
            // dart:io 의 File 을 쓰면 웹 빌드가 깨진다. XFile 로 바이트를 읽어
            // Image.memory 로 그린다. cacheWidth 로 썸네일 크기까지만 디코드한다.
            child: item.isVideo
                ? _icon(Icons.movie_outlined)
                : FutureBuilder<Uint8List>(
                    future: item.file.readAsBytes(),
                    builder: (_, snap) => snap.hasData
                        ? Image.memory(
                            snap.data!,
                            fit: BoxFit.cover,
                            cacheWidth: 184,
                            errorBuilder: (_, __, ___) =>
                                _icon(Icons.image_outlined),
                          )
                        : _icon(Icons.image_outlined),
                  ),
          ),
        ),
        Positioned.fill(
          child: IgnorePointer(
            child: Container(
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.35),
                borderRadius: BorderRadius.circular(12),
              ),
              alignment: Alignment.bottomCenter,
              padding: const EdgeInsets.only(bottom: 6),
              child: const Text(
                '등록 시 업로드',
                style: TextStyle(fontSize: 9.5, color: Colors.white),
              ),
            ),
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

  Widget _icon(IconData icon) => Container(
        color: AppColors.surfaceAlt,
        alignment: Alignment.center,
        child: Icon(icon, color: AppColors.textFaint),
      );
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
