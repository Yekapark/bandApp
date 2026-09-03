import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/application/auth_controller.dart';
import '../features/auth/presentation/login_screen.dart';
import '../features/auth/presentation/signup_screen.dart';
import '../features/auth/presentation/splash_screen.dart';
import '../features/auth/presentation/terms_screen.dart';
import '../features/band/presentation/band_gate_screen.dart';
import '../features/band/presentation/create_band_screen.dart';
import '../features/band/presentation/join_band_screen.dart';
import '../features/home/presentation/home_screen.dart';

class Routes {
  const Routes._();
  static const splash = '/';
  static const login = '/login';
  static const terms = '/terms';
  static const signup = '/signup';
  static const bandGate = '/band-gate';
  static const createBand = '/band-gate/create';
  static const joinBand = '/band-gate/join';
  static const home = '/home';
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _AuthRefresh();
  ref.listen(authControllerProvider.select((s) => s.status), (_, __) {
    refresh.bump();
  });
  ref.onDispose(refresh.dispose);

  return GoRouter(
    initialLocation: Routes.splash,
    refreshListenable: refresh,
    redirect: (context, state) {
      final status = ref.read(authControllerProvider).status;
      final loc = state.matchedLocation;

      // 부팅 확인 전에는 스플래시에 머문다.
      if (status == AuthStatus.unknown) {
        return loc == Routes.splash ? null : Routes.splash;
      }

      const publicRoutes = {Routes.login, Routes.terms, Routes.signup};
      final onPublic = publicRoutes.contains(loc);

      if (status == AuthStatus.unauthenticated) {
        return onPublic ? null : Routes.login;
      }

      // 로그인 상태에서 스플래시/공개 화면에 있으면 홈으로.
      if (loc == Routes.splash || onPublic) return Routes.home;
      return null;
    },
    routes: [
      GoRoute(path: Routes.splash, builder: (_, __) => const SplashScreen()),
      GoRoute(path: Routes.login, builder: (_, __) => const LoginScreen()),
      GoRoute(path: Routes.terms, builder: (_, __) => const TermsScreen()),
      GoRoute(path: Routes.signup, builder: (_, __) => const SignupScreen()),
      GoRoute(path: Routes.bandGate, builder: (_, __) => const BandGateScreen()),
      GoRoute(
        path: Routes.createBand,
        builder: (_, __) => const CreateBandScreen(),
      ),
      GoRoute(path: Routes.joinBand, builder: (_, __) => const JoinBandScreen()),
      GoRoute(path: Routes.home, builder: (_, __) => const HomeScreen()),
    ],
  );
});

class _AuthRefresh extends ChangeNotifier {
  void bump() => notifyListeners();
}
