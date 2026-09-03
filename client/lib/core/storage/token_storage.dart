import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final tokenStorageProvider = Provider<TokenStorage>((ref) {
  return TokenStorage(const FlutterSecureStorage());
});

/// access / refresh 토큰을 안전 저장소(Keychain/KeyStore)에 보관한다.
class TokenStorage {
  TokenStorage(this._storage);

  final FlutterSecureStorage _storage;

  static const _kAccess = 'auth.accessToken';
  static const _kRefresh = 'auth.refreshToken';

  Tokens? _cache;

  /// 앱 시작 시 한 번 호출해 캐시를 채운다.
  Future<Tokens?> load() async {
    final access = await _storage.read(key: _kAccess);
    final refresh = await _storage.read(key: _kRefresh);
    if (access == null || refresh == null) {
      _cache = null;
      return null;
    }
    _cache = Tokens(accessToken: access, refreshToken: refresh);
    return _cache;
  }

  Tokens? get current => _cache;

  Future<void> save(Tokens tokens) async {
    _cache = tokens;
    await _storage.write(key: _kAccess, value: tokens.accessToken);
    await _storage.write(key: _kRefresh, value: tokens.refreshToken);
  }

  Future<void> clear() async {
    _cache = null;
    await _storage.delete(key: _kAccess);
    await _storage.delete(key: _kRefresh);
  }
}

class Tokens {
  const Tokens({required this.accessToken, required this.refreshToken});

  final String accessToken;
  final String refreshToken;
}
