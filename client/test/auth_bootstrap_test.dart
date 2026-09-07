import 'package:bandapp_client/core/storage/token_storage.dart';
import 'package:bandapp_client/features/auth/application/auth_controller.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

/// 안전 저장소가 통째로 깨진 상황. 기기 백업이 SharedPreferences 만 복원하고 KeyStore 키는
/// 못 살리면 실제로 이렇게 된다(read 도 delete 도 던진다).
class _BrokenStorage extends TokenStorage {
  _BrokenStorage() : super(const FlutterSecureStorage());

  @override
  Future<Tokens?> load() async => throw Exception('keystore key gone');

  @override
  Future<void> clear() async => throw Exception('keystore key gone');
}

void main() {
  test('저장소가 던져도 bootstrap 은 로그아웃 상태로 떨어진다 (스플래시에 갇히지 않는다)', () async {
    final container = ProviderContainer(
      overrides: [tokenStorageProvider.overrideWith((ref) => _BrokenStorage())],
    );
    addTearDown(container.dispose);

    // 예외가 밖으로 새면 여기서 테스트가 실패한다.
    await container.read(authControllerProvider.notifier).bootstrap();

    // unknown 에 머물면 라우터가 어디로도 못 보내 스플래시가 영원히 돈다.
    expect(
      container.read(authControllerProvider).status,
      AuthStatus.unauthenticated,
    );
  });
}
