import 'package:firebase_app_distribution/firebase_app_distribution.dart'
    as distribution;
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';

import '../config/app_config.dart';

/// 테스터 빌드에서 "새 버전 있어요" 를 앱 안에서 띄운다.
///
/// 이게 없으면 테스터의 동선은 이렇다 — 메일 알림 → 메일 앱 열기 → 링크 → 브라우저 →
/// 설치. 메일이 묻히면 새 빌드가 있는지도 모른다. 여기서 물어보면 앱을 여는 것만으로
/// 알게 된다.
///
/// **스토어 빌드에서는 호출조차 되지 않는다** (`AppConfig.testerBuild`). 스토어에 올린
/// 앱이 스토어 밖에서 앱을 받아 까는 것은 정책 위반이다.
///
/// 실패는 전부 삼킨다. 업데이트 확인은 앱의 본래 일이 아니라서, 여기서 난 오류로
/// 앱이 뜨지 않거나 사용자에게 오류가 보이면 손해가 더 크다.
Future<void> promptIfNewTesterBuild() async {
  if (!AppConfig.testerBuild || kIsWeb) return;
  try {
    if (Firebase.apps.isEmpty) {
      await Firebase.initializeApp();
    }
    // 테스터 로그인·버전 비교·설치 안내까지 이 한 번에 들어 있다. 새 버전이 없으면
    // 아무것도 띄우지 않는다.
    await distribution.updateIfNewReleaseAvailable();
  } catch (e) {
    debugPrint('테스터 업데이트 확인 실패 (무시): $e');
  }
}
