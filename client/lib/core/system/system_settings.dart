import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// 휴대폰 설정 화면 열기 — 안드로이드 쪽은 `MainActivity.kt` 의 `bandule/system` 채널.
/// 새 플러그인을 들이지 않으려고 직접 만든 작은 채널이다.
class SystemSettings {
  SystemSettings._();

  static const _channel = MethodChannel('bandule/system');

  /// 이 앱의 알림 설정 화면. 열지 못하면 false.
  static Future<bool> openNotificationSettings() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return false;
    try {
      return await _channel.invokeMethod<bool>('openNotificationSettings') ?? false;
    } catch (e) {
      debugPrint('SystemSettings: 알림 설정을 열지 못함 ($e)');
      return false;
    }
  }
}
