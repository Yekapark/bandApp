import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';

import 'native_abi.dart';

/// 앱 전역 설정. 빌드 시 `--dart-define` 으로 덮어쓸 수 있다.
///
/// 예) flutter run --dart-define=API_BASE_URL=http://192.168.0.10:8080
class AppConfig {
  const AppConfig._();

  /// 임시 워크네임. 정식 앱 이름 확정 시 교체 (`example/BandScreen.dc.html` 목업 기준).
  static const String appName = 'STAGE ON';

  /// 백엔드 베이스 URL. 엔드포인트는 이 뒤에 `/api/v1/...` 가 붙는다.
  ///
  /// 안드로이드 에뮬레이터는 호스트의 localhost 가 10.0.2.2 라서 기본값을 분기한다.
  static String get apiBaseUrl {
    const fromDefine = String.fromEnvironment('API_BASE_URL');
    if (fromDefine.isNotEmpty) return fromDefine;

    if (!kIsWeb && Platform.isAndroid) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://localhost:8080';
  }

  static const String apiPrefix = '/api/v1';

  /// 카카오 네이티브 앱 키 (Android/iOS). 카카오 개발자센터 → 내 앱 → 앱 키.
  /// 빈 값이면 카카오 버튼이 "설정 안 됨" 스낵바를 띄운다.
  /// 실행 시 --dart-define=KAKAO_NATIVE_APP_KEY=xxxx 로 주입한다(저장소에 키를 커밋하지 않는다).
  static const String kakaoNativeAppKey =
      String.fromEnvironment('KAKAO_NATIVE_APP_KEY');

  /// 카카오 JavaScript 키 (웹). 카카오 개발자센터 → 앱 키 → JavaScript 키.
  /// 실행 시 --dart-define=KAKAO_JS_APP_KEY=xxxx 로 주입한다.
  static const String kakaoJavaScriptAppKey =
      String.fromEnvironment('KAKAO_JS_APP_KEY');

  static bool get kakaoEnabled =>
      kakaoNativeAppKey.isNotEmpty || kakaoJavaScriptAppKey.isNotEmpty;

  /// 카카오맵(kakao_map_sdk)은 로그인과 **같은 네이티브 앱 키**를 쓴다 — 지도 전용 키가 따로 없다.
  /// Android/iOS 전용이라 웹에서는 지도 대신 목록만 보여준다.
  ///
  /// SDK 인증에 실패하면(콘솔에 키 해시 미등록 등) 이 플래그가 켜지고, 지도 자리에는 안내 문구만
  /// 남는다. 지도가 등록 폼처럼 자주 여는 화면에 들어가 있어서, 인증 실패로 화면이 깨지는 대신
  /// 조용히 폴백해야 한다.
  static bool mapAuthFailed = false;

  static bool get mapEnabled => !kIsWeb &&
      kakaoMapAbiSupported &&
      kakaoNativeAppKey.isNotEmpty &&
      !mapAuthFailed;

  /// 스플래시 최소 노출 시간 (목업: 약 2초).
  static const Duration splashMinDuration = Duration(seconds: 2);
}
