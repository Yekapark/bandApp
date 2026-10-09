import 'dart:io';

import 'package:bandapp_client/features/board/application/picker_cache.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('picker copies are purged except kept ones; other cache files stay',
      () async {
    final cache = await Directory.systemTemp.createTemp('picker_cache_test');
    addTearDown(() => cache.delete(recursive: true));
    File p(String rel) => File([cache.path, ...rel.split('/')].join(Platform.pathSeparator))
      ..createSync(recursive: true);
    final old = p('11111111-2222-3333-4444-555555555555/old.mp4');
    final kept = p('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/now.mp4');
    final scaledOld = p('scaled_old.jpg');
    final scaledKept = p('scaled_now.jpg');
    final other = p('libCachedImageData/x.png');
    final notUuid = p('compressed/out.mp4');

    rememberPickerCacheDir([kept.path]);
    await purgePickerCopies({kept.path, scaledKept.path});

    expect(old.parent.existsSync(), isFalse);
    expect(scaledOld.existsSync(), isFalse);
    expect(kept.existsSync(), isTrue);
    expect(scaledKept.existsSync(), isTrue);
    expect(other.existsSync(), isTrue);
    expect(notUuid.existsSync(), isTrue);

    await purgePickerCopies(const {});
    expect(kept.existsSync(), isFalse);
    expect(other.existsSync(), isTrue);
    setPickerCacheDirForTest(null);
  });

  test('startup purges leftover picker copies and compressed videos', () async {
    final cache = await Directory.systemTemp.createTemp('picker_startup_test');
    addTearDown(() => cache.delete(recursive: true));
    final left = File('${cache.path}${Platform.pathSeparator}11111111-2222-3333-4444-555555555555${Platform.pathSeparator}v.mp4')
      ..createSync(recursive: true);
    final other = File('${cache.path}${Platform.pathSeparator}keep.bin')..createSync();
    var compressedCleaned = false;

    setPickerCacheDirForTest(null);
    await purgeStaleMediaOnStartup(
      tempDir: () async => cache,
      cleanupCompressed: () async => compressedCleaned = true,
    );

    expect(left.parent.existsSync(), isFalse);
    expect(other.existsSync(), isTrue);
    expect(compressedCleaned, isTrue);
    setPickerCacheDirForTest(null);
  });
}
