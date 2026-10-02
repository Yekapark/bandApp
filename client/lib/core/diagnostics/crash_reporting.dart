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
    // 릴리스의 debugPrint 도 기기 로그(logcat)로 나간다 — 오류 문자열 속 URL 서명·토큰을 가린다(PRIV-06).
    if (kReleaseMode) {
      debugPrint = (message, {wrapWidth}) => debugPrintThrottled(
          message == null ? null : scrubSensitive(message),
            wrapWidth: wrapWidth,
          );
    }
    if (kIsWeb) return;
    // main() 이 이것을 await 한 뒤 runApp 한다 — 여기서 던지면 앱이 첫 화면도 못 띄운다. Crashlytics 쪽 어떤
    // 실패든(초기화·네이티브 채널) 오류 기록만 끄고 넘어간다(QA-F05).
    final FirebaseCrashlytics crashlytics;
    try {
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp();
      }
      crashlytics = FirebaseCrashlytics.instance;
      if (!kReleaseMode) {
        await crashlytics.setCrashlyticsCollectionEnabled(false);
        return;
      }
    } catch (e) {
      debugPrint('CrashReporting: Firebase 미설정 — 오류 기록 끔 ($e)');
      return;
    }
    available = true;
    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      crashlytics.recordFlutterError(scrubDetails(details));
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      crashlytics.recordError(ScrubbedError(error), stack);
      return true;
    };
  }

  static bool get enabled =>
      available && FirebaseCrashlytics.instance.isCrashlyticsCollectionEnabled;

  /// 끌 때는 기기에 쌓여 아직 안 보낸 기록도 지운다. 수집을 꺼도 SDK 는 비정상 종료를 기기에 적어 두고,
  /// 나중에 다시 켜면 그것까지 보낸다 — 끈 동안의 기록이 나가면 안 된다.
  static Future<void> setEnabled(bool value) async {
    final crashlytics = FirebaseCrashlytics.instance;
    if (value) await crashlytics.deleteUnsentReports(); // 꺼져 있던 동안 쌓인 것
    await crashlytics.setCrashlyticsCollectionEnabled(value);
    if (!value) await crashlytics.deleteUnsentReports();
  }
}

/// Crashlytics 로 나가는 오류 — 원래 오류 문자열에서 민감값을 가린 것(PRIV-09). 스택은 호출부가 그대로 넘긴다.
/// Dio·이미지 로드 오류 문자열에는 요청 URL(R2 presigned 서명 포함)이 들어 있다.
class ScrubbedError {
  ScrubbedError(this.original);
  final Object original;
  @override
  String toString() => scrubSensitive(original.toString());
}

/// FlutterError 용 — 예외 문자열과 부가 정보(NetworkImage URL 등)를 가린다.
FlutterErrorDetails scrubDetails(FlutterErrorDetails details) {
  final collector = details.informationCollector;
  return details.copyWith(
    exception: ScrubbedError(details.exception),
    informationCollector: collector == null
        ? null
        : () => [
              for (final node in collector())
                DiagnosticsNode.message(scrubSensitive(node.toString())),
            ],
  );
}

final _rules = <(RegExp, String Function(Match))>[
  // URL 쿼리스트링 통째로(X-Amz-Signature·X-Amz-Credential 등).
  (RegExp(r'''(https?://[^\s?#"']+)\?[^\s"')]*'''), (m) => '${m[1]}?<redacted>'),
  (RegExp(r'''Bearer\s+[^\s"',]+''', caseSensitive: false), (_) => 'Bearer <redacted>'),
  (RegExp(r'eyJ[\w-]+\.[\w-]+\.[\w-]*'), (_) => '<jwt>'),
  (RegExp(r'[\w.%+-]+@[\w-]+(\.[\w-]+)*\.[A-Za-z]{2,}'), (_) => '<email>'),
  // "purchaseToken":"…", password=… 처럼 이름이 붙은 값.
  (
    RegExp(r'''((?:token|password|code|secret)["']?\s*[:=]\s*["']?)[^\s,"'&}]+''',
      caseSensitive: false,
    ),
    (m) => '${m[1]}<redacted>',
  ),
  // 이름 없이 떠도는 긴 불투명 문자열(구매 토큰 등).
  (RegExp(r'[\w.-]{40,}'), (_) => '<redacted>'),
];

/// 오류·로그 문자열에서 URL 쿼리, Bearer/JWT 토큰, 이메일, 구매 토큰 같은 값을 가린다.
String scrubSensitive(String text) {
  var out = text;
  for (final (pattern, replace) in _rules) {
    out = out.replaceAllMapped(pattern, replace);
  }
  return out;
}
