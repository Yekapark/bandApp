import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart';
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
  // 네이버 지도는 Android/iOS 전용. 웹에서는 지도 화면이 목록만 보여준다.
  if (!kIsWeb && AppConfig.naverMapEnabled) {
    await FlutterNaverMap().init(clientId: AppConfig.naverMapClientId);
  }
  runApp(const ProviderScope(child: BandApp()));
}
