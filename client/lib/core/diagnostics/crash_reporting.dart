import 'dart:ui';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';
import 'package:flutter/foundation.dart';

/// 앱 오류 기록 — Firebase Crashlytics (LAUNCH_REVIEW U17).
///
/// Play Console 의 비정상 종료 기록은 "사용 및 진단 정보 공유" 를 켠 기기에서만, 하루 한 번 올라온다.
/// 테스터 폰에서 앱이 꺼졌다는 의견(U13)을 받고도 원인을 볼 길이 없어서 넣었다. 네이티브 비정상 종료는 SDK 가
/// 알아서 잡고, Dart 쪽 처리 안 된 오류는 아래 핸들러가 넘긴다(비정상 종료가 아닌 "오류" 로 — 앱은 계속 돈다).
///
/// - **릴리스 빌드에서만 보낸다.** 디버그·프로필 빌드는 수집을 끈다(개발 중 오류가 대시보드를 덮지 않게).
/// - **계정 정보는 붙이지 않는다** — 사용자 ID·이메일을 setUserIdentifier 로 넣지 않는다. 개인정보처리방침 1-3.
/// - 사용자가 설정 › 앱 오류 기록 보내기 에서 끌 수 있다. 그 값은 Crashlytics 가 기기에 저장해 다음 실행에도 유지된다
///   — 그래서 릴리스에서는 시작할 때 켜기를 다시 부르지 않는다(끈 사람을 되켜지 않게).
/// - google-services.json 이 없는 빌드(CI·키 없는 PC)는 Firebase 초기화가 실패하고 아무것도 하지 않는다 — 푸시와 같은 방식.
class CrashReporting {
  CrashReporting._();

  /// 이 빌드에서 오류 기록을 쓸 수 있는가(릴리스 + Firebase 설정 있음). 설정 화면이 스위치를 보일지 정한다.
  static bool available = false;

  static Future<void> init() async {
    if (kIsWeb) return;
    try {
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp();
      }
    } catch (e) {
      debugPrint('CrashReporting: Firebase 미설정 — 오류 기록 끔 ($e)');
      return;
    }
    final crashlytics = FirebaseCrashlytics.instance;
    if (!kReleaseMode) {
      await crashlytics.setCrashlyticsCollectionEnabled(false);
      return;
    }
    available = true;
    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      crashlytics.recordFlutterError(details);
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      crashlytics.recordError(error, stack);
      return true;
    };
  }

  static bool get enabled =>
      available && FirebaseCrashlytics.instance.isCrashlyticsCollectionEnabled;

  static Future<void> setEnabled(bool value) =>
      FirebaseCrashlytics.instance.setCrashlyticsCollectionEnabled(value);
}
