import 'dart:async';

import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:bandapp_client/features/auth/data/auth_models.dart';
import 'package:bandapp_client/features/band/application/band_providers.dart';
import 'package:bandapp_client/features/band/data/band_models.dart';
import 'package:bandapp_client/features/band/data/selected_band_storage.dart';
import 'package:bandapp_client/features/notification/application/notification_providers.dart';
import 'package:bandapp_client/features/notification/data/notification_models.dart';
import 'package:bandapp_client/features/notification/data/notification_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

/// QA-F01 — 밴드를 골라 두고 앱을 강제 종료했다가 다시 켜면 첫 밴드로 돌아갔다(S24, +32).
/// "앱을 다시 켠다" = 같은 기기 저장소로 새 ProviderContainer 를 만든다.
void main() {
  final bands = [_band(1, '첫 밴드'), _band(2, '둘째 밴드'), _band(3, '셋째 밴드')];

  late _MemoryStorage disk;
  setUp(() => disk = _MemoryStorage());

  ProviderContainer launch(AuthState auth, {List<MyBand>? myBands}) {
    final c = ProviderContainer(overrides: [
      selectedBandStorageProvider
          .overrideWithValue(SelectedBandStorage(disk)),
      authControllerProvider.overrideWith(() => _FakeAuth(auth)),
      myBandsProvider.overrideWith((ref) async => myBands ?? bands),
    ]);
    addTearDown(c.dispose);
    // 화면이 듣고 있는 것처럼 붙잡아 둔다.
    c.listen(currentBandProvider, (_, __) {});
    return c;
  }

  Future<void> settle(ProviderContainer c) async {
    await c.read(myBandsProvider.future);
    await pumpEventQueue();
  }

  test('고른 밴드가 다시 켠 뒤에도 그대로다', () async {
    final first = launch(_signedIn(7));
    await settle(first);
    expect(first.read(currentBandProvider)!.id, 1);

    first.read(selectedBandIdProvider.notifier).select(2);
    await pumpEventQueue();
    first.dispose();

    final relaunched = launch(_signedIn(7));
    await settle(relaunched);
    expect(relaunched.read(currentBandProvider)!.id, 2);
  });

  test('기억한 밴드에서 나갔거나 추방·삭제됐으면 첫 밴드로', () async {
    final first = launch(_signedIn(7));
    await settle(first);
    first.read(selectedBandIdProvider.notifier).select(3);
    await pumpEventQueue();
    first.dispose();

    final relaunched = launch(_signedIn(7), myBands: bands.take(2).toList());
    await settle(relaunched);
    expect(relaunched.read(currentBandProvider)!.id, 1);
  });

  test('로그아웃하면 지운다 — 다음 계정이 물려받지 않는다', () async {
    final c = launch(_signedIn(7));
    await settle(c);
    c.read(selectedBandIdProvider.notifier).select(2);
    await pumpEventQueue();
    expect(disk.values, isNotEmpty);

    (c.read(authControllerProvider.notifier) as _FakeAuth)
        .set(const AuthState.signedOut());
    await pumpEventQueue();
    expect(disk.values, isEmpty);
    expect(c.read(selectedBandIdProvider), isNull);
  });

  test('다른 계정이 저장한 값은 쓰지 않는다', () async {
    final a = launch(_signedIn(7));
    await settle(a);
    a.read(selectedBandIdProvider.notifier).select(2);
    await pumpEventQueue();
    a.dispose();

    final b = launch(_signedIn(8));
    await settle(b);
    expect(b.read(selectedBandIdProvider), isNull);
    expect(b.read(currentBandProvider)!.id, 1);
  });

  test('시작할 때(로그인 확인 전)는 지우지 않는다', () async {
    disk.values['band.selected'] = '7:2';
    final c = launch(const AuthState.unknown());
    await pumpEventQueue();
    expect(disk.values['band.selected'], '7:2');

    (c.read(authControllerProvider.notifier) as _FakeAuth).set(_signedIn(7));
    await settle(c);
    expect(c.read(currentBandProvider)!.id, 2);
  });

  test('늦게 읽힌 저장값이 방금 고른 밴드를 덮지 않는다(알림을 눌러 켠 경우)', () async {
    disk.values['band.selected'] = '7:2';
    final gate = Completer<void>();
    disk.readGate = gate.future;

    final c = launch(_signedIn(7));
    await pumpEventQueue();
    c.read(selectedBandIdProvider.notifier).select(3); // PushService._openFromPush
    gate.complete();
    await settle(c);
    expect(c.read(currentBandProvider)!.id, 3);
  });

  test('계정이 바뀌면 알림 설정을 다시 받는다 — 앞 계정 값이 남지 않는다', () async {
    final repo = _SettingRepo();
    final c = ProviderContainer(overrides: [
      authControllerProvider.overrideWith(() => _FakeAuth(_signedIn(7))),
      notificationRepositoryProvider.overrideWithValue(repo),
    ]);
    addTearDown(c.dispose);
    c.listen(notificationSettingProvider, (_, __) {});

    expect((await c.read(notificationSettingProvider.future)).pushEnabled,
        isFalse);
    repo.push = true; // 다른 계정의 서버 값
    (c.read(authControllerProvider.notifier) as _FakeAuth).set(_signedIn(8));
    expect((await c.read(notificationSettingProvider.future)).pushEnabled,
        isTrue);
  });
}

MyBand _band(int id, String name) => MyBand(
      id: id,
      name: name,
      myRole: 'MEMBER',
      memberCount: 3,
      joinedAt: DateTime(2026, 1, id),
    );

AuthState _signedIn(int userId) => AuthState(
      status: AuthStatus.authenticated,
      user: AppUser(id: userId, name: 'u$userId'),
    );

class _FakeAuth extends AuthController {
  _FakeAuth(this._initial);
  final AuthState _initial;

  @override
  AuthState build() => _initial;

  void set(AuthState s) => state = s;
}

class _SettingRepo extends NotificationRepository {
  _SettingRepo() : super(Dio());
  bool push = false;

  @override
  Future<NotificationSetting> get() async =>
      NotificationSetting(pushEnabled: push, reminderOffsets: const []);
}

class _MemoryStorage extends FlutterSecureStorage {
  final values = <String, String>{};
  Future<void>? readGate;

  @override
  Future<String?> read({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (readGate != null) await readGate;
    return values[key];
  }

  @override
  Future<void> write({
    required String key,
    required String? value,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (value == null) {
      values.remove(key);
    } else {
      values[key] = value;
    }
  }

  @override
  Future<void> delete({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    values.remove(key);
  }
}
