import 'dart:async' show unawaited;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/dio_client.dart';
import '../../../core/storage/token_storage.dart';
import '../../notification/data/push_service.dart';
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

/// 강제 로그아웃된 까닭 — 로그인 화면이 한 번 보여 주고 비운다(예: 이용 정지 안내, LAUNCH_REVIEW P7).
final authNoticeProvider = StateProvider<String?>((ref) => null);

class AuthController extends Notifier<AuthState> {
  @override
  AuthState build() {
    // refresh 실패 신호를 받으면 즉시 로그아웃 상태로.
    final signal = ref.watch(sessionExpiredSignalProvider);
    void onExpired() => _onSessionExpired(signal.takeNotice());
    signal.addListener(onExpired);
    ref.onDispose(() => signal.removeListener(onExpired));

    return const AuthState.unknown();
  }

  TokenStorage get _storage => ref.read(tokenStorageProvider);
  AuthRepository get _repo => ref.read(authRepositoryProvider);

  /// 앱 시작 시 1회. 저장된 토큰이 있으면 /users/me 로 유효성까지 확인한다.
  ///
  /// **무슨 일이 있어도 예외를 밖으로 내보내지 않는다.** 여기서 던지면 status 가 unknown 에
  /// 머물고 라우터가 어디로도 못 보내서 스플래시에 영원히 갇힌다. 안전 저장소 읽기도
  /// 실패할 수 있다 — 기기 백업이 SharedPreferences 만 복원하고 KeyStore 키는 못 살려서
  /// 복호화가 깨지는 경우가 실제로 있다. 그럴 땐 저장된 것을 버리고 로그아웃 상태로 시작한다.
  Future<void> bootstrap() async {
    try {
      final tokens = await _storage.load();
      if (tokens == null) {
        state = const AuthState.signedOut();
        return;
      }
      final user = await _repo.me();
      state = AuthState(status: AuthStatus.authenticated, user: user);
    } catch (_) {
      // 토큰 만료/무효(인터셉터의 refresh 도 실패) 또는 저장소 자체가 깨진 경우.
      // clear() 마저 던질 수 있으므로 여기서 한 번 더 삼킨다 — 상태 전환이 최우선이다.
      try {
        await _storage.clear();
      } catch (_) {}
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

  /// 로그아웃. **푸시 정리를 토큰을 지우기 전에** 한다 — 예전에는 토큰을 먼저 지운 뒤 인증이 필요한 해제 요청을
  /// 보내 401 이 났고, 로그아웃한 폰에 이전 계정의 일정·정산 알림이 계속 갔다(LAUNCH_REVIEW U1). 이제 기기 토큰을
  /// 로그아웃 요청에 실어 서버가 지우고, 기기의 FCM 토큰도 폐기한다.
  Future<void> logout() async {
    final refresh = _storage.current?.refreshToken;
    final push = ref.read(pushServiceProvider);
    final deviceToken = push.currentToken;
    if (refresh != null || deviceToken != null) {
      await _repo.logout(refreshToken: refresh ?? '-', deviceToken: deviceToken);
    }
    await push.stop(unregister: false);
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

  /// 세션 만료(refresh 실패)로 강제 로그아웃. 인증 없이 통하는 로그아웃 요청으로 이 기기의 푸시 토큰을 지운다 —
  /// 안 그러면 다시 로그인하기 전까지 만료된 계정의 알림이 계속 온다(U1).
  void _onSessionExpired([String? notice]) {
    // 상태보다 먼저 — 상태가 바뀌면 라우터가 로그인 화면을 띄우고, 그 화면이 이걸 읽는다.
    if (notice != null) ref.read(authNoticeProvider.notifier).state = notice;
    if (state.status == AuthStatus.unauthenticated) return;
    final stale = _storage.current?.refreshToken;
    final push = ref.read(pushServiceProvider);
    final deviceToken = push.currentToken;
    if (deviceToken != null) {
      unawaited(_repo.logout(refreshToken: stale ?? '-', deviceToken: deviceToken));
    }
    unawaited(push.stop(unregister: false));
    _storage.clear();
    state = const AuthState.signedOut();
  }
}
