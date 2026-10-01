import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../../core/storage/secure_read.dart';

final pushPrimerStorageProvider = Provider<PushPrimerStorage>((ref) {
  return PushPrimerStorage(const FlutterSecureStorage());
});

/// 알림 안내 시트를 이 기기에서 보여 줬는지(U21). 계정이 아니라 기기 기준 — 권한도 기기에 붙어 있다.
/// 로그아웃해도 지우지 않는다. 앱을 지웠다 깔면 다시 묻는다(권한도 그때 초기화된다).
class PushPrimerStorage {
  PushPrimerStorage(this._storage);

  final FlutterSecureStorage _storage;

  static const _key = 'push.primerShown';

  Future<bool> shown() async => await secureRead(_storage, _key) != null;

  Future<void> markShown() =>
      _storage.write(key: _key, value: DateTime.now().toIso8601String());
}
