import 'dart:io' show Directory, File, Platform;

import 'package:flutter/foundation.dart' show kIsWeb, visibleForTesting;
import 'package:path_provider/path_provider.dart';
import 'package:v_video_compressor/v_video_compressor.dart';

/// 선택기(image_picker)는 고른 파일을 캐시/<UUID>/ 로 복사하고(사진은 캐시/scaled_* 축소본도) 지우지 않는다 —
/// 플러그인 코드에도 "앱이 직접 지우라"는 TODO 가 있다. 영상은 원본 크기 그대로라 고르고 취소할 때마다 수십 MB 씩
/// 쌓였고 앱을 다시 켜도 남았다(QA-R26/U60). 고른 파일은 글쓰기 화면만 쓰므로, 그 화면이 들고 있는 것 외의 복사본을
/// 지운다. 강제 종료로 남은 것은 앱을 다시 켤 때([purgeStaleMediaOnStartup])나 다음에 고를 때 지워진다.
String? _pickerCacheDir;

final _uuidName =
    RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');

String _name(String path) => path.split(Platform.pathSeparator).last;

/// 방금 고른 파일 경로에서 선택기가 쓰는 캐시 폴더를 알아 둔다.
void rememberPickerCacheDir(List<String> pickedPaths) {
  for (final path in pickedPaths) {
    final parent = File(path).parent;
    if (_uuidName.hasMatch(_name(parent.path))) {
      _pickerCacheDir = parent.parent.path;
    } else if (_name(path).startsWith('scaled_')) {
      _pickerCacheDir = parent.path;
    }
  }
}

@visibleForTesting
void setPickerCacheDirForTest(String? dir) => _pickerCacheDir = dir;

/// [keep] 에 든 파일(과 그 파일이 든 UUID 폴더)만 남기고 선택기 복사본을 지운다. UUID 이름 폴더와 scaled_ 파일 외에는
/// 손대지 않는다 — 같은 캐시 폴더를 다른 라이브러리도 쓴다.
Future<void> purgePickerCopies(Set<String> keep) async {
  final dir = _pickerCacheDir;
  if (kIsWeb || dir == null) return;
  try {
    await for (final e in Directory(dir).list()) {
      final name = _name(e.path);
      final inUse = keep.any(
        (k) =>
            k == e.path || k.startsWith('${e.path}${Platform.pathSeparator}'),
      );
      if (inUse) continue;
      if (e is Directory && _uuidName.hasMatch(name)) {
        await e.delete(recursive: true);
      } else if (e is File && name.startsWith('scaled_')) {
        await e.delete();
      }
    }
  } catch (_) {
    // 캐시 정리 실패는 사용자에게 알릴 일이 아니다 — 다음에 고를 때 다시 지운다.
  }
}

/// 앱 시작 때 한 번 — 강제 종료로 남은 선택기 복사본(캐시)과 영상 압축본(데이터)을 지운다. 예전에는 글쓰기 화면을
/// 다시 열어야 지워져서, 그 전까지 원본 크기 복사본·압축본이 남았다(U60 후속). 시작 시점엔 글쓰기 화면이 없고
/// 강제 종료된 업로드는 이어지지 않으므로 쓰는 파일이 없다. 선택기는 안드로이드 캐시 폴더(= 임시 폴더)에 복사한다.
Future<void> purgeStaleMediaOnStartup({
  Future<Directory> Function() tempDir = getTemporaryDirectory,
  Future<void> Function()? cleanupCompressed,
}) async {
  if (kIsWeb) return;
  try {
    _pickerCacheDir ??= (await tempDir()).path;
    await purgePickerCopies(const {});
    await (cleanupCompressed ??
        () => VVideoCompressor().cleanupFiles(deleteCompressedVideos: true))();
  } catch (_) {
    // 정리 실패로 앱 시작을 막지 않는다 — 글쓰기 화면에서 다시 지운다.
  }
}
