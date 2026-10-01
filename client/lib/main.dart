import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:kakao_map_sdk/kakao_map_sdk.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';

import 'app.dart';
import 'core/config/app_config.dart';
import 'core/config/native_abi.dart';
import 'core/diagnostics/crash_reporting.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // 오류 기록이 가장 먼저 — 아래 SDK 초기화에서 앱이 꺼져도 남게(U17).
  await CrashReporting.init();
  if (AppConfig.kakaoEnabled) {
    // SDK 2.x 부터 비동기다 — 끝나기 전에 로그인 버튼이 눌리지 않게 기다린다.
    await KakaoSdk.init(
      nativeAppKey: AppConfig.kakaoNativeAppKey,
      javaScriptAppKey: AppConfig.kakaoJavaScriptAppKey,
    );
    // 예전에는 디버그 빌드에서 KakaoSdk.origin 으로 키 해시를 찍었는데 SDK 2.x 에서 없어졌다.
    // "keyHash validation failed" 가 나면 콘솔에 등록한 키 해시가 이 빌드의 서명과 다른 것이다 —
    // 키 해시 구하는 법은 docs/TROUBLESHOOTING.md 의 카카오 키 해시 항목(keytool / Play 앱 서명 SHA-1 변환).
  }
  // 카카오맵은 Android/iOS 전용이고, 로그인과 같은 네이티브 앱 키를 쓴다.
  //
  // ABI 확인이 먼저다. 카카오맵 엔진은 ARM 빌드만 있어서 x86_64 에뮬레이터에서 초기화하면
  // 네이티브 쪽 UnsatisfiedLinkError 로 앱이 통째로 죽는다 — 아래 try/catch 로도 못 막는다
  // (Dart 로 올라오지 않는 치명적 오류다). 그래서 지원 ABI 가 아니면 호출 자체를 건너뛴다.
  //
  // try/catch 는 그 다음 방어선이다. 인증 실패(콘솔 키 해시 미등록 등)처럼 Dart 로 올라오는
  // 실패는 여기서 삼키고, 지도 화면·등록 폼은 AppConfig.mapEnabled 로 안내 문구 폴백한다.
  if (!kIsWeb &&
      kakaoMapAbiSupported &&
      AppConfig.kakaoNativeAppKey.isNotEmpty) {
    try {
      await KakaoMapSdk.instance.initialize(AppConfig.kakaoNativeAppKey);
    } catch (e) {
      AppConfig.mapAuthFailed = true;
      debugPrint('kakao map sdk init failed: $e');
    }
  }
  runApp(const ProviderScope(child: BandApp()));
}
