import 'dart:async' show unawaited;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
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
    } catch (e) {
      if (e is ApiException && _isTransient(e)) {
        // 인터넷이 없거나 서버가 잠깐 안 받는 것 — 토큰이 틀렸다는 뜻이 아니다. 예전에는 여기서도
        // 토큰을 지워서, 지하철에서 앱을 열기만 해도 로그아웃됐다. 토큰이 정말 무효면 인터셉터가
        // 401 → refresh 실패로 세션 만료를 따로 알린다. 사용자 정보는 나중에 채운다.
        state = const AuthState(status: AuthStatus.authenticated);
        unawaited(_fillUserLater());
        return;
      }
      // 토큰 만료/무효(인터셉터의 refresh 도 실패) 또는 저장소 자체가 깨진 경우.
      // clear() 마저 던질 수 있으므로 여기서 한 번 더 삼킨다 — 상태 전환이 최우선이다.
      try {
        await _storage.clear();
      } catch (_) {}
      state = const AuthState.signedOut();
    }
  }

  static bool _isTransient(ApiException e) =>
      e.isNetwork || (e.statusCode ?? 0) >= 500;

  /// 오프라인으로 시작했을 때 사용자 정보(내 id·이름)를 뒤늦게 채운다. 없으면 "내 글" 표시 등이
  /// 앱을 다시 켤 때까지 빠진다. 그사이 로그아웃했거나 다른 계정으로 들어왔으면 손대지 않는다.
  Future<void> _fillUserLater() async {
    for (var wait = 5; wait <= 320; wait *= 2) {
      await Future<void>.delayed(Duration(seconds: wait));
      try {
        if (!state.isAuthenticated || state.user != null) return;
        final user = await _repo.me();
        if (state.isAuthenticated && state.user == null) {
          state = AuthState(status: AuthStatus.authenticated, user: user);
        }
        return;
      } catch (_) {
        // 아직 오프라인이거나, 컨트롤러가 이미 버려졌다(state 접근이 던진다) — 다음 차례에.
      }
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
    // 서버 정리·푸시 해제는 최선을 다할 뿐이다. 오프라인이면 실패하거나 한참 걸리는데, 그렇다고
    // 로그아웃 버튼이 안 먹으면 안 된다 — 이 기기에서의 로그아웃(아래 finally)은 무조건 한다.
    try {
      if (refresh != null || deviceToken != null) {
        await _repo
            .logout(refreshToken: refresh ?? '-', deviceToken: deviceToken)
            .timeout(const Duration(seconds: 8));
      }
      await push.stop(unregister: false).timeout(const Duration(seconds: 5));
    } catch (_) {
      // 서버 쪽 기기 토큰 정리는 못 했을 수 있다(U1 은 최선 노력). 로컬 로그아웃이 우선이다.
    } finally {
      try {
        await _storage.clear();
      } catch (_) {
        // 저장소가 깨졌어도 캐시는 clear() 첫 줄에서 비워졌다 — 이 실행 동안은 로그아웃이다.
      }
      state = const AuthState.signedOut();
    }
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
    // 안전 저장소가 깨져 있으면 지우기도 던진다 — 아무도 기다리지 않는 Future 라 처리 안 된
    // 오류로 남는다. 캐시는 clear() 첫 줄에서 이미 비워져 이 기기에서는 로그아웃이다.
    unawaited(_storage.clear().catchError((_) {}));
    state = const AuthState.signedOut();
  }
}

/// 지금 로그인한 사용자 id. 내 것만 담는 캐시(알림 설정·알림 목록)가 이걸 지켜본다 — 로그아웃하고 다른 계정으로
/// 들어오면 다시 받는다. 데이터 저장소(일정·게시판·정산 등)도 이걸 지켜봐서, 계정이 바뀌면 그 위의 캐시가 모두
/// 새로 받아진다(AUTH-09 — 같은 밴드의 다른 계정으로 바꿔 로그인하면 앞 계정 시점의 화면이 남아 있었다). 예전에는 앱을 끄기 전까지 앞 계정의 알림 설정·알림 목록이 그대로 보였고, 그 설정으로
/// [저장] 을 누르면 새 계정에 앞 계정 값이 들어갔다.
final signedInUserIdProvider =
    Provider<int?>((ref) => ref.watch(authControllerProvider.select((s) => s.user?.id)));
