import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:kakao_map_sdk/kakao_map_sdk.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';

import 'app.dart';
import 'core/config/app_config.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (AppConfig.kakaoEnabled) {
    KakaoSdk.init(
      nativeAppKey: AppConfig.kakaoNativeAppKey,
      javaScriptAppKey: AppConfig.kakaoJavaScriptAppKey,
    );
  }
  // 카카오맵은 Android/iOS 전용이고, 로그인과 같은 네이티브 앱 키를 쓴다.
  // 인증 실패(콘솔 키 해시 미등록 등)로 앱 전체가 죽지 않도록 여기서 삼키고,
  // 지도 화면·등록 폼은 AppConfig.mapEnabled 로 안내 문구 폴백한다.
  if (!kIsWeb && AppConfig.kakaoNativeAppKey.isNotEmpty) {
    try {
      await KakaoMapSdk.instance.initialize(AppConfig.kakaoNativeAppKey);
    } catch (e) {
      AppConfig.mapAuthFailed = true;
      debugPrint('kakao map sdk init failed: $e');
    }
  }
  runApp(const ProviderScope(child: BandApp()));
}
