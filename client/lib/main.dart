import 'dart:async' show unawaited;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:kakao_map_sdk/kakao_map_sdk.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';

import 'app.dart';
import 'core/config/app_config.dart';
import 'core/config/native_abi.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (AppConfig.kakaoEnabled) {
    KakaoSdk.init(
      nativeAppKey: AppConfig.kakaoNativeAppKey,
      javaScriptAppKey: AppConfig.kakaoJavaScriptAppKey,
    );
    // 카카오 로그인이 "keyHash validation failed" 로 막히는 일이 잦은데, 원인은 거의 항상
    // 콘솔에 등록한 키 해시가 이 빌드의 서명과 다른 것이다(디버그/릴리스 키스토어가 다르거나
    // 오타). SDK 가 실제로 서버에 보내는 값을 그대로 찍어 두면 콘솔에 붙여넣기만 하면 된다.
    if (kDebugMode) {
      unawaited(KakaoSdk.origin
          .then((v) => debugPrint('kakao keyHash (콘솔에 등록할 값): $v'))
          .catchError((Object e) {
        debugPrint('kakao keyHash 조회 실패: $e');
        return '';
      }));
    }
  }
  // 카카오맵은 Android/iOS 전용이고, 로그인과 같은 네이티브 앱 키를 쓴다.
  //
  // ABI 확인이 먼저다. 카카오맵 엔진은 ARM 빌드만 있어서 x86_64 에뮬레이터에서 초기화하면
  // 네이티브 쪽 UnsatisfiedLinkError 로 앱이 통째로 죽는다 — 아래 try/catch 로도 못 막는다
  // (Dart 로 올라오지 않는 치명적 오류다). 그래서 지원 ABI 가 아니면 호출 자체를 건너뛴다.
  //
  // try/catch 는 그 다음 방어선이다. 인증 실패(콘솔 키 해시 미등록 등)처럼 Dart 로 올라오는
  // 실패는 여기서 삼키고, 지도 화면·등록 폼은 AppConfig.mapEnabled 로 안내 문구 폴백한다.
  if (!kIsWeb && kakaoMapAbiSupported && AppConfig.kakaoNativeAppKey.isNotEmpty) {
    try {
      await KakaoMapSdk.instance.initialize(AppConfig.kakaoNativeAppKey);
    } catch (e) {
      AppConfig.mapAuthFailed = true;
      debugPrint('kakao map sdk init failed: $e');
    }
  }
  runApp(const ProviderScope(child: BandApp()));
}
