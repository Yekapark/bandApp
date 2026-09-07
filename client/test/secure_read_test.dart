import 'package:bandapp_client/core/storage/secure_read.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

/// 기기 백업이 값만 복원하고 KeyStore 열쇠는 못 살린 상태 — read 가 BAD_DECRYPT 로 던진다.
class _BrokenStorage extends FlutterSecureStorage {
  final deleted = <String>[];

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
    throw Exception('BadPaddingException: BAD_DECRYPT');
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
    deleted.add(key);
  }
}

void main() {
  test('복호화가 깨진 값은 null 로 돌려주고, 다시 안 걸리게 지운다', () async {
    final storage = _BrokenStorage();

    // 던지면 이 값을 읽는 화면이 통째로 죽는다. null 이어야 한다.
    expect(await secureRead(storage, 'notifications.lastSeenAt.1'), isNull);

    // 안 지우면 앱을 다시 깔기 전까지 매번 같은 예외가 난다.
    expect(storage.deleted, ['notifications.lastSeenAt.1']);
  });
}
