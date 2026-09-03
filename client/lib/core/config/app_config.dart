import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';

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

  /// 네이버 지도 클라이언트 ID (NCP → Maps → Application). Android/iOS 전용.
  /// 빈 값이면 지도 화면이 마커 대신 목록만 보여준다.
  /// 배포 빌드: --dart-define=NAVER_MAP_CLIENT_ID=xxxx
  static const String naverMapClientId = String.fromEnvironment(
    'NAVER_MAP_CLIENT_ID',
  );

  static bool get naverMapEnabled => naverMapClientId.isNotEmpty;

  /// 스플래시 최소 노출 시간 (목업: 약 2초).
  static const Duration splashMinDuration = Duration(seconds: 2);
}
