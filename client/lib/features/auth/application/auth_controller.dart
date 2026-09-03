import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/dio_client.dart';
import '../../../core/storage/token_storage.dart';
import '../data/auth_models.dart';
import '../data/auth_repository.dart';

enum AuthStatus { unknown, authenticated, unauthenticated }

class AuthState {
  const AuthState({required this.status, this.user});

  final AuthStatus status;
  final AppUser? user;

  bool get isAuthenticated => status == AuthStatus.authenticated;

  const AuthState.unknown() : this(status: AuthStatus.unknown);
  const AuthState.signedOut() : this(status: AuthStatus.unauthenticated);
}

final authControllerProvider =
    NotifierProvider<AuthController, AuthState>(AuthController.new);

class AuthController extends Notifier<AuthState> {
  @override
  AuthState build() {
    // refresh 실패 신호를 받으면 즉시 로그아웃 상태로.
    final signal = ref.watch(sessionExpiredSignalProvider);
    void onExpired() => _onSessionExpired();
    signal.addListener(onExpired);
    ref.onDispose(() => signal.removeListener(onExpired));

    return const AuthState.unknown();
  }

  TokenStorage get _storage => ref.read(tokenStorageProvider);
  AuthRepository get _repo => ref.read(authRepositoryProvider);

  /// 앱 시작 시 1회. 저장된 토큰이 있으면 /users/me 로 유효성까지 확인한다.
  Future<void> bootstrap() async {
    final tokens = await _storage.load();
    if (tokens == null) {
      state = const AuthState.signedOut();
      return;
    }
    try {
      final user = await _repo.me();
      state = AuthState(status: AuthStatus.authenticated, user: user);
    } catch (_) {
      // 토큰 만료/무효 — 인터셉터가 refresh 를 시도했고 그래도 실패한 경우.
      await _storage.clear();
      state = const AuthState.signedOut();
    }
  }

  Future<void> loginEmail(String email, String password) async {
    final result = await _repo.login(email: email.trim(), password: password);
    await _apply(result);
  }

  Future<void> signupEmail({
    required String email,
    required String password,
    required String name,
  }) async {
    final result = await _repo.signup(
      email: email.trim(),
      password: password,
      name: name.trim(),
    );
    await _apply(result);
  }

  Future<void> loginKakao(String kakaoAccessToken) async {
    final result = await _repo.kakao(kakaoAccessToken: kakaoAccessToken);
    await _apply(result);
  }

  Future<void> logout() async {
    final refresh = _storage.current?.refreshToken;
    if (refresh != null) {
      await _repo.logout(refreshToken: refresh);
    }
    await _storage.clear();
    state = const AuthState.signedOut();
  }

  /// 회원 탈퇴. 성공하면 로그아웃과 같은 로컬 정리를 한다. 실패 시 예외를 던진다.
  Future<void> withdraw({String? password}) async {
    await _repo.withdraw(password: password);
    await _storage.clear();
    state = const AuthState.signedOut();
  }

  /// 밴드 탈퇴·추방 등으로 밴드 목록이 바뀌었을 때 관련 provider 를 다시 읽게 한다.
  bool get isEmailAccount => state.user?.socialProvider == null;

  Future<void> _apply(AuthResult result) async {
    await _storage.save(result.tokens);
    state = AuthState(status: AuthStatus.authenticated, user: result.user);
  }

  void _onSessionExpired() {
    if (state.status == AuthStatus.unauthenticated) return;
    _storage.clear();
    state = const AuthState.signedOut();
  }
}
