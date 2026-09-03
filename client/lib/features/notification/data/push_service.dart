import 'dart:io' show Platform;

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'notification_repository.dart';

/// 앱 전역 SnackBar 를 띄우기 위한 키 (app.dart 의 MaterialApp 에 연결).
final scaffoldMessengerKey = GlobalKey<ScaffoldMessengerState>();

final pushServiceProvider = Provider<PushService>((ref) {
  return PushService(ref.watch(notificationRepositoryProvider));
});

/// FCM 디바이스 토큰 등록·수신 담당.
///
/// **설정 파일(`google-services.json` / `GoogleService-Info.plist` / 웹 `firebase_options`)이
/// 없으면 초기화가 실패하고 조용히 비활성화된다** — 카카오 SDK·네이버 지도와 같은 방식.
/// 그 상태에서도 앱의 나머지 기능은 정상 동작한다.
class PushService {
  PushService(this._repo);

  final NotificationRepository _repo;

  bool _started = false;
  bool _available = false;
  String? _token;

  /// 로그인 직후 1회. 이미 시작했으면 무시.
  Future<void> start() async {
    if (_started) return;
    _started = true;

    if (!await _ensureFirebase()) return;

    try {
      final messaging = FirebaseMessaging.instance;
      final settings = await messaging.requestPermission();
      if (settings.authorizationStatus == AuthorizationStatus.denied) return;

      // 웹은 VAPID 키가 없으면 getToken 이 던진다 → catch 되어 no-op.
      final token = await messaging.getToken();
      if (token != null) {
        _token = token;
        await _register(token);
      }
      messaging.onTokenRefresh.listen((t) {
        _token = t;
        _register(t);
      });

      FirebaseMessaging.onMessage.listen(_onForegroundMessage);
    } catch (e) {
      debugPrint('PushService: 초기화 건너뜀 ($e)');
    }
  }

  /// 로그아웃 시. 등록했던 토큰을 해제하고 상태를 되돌린다.
  Future<void> stop() async {
    final token = _token;
    _started = false;
    _token = null;
    if (!_available || token == null) return;
    try {
      await _repo.unregisterDeviceToken(token);
    } catch (_) {
      // 해제 실패는 무시 — 서버 배치가 무효 토큰을 정리한다.
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

  Future<void> _register(String token) async {
    try {
      await _repo.registerDeviceToken(token: token, platform: _platform());
    } catch (e) {
      debugPrint('PushService: 토큰 등록 실패 ($e)');
    }
  }

  void _onForegroundMessage(RemoteMessage message) {
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
