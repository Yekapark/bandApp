import 'dart:async' show Completer, Timer, unawaited;
import 'dart:io' show Platform;

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/system/system_settings.dart';
import '../../../routing/app_router.dart';
import '../../band/application/band_providers.dart';
import '../application/notification_providers.dart';
import '../application/notification_route.dart';
import '../application/push_permission_flow.dart';
import '../presentation/push_primer_sheet.dart';
import 'notification_repository.dart';
import 'push_primer_storage.dart';

/// 앱 전역 SnackBar 를 띄우기 위한 키 (app.dart 의 MaterialApp 에 연결).
final scaffoldMessengerKey = GlobalKey<ScaffoldMessengerState>();

final pushServiceProvider = Provider<PushService>((ref) {
  return PushService(ref, ref.watch(notificationRepositoryProvider));
});

/// FCM 디바이스 토큰 등록·수신 담당.
///
/// **설정 파일(`google-services.json` / `GoogleService-Info.plist` / 웹 `firebase_options`)이
/// 없으면 초기화가 실패하고 조용히 비활성화된다** — 카카오 SDK·네이버 지도와 같은 방식.
/// 그 상태에서도 앱의 나머지 기능은 정상 동작한다.
class PushService {
  PushService(this._ref, this._repo);

  final Ref _ref;
  final NotificationRepository _repo;

  bool _active = false; // 로그인 상태 — stop() 전까지 true
  bool _registered = false; // 서버에 토큰 등록 성공
  bool _listening = false; // FCM 스트림 구독 완료 (재시도해도 두 번 구독하지 않는다)
  bool _busy = false; // 재진입 방지
  bool _available = false;
  String? _token;
  AppLifecycleListener? _lifecycle;

  /// 로그인 직후. 등록에 실패해도 상태를 잠그지 않는다 — 포그라운드 복귀 때마다 다시 시도한다.
  ///
  /// 실패는 흔하고 대개 복구 가능하다: 알림 권한을 거부했다가 OS 설정에서 켠 경우, 서버가
  /// 잠깐 안 뜬 경우, 웹 VAPID 키가 없는 경우. 예전처럼 첫 시도에서 플래그를 세워 버리면
  /// 그 뒤로 영영 재시도하지 않아 device_tokens 에 행이 안 생기고 푸시가 아예 안 온다.
  Future<void> start() async {
    _active = true;
    // 등록 실패 시의 재시도 창구이기도 하므로 초기화 성공 여부와 무관하게 먼저 건다.
    _lifecycle ??= AppLifecycleListener(onResume: _onResume);
    await _tryRegister();
  }

  /// 이 기기의 푸시 토큰(등록을 시도한 적이 없으면 null). 로그아웃 요청에 실어 서버가 지우게 한다.
  String? get currentToken => _token;

  /// 로그아웃 시. 등록했던 토큰을 해제하고 상태를 되돌린다.
  ///
  /// [unregister] 가 false 면 서버 해제 요청을 건너뛴다 — 로그아웃 요청(`/auth/logout` 의 `deviceToken`)으로
  /// 이미 지웠을 때. 해제 요청은 인증이 필요한데, 로그아웃 뒤에 부르면 401 이 나서 서버에 토큰이 남았다(U1).
  ///
  /// 마지막에 **기기의 FCM 토큰 자체를 폐기**한다. 서버 요청이 네트워크 문제로 실패해도 이 기기로는 이전 계정의
  /// 알림이 더 오지 않는다(서버에 남은 옛 토큰은 발송 때 무효로 판정돼 정리된다). 다음 로그인에서 새 토큰을 받는다.
  Future<void> stop({bool unregister = true}) async {
    final token = _token;
    _active = false;
    _registered = false;
    _token = null;
    _lifecycle?.dispose();
    _lifecycle = null;
    if (token == null) return;
    if (unregister) {
      try {
        await _repo.unregisterDeviceToken(token);
      } catch (_) {
        // 해제 실패는 무시 — 아래에서 기기 토큰을 폐기하고, 서버 배치가 무효 토큰을 정리한다.
      }
    }
    try {
      if (_available) await FirebaseMessaging.instance.deleteToken();
    } catch (e) {
      debugPrint('PushService: 기기 토큰 폐기 실패 ($e)');
    }
  }

  void _onResume() {
    // 앱이 백그라운드에 있는 동안 온 푸시는 onMessage 가 뜨지 않는다. 돌아왔을 때 알림
    // 목록을 다시 읽지 않으면 홈의 종 배지가 옛 값 그대로 남는다(프로바이더가
    // autoDispose 가 아니라 캐시가 살아 있다 — docs/progress/NEXT.md §4).
    _refreshNotifications();

    // OS 설정에서 알림을 켜고 돌아왔을 수 있다. 아직 등록 못 했으면 여기서 다시 시도한다.
    if (_active && !_registered) unawaited(_tryRegister());
  }

  Future<void> _tryRegister() async {
    if (_registered || _busy) return;
    _busy = true;
    try {
      if (!await _ensureFirebase()) return;

      final messaging = FirebaseMessaging.instance;
      // 권한 창은 앱 안내 시트를 거쳐서만 띄운다(U21). 이미 허용됐으면 바로 지나간다. 안내에서 [나중에] 를
      // 골랐으면 여기서 멈추고, 사용자가 휴대폰 설정이나 "알림 켜기" 카드로 켜고 돌아오면 _onResume 이 다시 온다.
      final flow = PushPermissionFlow(
        isGranted: () async =>
            _granted((await messaging.getNotificationSettings()).authorizationStatus),
        alreadyAsked: _ref.read(pushPrimerStorageProvider).shown,
        markAsked: _ref.read(pushPrimerStorageProvider).markShown,
        showPrimer: _showPrimerWhenSettled,
        request: () async =>
            _granted((await messaging.requestPermission()).authorizationStatus),
        openSettings: () async {
          await SystemSettings.openNotificationSettings();
        },
      );
      if (!await flow.run()) return;

      // 웹은 VAPID 키가 없으면 getToken 이 던진다 → catch 되어 no-op.
      final token = await messaging.getToken();
      if (token == null) return;
      _token = token;
      _registered = await _register(token);

      if (!_listening) {
        _listening = true;
        messaging.onTokenRefresh.listen((t) {
          if (!_active) return; // 로그아웃 뒤 갱신은 남의 계정에 토큰을 붙일 뿐이다
          _token = t;
          _register(t).then((ok) => _registered = ok);
        });
        FirebaseMessaging.onMessage.listen(_onForegroundMessage);
        // 알림을 눌러 앱이 열렸을 때 그 화면으로 보낸다(U6). 백그라운드에서 누른 것은 스트림으로,
        // 앱이 꺼져 있다가 알림으로 켜진 것은 getInitialMessage 로 한 번 온다.
        FirebaseMessaging.onMessageOpenedApp.listen(_openFromPush);
        final initial = await messaging.getInitialMessage();
        if (initial != null) _openFromPush(initial);
      }
    } catch (e) {
      debugPrint('PushService: 초기화 건너뜀 ($e)');
    } finally {
      _busy = false;
    }
  }

  static bool _granted(AuthorizationStatus s) =>
      s == AuthorizationStatus.authorized || s == AuthorizationStatus.provisional;

  /// 로그인한 뒤 첫 화면이 자리를 잡으면 안내 시트를 띄운다. 로그인 직후에는 라우터가 로그인 → 홈 → (밴드가 없으면)
  /// 밴드 고르기로 연달아 화면을 바꾸는데, 그 사이에 띄우면 시트가 화면과 함께 사라진다. 그래서 로그인 전 화면이 아니고
  /// 주소가 [_settle] 동안 바뀌지 않을 때까지 기다린다. [_settleTimeout] 안에 자리를 못 잡으면 null — 다음 기회에.
  Future<bool?> _showPrimerWhenSettled() async {
    final router = _ref.read(routerProvider);
    final delegate = router.routerDelegate;
    final settled = Completer<bool>();
    Timer? quiet;

    void arm() {
      quiet?.cancel();
      if (_beforeSignIn.contains(delegate.currentConfiguration.uri.path)) return;
      quiet = Timer(_settle, () {
        if (!settled.isCompleted) settled.complete(true);
      });
    }

    delegate.addListener(arm);
    arm();
    final ok = await settled.future
        .timeout(_settleTimeout, onTimeout: () => false)
        .whenComplete(() {
      quiet?.cancel();
      delegate.removeListener(arm);
    });
    if (!ok || !_active) return null;
    final context = delegate.navigatorKey.currentContext;
    if (context == null || !context.mounted) return null;
    return showPushPrimerSheet(context);
  }

  static const _settle = Duration(milliseconds: 900);
  static const _settleTimeout = Duration(seconds: 30);
  static const _beforeSignIn = {
    Routes.splash,
    Routes.login,
    Routes.signup,
    Routes.terms,
    Routes.passwordReset,
  };

  Future<bool> _ensureFirebase() async {
    if (_available) return true;
    try {
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp();
      }
      _available = true;
      return true;
    } catch (e) {
      debugPrint('PushService: Firebase 미설정 — 푸시 비활성화 ($e)');
      return false;
    }
  }

  /// 등록 성공 여부. false 면 다음 포그라운드 복귀 때 다시 시도된다.
  Future<bool> _register(String token) async {
    try {
      await _repo.registerDeviceToken(token: token, platform: _platform());
      return true;
    } catch (e) {
      debugPrint('PushService: 토큰 등록 실패 ($e)');
      return false;
    }
  }

  /// 알림 목록 캐시를 비운다. 배지(안 읽은 수)가 이 목록에서 계산되므로 함께 갱신된다.
  /// 어느 밴드인지 몰라도 되게 family 전체를 무효화한다.
  void _refreshNotifications() {
    _ref.invalidate(notificationFeedProvider);
  }

  /// 푸시를 눌렀을 때 — 그 알림의 밴드로 바꾸고 해당 화면을 연다(U6). 서버가 싣는 data:
  /// `type`·`bandId`·(일정 알림이면) `reservationId` (`NotificationMessages`).
  void _openFromPush(RemoteMessage message) {
    if (!_active) return;
    final data = message.data;
    final bandId = int.tryParse('${data['bandId'] ?? ''}');
    final route = notificationRoute(
      data['type']?.toString(),
      int.tryParse('${data['reservationId'] ?? ''}'),
    );
    // 밴드를 여러 개 가진 사람은 지금 보고 있는 밴드가 알림의 밴드가 아닐 수 있다. 화면들이 "현재 밴드" 로
    // 데이터를 읽으므로 먼저 바꾼다(멤버가 아닌 밴드면 목록의 첫 밴드로 돌아간다).
    if (bandId != null) _ref.read(selectedBandIdProvider.notifier).select(bandId);
    if (route == null) return;
    // 콜드 스타트면 라우터가 아직 스플래시·홈으로 가는 중이다 — 한 프레임 뒤에 얹는다.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _ref.read(routerProvider).push(route);
    });
  }

  void _onForegroundMessage(RemoteMessage message) {
    // 앱을 보고 있는 중에 온 푸시. 스낵바만 띄우고 끝내면 종 배지가 안 오른다.
    _refreshNotifications();

    final n = message.notification;
    final text = n?.title ?? n?.body ?? message.data['title']?.toString();
    if (text == null || text.isEmpty) return;
    scaffoldMessengerKey.currentState
      ?..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  String _platform() {
    if (kIsWeb) return 'WEB';
    if (Platform.isIOS) return 'IOS';
    return 'ANDROID';
  }
}
