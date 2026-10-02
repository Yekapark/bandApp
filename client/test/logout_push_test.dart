import 'package:bandapp_client/core/storage/token_storage.dart';
import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/features/auth/data/auth_repository.dart';
import 'package:bandapp_client/features/notification/data/notification_repository.dart';
import 'package:bandapp_client/features/notification/data/push_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

/// LAUNCH_REVIEW U1 — 로그아웃하면 이 기기로 가던 푸시가 끊겨야 한다. 예전에는 토큰을 먼저 지운 뒤 인증이 필요한
/// 해제 요청을 보내 401 이 났고, 서버에 기기 토큰이 남아 로그아웃한 폰에 이전 계정 알림이 계속 갔다.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('로그아웃 요청에 기기 토큰을 싣고, 토큰 저장소를 비우기 전에 푸시를 멈춘다', () async {
    final calls = <String>[];
    final storage = _FakeStorage(calls);
    final repo = _FakeAuthRepo(calls);
    final container = ProviderContainer(overrides: [
      tokenStorageProvider.overrideWithValue(storage),
      authRepositoryProvider.overrideWithValue(repo),
      pushServiceProvider.overrideWith((ref) => _FakePush(ref, calls)),
    ]);
    addTearDown(container.dispose);

    await container.read(authControllerProvider.notifier).logout();

    expect(repo.lastDeviceToken, 'fcm-device-1');
    expect(repo.lastRefresh, 'refresh-1');
    expect(calls, ['logout', 'push.stop(unregister: false)', 'storage.clear']);
    expect(container.read(authControllerProvider).status,
        AuthStatus.unauthenticated);
  });

  test('서버 로그아웃이 실패해도(오프라인) 이 기기에서는 로그아웃된다', () async {
    final calls = <String>[];
    final container = ProviderContainer(overrides: [
      tokenStorageProvider.overrideWithValue(_FakeStorage(calls)),
      authRepositoryProvider.overrideWithValue(_FakeAuthRepo(calls)..fail = true),
      pushServiceProvider.overrideWith((ref) => _FakePush(ref, calls)),
    ]);
    addTearDown(container.dispose);

    await container.read(authControllerProvider.notifier).logout();

    expect(calls, contains('storage.clear'));
    expect(container.read(authControllerProvider).status,
        AuthStatus.unauthenticated);
  });
}

class _FakeStorage extends TokenStorage {
  _FakeStorage(this.calls) : super(const FlutterSecureStorage());

  final List<String> calls;

  @override
  Tokens? get current =>
      const Tokens(accessToken: 'access-1', refreshToken: 'refresh-1');

  @override
  Future<void> clear() async => calls.add('storage.clear');
}

class _FakeAuthRepo extends AuthRepository {
  _FakeAuthRepo(this.calls) : super(Dio());

  final List<String> calls;
  String? lastRefresh;
  String? lastDeviceToken;
  bool fail = false;

  @override
  Future<void> logout({required String refreshToken, String? deviceToken}) async {
    calls.add('logout');
    if (fail) throw StateError('offline');
    lastRefresh = refreshToken;
    lastDeviceToken = deviceToken;
  }
}

class _FakePush extends PushService {
  _FakePush(Ref ref, this.calls) : super(ref, NotificationRepository(Dio()));

  final List<String> calls;

  @override
  String? get currentToken => 'fcm-device-1';

  @override
  Future<void> stop({bool unregister = true}) async =>
      calls.add('push.stop(unregister: $unregister)');
}
