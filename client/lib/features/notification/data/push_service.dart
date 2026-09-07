import 'dart:async' show unawaited;
import 'dart:io' show Platform;

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../application/notification_providers.dart';
import 'notification_repository.dart';

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

  /// 로그아웃 시. 등록했던 토큰을 해제하고 상태를 되돌린다.
  Future<void> stop() async {
    final token = _token;
    _active = false;
    _registered = false;
    _token = null;
    _lifecycle?.dispose();
    _lifecycle = null;
    if (token == null) return;
    try {
      await _repo.unregisterDeviceToken(token);
    } catch (_) {
      // 해제 실패는 무시 — 서버 배치가 무효 토큰을 정리한다.
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
      final settings = await messaging.requestPermission();
      if (settings.authorizationStatus == AuthorizationStatus.denied) return;

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
      }
    } catch (e) {
      debugPrint('PushService: 초기화 건너뜀 ($e)');
    } finally {
      _busy = false;
    }
  }

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
