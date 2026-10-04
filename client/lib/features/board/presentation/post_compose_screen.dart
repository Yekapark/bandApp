import 'package:flutter/foundation.dart' show Uint8List;
import 'dart:async' show Timer, unawaited;
import 'dart:ui' show FontFeature;

import 'package:flutter/foundation.dart' show Uint8List, kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:v_video_compressor/v_video_compressor.dart';

import '../../../routing/app_router.dart';
import '../../plan/application/plan_providers.dart';
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

  /// 영상 압축기. 상태가 없는 얇은 래퍼라 화면마다 하나 둔다.
  final _compressor = VVideoCompressor();

  /// 첨부 업로드 진행 상태. 영상은 수백 MB 라 한참 걸린다 — 아무 표시가 없으면
  /// 사용자가 앱이 멈춘 줄 안다. 몇 번째/전체와 퍼센트를 함께 보여준다.
  int _uploadIndex = 0;
  int _uploadTotal = 0;
  int _uploadPct = 0;

  bool get _isEdit => _postId != null;

  /// 화면을 연 밴드에 고정한다(POST-10·UI-06). 작성 중 밴드에서 나가거나 알림을 눌러 밴드가 바뀌면
  /// "현재 밴드" 가 다른 밴드로 넘어간다 — 등록할 때 그걸 읽으면 엉뚱한 밴드에 글이 올라간다.
  int? _bandId;

  /// 올라간 것 + 올릴 것. 10개 상한은 이 합계로 센다.
  int get _attachmentCount => _media.length + _pending.length;

  @override
  void initState() {
    super.initState();
    _postId = widget.postId;
    _bandId = ref.read(currentBandProvider)?.id;
    _title.addListener(() => _dirty = true);
    _content.addListener(() => _dirty = true);
  }

  @override
  void dispose() {
    // 압축 중에 나가면 네이티브 인코더가 끝까지 돌고 결과 파일이 캐시에 남는다(MEDIA-05).
    // 못 올린 압축본도 이 화면이 사라지면 다시 올릴 길이 없으니 함께 치운다.
    if (_compressPct != null || _pending.isNotEmpty) {
      unawaited(_compressor
          .cancelCompression()
          .whenComplete(
              () => _compressor.cleanupFiles(deleteCompressedVideos: true))
          .catchError((_) {}));
    }
    _title.dispose();
    _content.dispose();
    super.dispose();
  }

  /// 영상 압축 중에는 등록·저장을 막는다. 예전에는 압축 도중 등록을 누르면 영상 없이 글이 올라가고 화면이 닫힌 뒤,
  /// 압축이 끝난 영상은 버려졌다(닫힌 화면에 setState 오류까지).
  bool get _canSubmit =>
      _compressPct == null &&
      _title.text.trim().isNotEmpty &&
      _content.text.trim().isNotEmpty;

  /// 글은 만들어졌는데 첨부 일부를 못 올린 상태(새 글 → "사진 추가" 모드).
  bool get _hasFailedPending => _isEdit && _pending.isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final bandId = _bandId;
    if (bandId == null) {
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
          .watch(postDetailProvider((bandId: bandId, postId: widget.postId!)));
      return detailAsync.when(
        loading: () => const Scaffold(
          body: Center(child: CircularProgressIndicator()),
        ),
        error: (e, _) => Scaffold(
          appBar: AppBar(),
          body: Center(
            child: Text(
              e is ApiException ? e.message : '게시글을 불러오지 못했어요.',
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
          return _form(bandId);
        },
      );
    }

    return _form(bandId);
  }

  Widget _form(int bandId) {
    return PopScope(
      // 올리는 중·압축 중에 나가면 남은 첨부가 조용히 버려진다 — 확인을 받는다.
      canPop: !_dirty && !_busy && _compressPct == null,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final leave = await _confirmDiscard(
            midUpload: _busy || _compressPct != null || _hasFailedPending);
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
            // 무료 밴드는 첨부가 30일 뒤 사라진다. 요금제 화면과 홈 배너에만 적혀 있어서
            // 정작 올리는 순간에는 모르고 올렸다 — 사진이 사라지는 건 되돌릴 수 없으니
            // 올리기 전에 알린다. 요금제를 못 불러왔으면 아무 말도 하지 않는다(단정 금지).
            if (ref.watch(bandPlanProvider(bandId)).valueOrNull?.isPremium ==
                false) ...[
              const SizedBox(height: 3),
              const Text(
                '무료 요금제라 올린 사진·영상은 30일 뒤 사라져요.',
                style: TextStyle(fontSize: 11, color: AppColors.primarySoft),
              ),
            ],
            const SizedBox(height: 10),
            _MediaStrip(
              media: _media,
              pending: _pending,
              busy: _busy,
              compressPct: _compressPct,
              pendingFailed: _isEdit,
              onAdd: () => _addAttachment(bandId),
              onRemove: (m) => _removeMedia(bandId, m),
              onRemovePending: _removePending,
            ),
            if (_uploadTotal > 0) ...[
              const SizedBox(height: 16),
              _UploadProgress(
                index: _uploadIndex,
                total: _uploadTotal,
                pct: _uploadPct,
              ),
            ],
            const SizedBox(height: 24),
            // 수정 중 첨부를 못 올렸으면 여기서 다시 올린다("저장" 은 글만 저장하고 닫는다).
            if (widget.postId != null && _pending.isNotEmpty && !_busy) ...[
              TextButton(
                onPressed: _compressPct == null
                    ? () => _uploadQueue(bandId, List.of(_pending))
                    : null,
                child: Text('못 올린 첨부 ${_pending.length}개 다시 올리기'),
              ),
              const SizedBox(height: 8),
            ],
            if (widget.postId != null)
              PrimaryButton(
                label: '저장',
                loading: _busy,
                enabled: _canSubmit,
                onPressed: () => _saveEdit(bandId),
              )
            else if (_isEdit)
              // 못 올린 첨부가 남았으면 "완료" 대신 다시 올리기 — 예전에는 "다시 시도해 주세요" 라고만 하고
              // 다시 올릴 방법이 없었다("완료" 는 그냥 닫혀서 남은 첨부가 버려졌다).
              PrimaryButton(
                label: _hasFailedPending ? '남은 첨부 다시 올리기' : '완료',
                loading: _busy,
                enabled: _compressPct == null,
                onPressed: _hasFailedPending
                    ? () => _uploadQueue(bandId, List.of(_pending))
                    : () => Navigator.of(context).pop(true),
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

  Future<bool> _confirmDiscard({bool midUpload = false}) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('작성을 취소할까요?', style: TextStyle(fontSize: 16)),
        content: Text(
          midUpload ? '아직 올리지 못한 사진·영상은 저장되지 않아요.' : '입력한 내용은 저장되지 않아요.',
          style: const TextStyle(fontSize: 12.5, color: AppColors.textDim),
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
    if (_busy) return; // 같은 프레임의 연타 — 버튼이 아직 비활성으로 다시 그려지기 전
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
      final queue = List.of(_pending);
      setState(() => _uploadTotal = queue.length);
      for (var i = 0; i < queue.length; i++) {
        final ok = await _uploadOne(bandId, detail.id, queue[i], index: i + 1);
        if (!ok) failed.add(queue[i]);
        if (!mounted) return;
      }
      setState(() {
        _pending = failed;
        // 못 올린 첨부가 남았으면 뒤로 가기 전에 묻는다.
        _dirty = failed.isNotEmpty;
      });

      ref.read(boardFeedProvider(bandId).notifier).refresh();
      // 압축본은 앱 캐시에 쌓인다 — 다 올렸으면 치운다. 못 올린 게 남았으면 두어야 다시 올릴 수 있다.
      if (failed.isEmpty) {
        unawaited(_compressor.cleanupFiles(deleteCompressedVideos: true));
      }
      if (!mounted) return;

      if (failed.isEmpty) {
        Navigator.of(context).pop(true);
      } else {
        _toast('글은 등록됐어요. 첨부 ${failed.length}개는 올리지 못했어요 — 다시 시도해 주세요.');
      }
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('게시글을 등록하지 못했어요.');
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          _uploadTotal = 0;
        });
      }
    }
  }

  /// 첨부 한 건 업로드. 성공하면 true. 실패는 토스트로 알리고 호출자가 대기 목록에 남긴다.
  Future<bool> _uploadOne(int bandId, int postId, _PendingMedia item,
      {int index = 1}) async {
    if (mounted) {
      setState(() {
        _uploadIndex = index;
        _uploadPct = 0;
      });
    }
    try {
      // 파일을 통째로 메모리에 올리지 않고 스트림으로 흘려보낸다 — 영상은 수백 MB 가 된다.
      final media = await ref.read(boardRepositoryProvider).uploadMedia(
            bandId: bandId,
            postId: postId,
            contentType: item.contentType,
            sizeBytes: await item.file.length(),
            data: item.file.openRead(),
            // 퍼센트가 바뀔 때만 다시 그린다 — 콜백은 초당 수십 번 온다.
            onProgress: (sent, total) {
              if (!mounted || total <= 0) return;
              final pct = (sent * 100 ~/ total).clamp(0, 100);
              if (pct != _uploadPct) setState(() => _uploadPct = pct);
            },
          );
      if (mounted) setState(() => _media = [..._media, media]);
      return true;
    } on ApiException catch (e) {
      _toast(e.message);
      return false;
    } catch (_) {
      _toast('첨부를 올리지 못했어요.');
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
      _toast('수정하지 못했어요.');
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
    // 영상은 PREMIUM 전용이다(서버가 403 PLAN_REQUIRED 로 막는다). 고르게 해 놓고 압축까지
    // 시킨 뒤 거절하면 최악이라, 고르는 자리에서 잠근다. 요금제를 못 불러왔으면 잠그지
    // 않는다 — 서버가 최종 방어선이고, 통신이 불안한 것만으로 막을 이유는 없다.
    final videoLocked =
        ref.read(bandPlanProvider(bandId)).valueOrNull?.isPremium == false;

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
              enabled: !videoLocked,
              leading: Icon(
                  videoLocked ? Icons.lock_outline : Icons.videocam_outlined),
              title: const Text('영상'),
              subtitle: videoLocked
                  ? const Text('프리미엄 밴드만 올릴 수 있어요',
                      style: TextStyle(fontSize: 11))
                  : null,
              trailing: videoLocked
                  ? TextButton(
                      onPressed: () {
                        Navigator.pop(ctx);
                        context.push(Routes.plan);
                      },
                      child: const Text('요금제', style: TextStyle(fontSize: 12)),
                    )
                  : null,
              onTap: videoLocked ? null : () => Navigator.pop(ctx, 'video'),
            ),
          ],
        ),
      ),
    );
    if (kind == null) return;

    // 남은 자리만큼만 고르게 한다 — 12장 고른 뒤 "10개까지예요" 를 보는 것보다 낫다.
    final room = 10 - _attachmentCount;

    // 이미지는 고르는 시점에 줄인다 — imageQuality 만으로는 재인코딩만 되고 해상도가 그대로라,
    // 요즘 폰의 1200만 화소 사진이 4000x3000 그대로 장당 2~4MB 로 올라갔다. 긴 변 2048px 로
    // 비율 유지 축소하면 400~700KB 로 떨어진다. 피드·상세에서 보는 용도라 이 정도면 충분하고,
    // PREMIUM 은 보관기한이 무제한이라 원본을 그대로 쌓을 이유가 없다.
    //
    // 사진은 여러 장을 한 번에 고른다(합주 사진은 원래 여러 장이다). 영상은 한 번에 하나만
    // 받는다 — 압축이 건당 수십 초라 여러 개를 줄줄이 돌리면 앱이 멈춘 것처럼 보인다.
    final List<XFile> picked;
    if (kind == 'video') {
      final one = await _picker.pickVideo(source: ImageSource.gallery);
      picked = one == null ? const [] : [one];
    } else {
      picked = await _picker.pickMultiImage(
        imageQuality: 88,
        maxWidth: 2048,
        maxHeight: 2048,
        limit: room,
      );
    }
    if (picked.isEmpty) return;

    // 갤러리가 limit 을 안 지키는 기기가 있다 — 넘치면 앞에서부터 자른다.
    final files = picked.take(room).toList();
    if (picked.length > files.length) {
      _toast('첨부는 글당 10개까지예요. 앞의 ${files.length}개만 담았어요.');
    }

    final items = <_PendingMedia>[];
    for (final file in files) {
      final contentType = _resolveContentType(file, kind);
      if (contentType == null || !_allowedTypes.contains(contentType)) {
        _toast('지원하지 않는 형식이에요. (JPG·PNG·WEBP·MP4·MOV)');
        continue;
      }

      // 영상은 압축한 뒤에 재야 한다 — 폰 기본 촬영은 6분이면 700MB 를 넘지만 압축하면 들어온다.
      final item =
          _PendingMedia(await _compressIfVideo(file, contentType), contentType);

      // 길이만 확인한다 — 상한 검사하려고 파일을 통째로 메모리에 올릴 이유가 없다.
      final limit =
          contentType.startsWith('video/') ? _videoMaxBytes : _imageMaxBytes;
      if (await item.file.length() > limit) {
        _toast(contentType.startsWith('video/')
            ? '영상이 너무 커요. 200MB 아래로 줄여야 올릴 수 있어요.'
            : '이미지는 최대 10MB까지예요.');
        continue;
      }
      items.add(item);
    }
    if (items.isEmpty || !mounted) return;

    // 새 글: 아직 글이 없어 매달 곳이 없다. 등록할 때 함께 올린다.
    if (!_isEdit) {
      if (!mounted) return;
      setState(() {
        _pending = [..._pending, ...items];
        _dirty = true;
      });
      return;
    }

    await _uploadQueue(bandId, items);
  }

  /// 이미 있는 글에 첨부를 차례로 올린다. 못 올린 것은 [_pending] 에 남겨 다시 올릴 수 있게 한다.
  Future<void> _uploadQueue(int bandId, List<_PendingMedia> items) async {
    setState(() {
      _busy = true;
      _uploadTotal = items.length;
      _pending = _pending.where((p) => !items.contains(p)).toList();
    });
    try {
      var uploaded = 0;
      final failed = <_PendingMedia>[];
      for (var i = 0; i < items.length; i++) {
        if (await _uploadOne(bandId, _postId!, items[i], index: i + 1)) {
          uploaded++;
        } else {
          failed.add(items[i]);
        }
        if (!mounted) return;
      }
      if (_pending.isEmpty && failed.isEmpty) {
        unawaited(_compressor.cleanupFiles(deleteCompressedVideos: true));
      }
      setState(() {
        _pending = [..._pending, ...failed];
        if (failed.isNotEmpty) {
          _dirty = true;
        } else if (widget.postId == null) {
          _dirty = false; // 글은 이미 저장됐고 남은 첨부도 없다
        }
      });
      if (uploaded > 0) {
        ref.invalidate(
          postDetailProvider((bandId: bandId, postId: _postId!)),
        );
        ref.read(boardFeedProvider(bandId).notifier).refresh();
      }
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          _uploadTotal = 0;
        });
      }
    }
  }

  /// 진행률이 이 시간 동안 한 번도 안 바뀌면 압축이 물린 것으로 보고 취소한다.
  /// 큰 영상은 오래 걸리지만 "느린 것"은 진행률이 계속 오른다 — 멈춘 것만 걸러낸다.
  static const _compressStall = Duration(seconds: 90);

  /// 영상이면 720p 로 압축해 돌려준다. 이미지·웹이거나 실패하면 원본 그대로.
  ///
  /// 폰 기본 촬영(1080p 17Mbps)은 6분이면 700MB 가 넘어 상한(200MB)에 들어가지 않는다.
  /// 720p 로 줄이면 6분이 대략 90MB 다. 합주 영상은 소리가 본체라 이 정도면 충분하다.
  ///
  /// 압축은 30초 넘게 걸릴 수 있어 진행률을 보여준다. 실패하면 원본으로 진행하고,
  /// 상한을 넘으면 호출한 쪽의 크기 검사에서 걸린다.
  ///
  /// **진행률이 멈추면 취소한다.** 예전 라이브러리(video_compress)의 트랜스코더는 특정 영상에서
  /// 교착에 빠졌다(실기기: 131MB 영상이 33% 에서 4분간 정지, U6). 2026-09-29 Media3 기반
  /// v_video_compressor 로 바꿨지만 감시는 남긴다 — 기기 인코더에 따라 멈추는 경우가 없다고 장담할 수 없고,
  /// 멈추면 원본으로 넘어가는 게 사용자에게 가장 낫다. 원본이 상한을 넘으면 호출한 쪽에서 걸린다.
  Future<XFile> _compressIfVideo(XFile file, String contentType) async {
    if (kIsWeb || !contentType.startsWith('video/')) return file;

    var lastPct = -1.0;
    var lastMoved = DateTime.now();
    var stalled = false;

    final watchdog = Timer.periodic(const Duration(seconds: 15), (_) {
      if (DateTime.now().difference(lastMoved) < _compressStall) return;
      stalled = true;
      unawaited(_compressor.cancelCompression());
    });

    setState(() => _compressPct = 0);
    try {
      // medium = 720p·1.8Mbps. 결과가 원본보다 크면(이미 작은 영상) 원본을 쓴다.
      final result = await _compressor.compressVideo(
        file.path,
        const VVideoCompressionConfig.medium(),
        onProgress: (progress) {
          final pct = progress * 100; // 라이브러리는 0~1, 화면은 0~100
          if (pct != lastPct) {
            lastPct = pct;
            lastMoved = DateTime.now();
          }
          if (mounted) setState(() => _compressPct = pct);
        },
      );
      if (stalled || result == null) return file;
      return XFile(result.compressedFilePath);
    } catch (_) {
      return file;
    } finally {
      watchdog.cancel();
      if (mounted) {
        setState(() => _compressPct = null);
        if (stalled) {
          _toast('영상을 줄이지 못해 원본 그대로 올려요. 너무 크면 등록이 안 될 수 있어요.');
        }
      }
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
      _toast('첨부를 지우지 못했어요.');
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
    required this.pendingFailed,
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

  /// 글이 이미 있는데 남은 대기 첨부 = 올리기에 실패한 것.
  final bool pendingFailed;
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
                failed: pendingFailed,
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
  const _PendingThumb({
    required this.item,
    required this.onRemove,
    this.failed = false,
  });

  final _PendingMedia item;
  final bool failed;
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
              child: Text(
                failed ? '올리지 못함' : '등록 시 업로드',
                style: const TextStyle(fontSize: 9.5, color: Colors.white),
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

/// 첨부 업로드 진행 표시. 영상은 수백 MB 라 몇 분씩 걸린다 — 아무 표시가 없으면
/// 사용자가 앱이 멈춘 줄 알고 뒤로 나가 버린다(글은 이미 저장돼 있어 첨부만 유실된다).
class _UploadProgress extends StatelessWidget {
  const _UploadProgress({
    required this.index,
    required this.total,
    required this.pct,
  });

  final int index;
  final int total;
  final int pct;

  @override
  Widget build(BuildContext context) {
    final label = total > 1 ? '첨부 올리는 중 $index/$total' : '첨부 올리는 중';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              label,
              style: const TextStyle(fontSize: 12, color: AppColors.textDim),
            ),
            Text(
              '$pct%',
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.primary,
                fontWeight: FontWeight.w700,
                fontFeatures: [FontFeature.tabularFigures()],
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        ClipRRect(
          borderRadius: BorderRadius.circular(4),
          child: LinearProgressIndicator(
            value: pct / 100,
            minHeight: 6,
            backgroundColor: AppColors.surfaceRaised,
            valueColor: const AlwaysStoppedAnimation(AppColors.primary),
          ),
        ),
        const SizedBox(height: 6),
        const Text(
          '영상은 몇 분 걸릴 수 있어요. 화면을 벗어나지 마세요.',
          style: TextStyle(fontSize: 11, color: AppColors.textFaint),
        ),
      ],
    );
  }
}
