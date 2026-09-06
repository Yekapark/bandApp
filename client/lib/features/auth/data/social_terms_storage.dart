import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final socialTermsStorageProvider = Provider<SocialTermsStorage>((ref) {
  return SocialTermsStorage(const FlutterSecureStorage());
});

/// 카카오 가입 전 약관 동의를 이 기기에서 이미 받았는지 기억한다.
///
/// **왜 기기에 두나** — 동의는 계정마다 받아야 맞지만, 카카오는 **로그인해 봐야** 누구인지
/// 알 수 있다. 즉 "이 사람이 처음인가" 를 로그인 전에는 알 방법이 없다. 그래서 이 기기에서
/// 한 번 받았는지로 갈음한다.
///
/// 기기를 바꾸면 한 번 더 묻는다. 동의 화면이 한 번 더 나오는 것뿐이라 손해가 작고,
/// 반대로 안 물어서 동의 없이 계정이 생기는 쪽이 훨씬 나쁘다.
///
/// 비밀값은 아니지만 이미 붙어 있는 저장소를 쓴다 — 이것 하나 때문에 의존성을 더하지 않는다.
class SocialTermsStorage {
  SocialTermsStorage(this._storage);

  final FlutterSecureStorage _storage;

  static const _key = 'auth.socialTermsAgreed';

  Future<bool> agreed() async => await _storage.read(key: _key) == 'true';

  Future<void> markAgreed() => _storage.write(key: _key, value: 'true');
}
