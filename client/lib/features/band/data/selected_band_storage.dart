import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../../core/storage/secure_read.dart';

final selectedBandStorageProvider = Provider<SelectedBandStorage>((ref) {
  return SelectedBandStorage(const FlutterSecureStorage());
});

/// 마지막으로 고른 밴드를 기기에 기억한다(QA-F01 — 앱을 강제 종료하고 다시 켜면 첫 밴드로 돌아갔다).
///
/// **누가 고른 것인지 함께 적는다**(`사용자id:밴드id`). 다른 계정으로 로그인하면 남의 선택을 물려받지 않는다.
/// 로그아웃하면 지운다(`SelectedBandId`).
class SelectedBandStorage {
  SelectedBandStorage(this._storage);

  final FlutterSecureStorage _storage;

  static const _key = 'band.selected';

  /// [userId] 가 저장한 밴드 id. 없거나 다른 사람 것이면 null.
  Future<int?> load(int userId) async {
    final raw = await secureRead(_storage, _key);
    final parts = raw?.split(':');
    if (parts == null || parts.length != 2) return null;
    if (int.tryParse(parts[0]) != userId) return null;
    return int.tryParse(parts[1]);
  }

  Future<void> save(int userId, int bandId) =>
      _storage.write(key: _key, value: '$userId:$bandId');

  Future<void> clear() => _storage.delete(key: _key);
}
