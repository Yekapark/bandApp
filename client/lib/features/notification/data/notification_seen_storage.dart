import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final notificationSeenStorageProvider = Provider<NotificationSeenStorage>((ref) {
  return NotificationSeenStorage(const FlutterSecureStorage());
});

/// 알림 목록을 마지막으로 연 시각을 기기에 저장한다.
///
/// 읽음 여부를 서버에 두지 않기로 한 결과다 — 배지는 "이 시각 이후에 온 알림 수"로 센다.
/// 기기를 바꾸면 배지가 초기화되지만, 밴드 앱에 서버 읽음 상태까지 둘 만한 이득이 없다.
/// 밴드별로 따로 기억한다.
class NotificationSeenStorage {
  NotificationSeenStorage(this._storage);

  final FlutterSecureStorage _storage;

  static String _key(int bandId) => 'notifications.lastSeenAt.$bandId';

  Future<DateTime?> lastSeen(int bandId) async {
    final raw = await _storage.read(key: _key(bandId));
    if (raw == null) return null;
    return DateTime.tryParse(raw);
  }

  Future<void> markSeen(int bandId, DateTime at) =>
      _storage.write(key: _key(bandId), value: at.toIso8601String());
}
