import 'dart:io' show Directory, File, Platform;

import 'package:flutter/foundation.dart' show kIsWeb, visibleForTesting;

/// 선택기(image_picker)는 고른 파일을 캐시/<UUID>/ 로 복사하고(사진은 캐시/scaled_* 축소본도) 지우지 않는다 —
/// 플러그인 코드에도 "앱이 직접 지우라"는 TODO 가 있다. 영상은 원본 크기 그대로라 고르고 취소할 때마다 수십 MB 씩
/// 쌓였고 앱을 다시 켜도 남았다(QA-R26/U60). 고른 파일은 글쓰기 화면만 쓰므로, 그 화면이 들고 있는 것 외의 복사본을
/// 지운다. 강제 종료로 남은 것은 다음에 고를 때 지워진다.
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
