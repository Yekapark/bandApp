import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// 안전 저장소에서 값을 읽되, **복호화가 깨진 값은 없는 것으로 친다.**
///
/// 기기 백업은 안전 저장소가 값을 담아 두는 SharedPreferences 는 복원하지만, 그 값을 푸는
/// 열쇠가 든 Android KeyStore 는 복원하지 않는다. 그래서 재설치 뒤에는 "열쇠 없는 금고" 가
/// 남고, 그 항목을 읽을 때마다 `BadPaddingException(BAD_DECRYPT)` 가 난다.
///
/// **되살릴 방법이 없다.** 그러니 지우고 없는 셈 친다 — 안 지우면 앱을 다시 깔기 전까지
/// 매번 같은 예외가 나고, 그 값을 읽는 화면이 통째로 죽는다(실제로 홈 화면이 그랬다,
/// docs/TROUBLESHOOTING.md 2026-09-07).
///
/// 쓰기는 감싸지 않는다 — 새로 쓰는 값은 현재 열쇠로 암호화되므로 이 문제를 겪지 않는다.
Future<String?> secureRead(FlutterSecureStorage storage, String key) async {
  try {
    return await storage.read(key: key);
  } catch (e) {
    debugPrint('secureRead: $key 를 읽지 못해 버린다 ($e)');
    try {
      await storage.delete(key: key);
    } catch (_) {
      // 지우기까지 실패해도 호출자에게는 "값 없음" 이면 충분하다.
    }
    return null;
  }
}
