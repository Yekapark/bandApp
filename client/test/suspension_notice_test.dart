import 'package:bandapp_client/core/network/dio_client.dart';
import 'package:bandapp_client/core/storage/token_storage.dart';
import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/features/auth/data/auth_repository.dart';
import 'package:bandapp_client/features/notification/data/notification_repository.dart';
import 'package:bandapp_client/features/notification/data/push_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

/// LAUNCH_REVIEW P7 — 이용이 정지되면 토큰 갱신이 ACCOUNT_SUSPENDED 로 거절되고 앱이 로그아웃된다.
/// 이때 그냥 로그인 화면으로 튕기면 왜 나갔는지 모른다 — 서버 안내(기간·문의처)가 로그인 화면까지 가야 한다.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  ProviderContainer build() {
    final container = ProviderContainer(overrides: [
      tokenStorageProvider.overrideWithValue(_FakeStorage()),
      authRepositoryProvider.overrideWithValue(_FakeAuthRepo()),
      pushServiceProvider.overrideWith((ref) => _FakePush(ref)),
    ]);
    addTearDown(container.dispose);
    container.read(authControllerProvider); // 신호 구독 시작
    return container;
  }

  test('정지 안내와 함께 세션이 끊기면 로그아웃하고 안내를 남긴다', () {
    final container = build();

    container.read(sessionExpiredSignalProvider).fire(
        notice: '이용이 정지된 계정이에요 (2026년 10월 5일까지). 이의가 있으면 notice@bandule.com 로 알려 주세요.');

    expect(container.read(authControllerProvider).status,
        AuthStatus.unauthenticated);
    expect(container.read(authNoticeProvider), contains('정지'));
  });

  test('그냥 만료면 안내가 없다', () {
    final container = build();

    container.read(sessionExpiredSignalProvider).fire();

    expect(container.read(authControllerProvider).status,
        AuthStatus.unauthenticated);
    expect(container.read(authNoticeProvider), isNull);
  });

  test('안내는 한 번만 꺼낸다', () {
    final signal = SessionExpiredSignal();
    addTearDown(signal.dispose);
    signal.fire(notice: '정지');
    expect(signal.takeNotice(), '정지');
    expect(signal.takeNotice(), isNull);
  });
}

class _FakeStorage extends TokenStorage {
  _FakeStorage() : super(const FlutterSecureStorage());

  @override
  Tokens? get current =>
      const Tokens(accessToken: 'access-1', refreshToken: 'refresh-1');

  @override
  Future<void> clear() async {}
}

class _FakeAuthRepo extends AuthRepository {
  _FakeAuthRepo() : super(Dio());

  @override
  Future<void> logout({required String refreshToken, String? deviceToken}) async {}
}

class _FakePush extends PushService {
  _FakePush(Ref ref) : super(ref, NotificationRepository(Dio()));

  @override
  String? get currentToken => null;

  @override
  Future<void> stop({bool unregister = true}) async {}
}
