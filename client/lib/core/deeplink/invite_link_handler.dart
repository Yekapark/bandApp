import 'dart:async';

import 'package:app_links/app_links.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../routing/app_router.dart';

/// 초대 링크(`bandule://invite/{code}`)를 받아 합류 화면으로 넘긴다.
///
/// 앱이 꺼져 있다가 링크로 열린 경우와, 이미 떠 있는데 링크가 온 경우 둘 다 받는다.
///
/// **스킴이 `bandapp` 이 아니라 `bandule` 인 이유** — `bandapp://` 는 네이버 밴드가 쓰는
/// 주소다. 예전 값으로 두면 초대 링크가 네이버 밴드 앱을 열었다(실제로 그랬다).
/// 백엔드의 `app.deeplink.scheme` 과 같은 값이어야 한다.
class InviteLinkHandler {
  InviteLinkHandler(this._router);

  /// 아직 못 연 초대코드.
  ///
  /// **초대 링크를 받은 사람은 대개 로그인도 안 되어 있다** — 새로 들어오는 멤버니까.
  /// 그대로 두면 로그인 화면으로 가면서 코드가 사라지고, 로그인해도 홈으로 갈 뿐이다.
  /// 여기 담아 두었다가 로그인이 끝나면 라우터가 꺼내 쓴다(`app_router.dart` 의 redirect).
  static String? pendingCode;

  /// 꺼내면서 비운다. 한 번만 쓰이게 해서 로그인할 때마다 합류 화면이 뜨는 것을 막는다.
  static String? takePendingCode() {
    final code = pendingCode;
    pendingCode = null;
    return code;
  }

  final GoRouter _router;
  final AppLinks _appLinks = AppLinks();
  StreamSubscription<Uri>? _sub;

  /// 코드가 오면 합류 화면을 연다. 합류 화면은 이미 `?code=` 를 받게 돼 있다.
  ///
  /// 링크를 눌렀는데 아무 반응이 없는 것이 사용자에게 가장 나쁘므로, 코드를 못 읽어도
  /// 화면은 열어 준다 — 손으로 넣을 수 있는 입력칸이 거기 있다.
  void _open(Uri uri) {
    if (!isInviteLink(uri)) {
      // 우리 초대 링크가 아니면 손대지 않는다. 웹에서는 페이지 주소 자체가 여기로
      // 들어오므로, 이 검사가 없으면 앱을 열 때마다 합류 화면이 뜬다.
      debugPrint('초대 링크가 아니라 무시: $uri');
      return;
    }
    final code = codeOf(uri);
    debugPrint('초대 링크 수신: $uri (code=$code)');
    // 코드를 못 읽어도 화면은 열어 준다 — 링크를 눌렀는데 아무 반응이 없는 것이
    // 사용자에게 가장 나쁘고, 그 화면에 손으로 넣는 입력칸이 있다.
    _router.push(
      code == null ? Routes.joinBand : '${Routes.joinBand}?code=$code',
    );
  }

  @visibleForTesting
  static bool isInviteLink(Uri uri) =>
      uri.scheme == 'bandule' && uri.host == 'invite';

  /// `bandule://invite/ABCD1234` 에서 `ABCD1234`.
  ///
  /// `host` 가 `invite` 이므로 코드는 첫 경로 조각에 있다. 여기서 코드 규칙(8자)까지
  /// 따지지는 않는다. 그건 서버가 판정할 일이고, 앱이 미리 거르면 서버 규칙이 바뀔 때
  /// 앱이 먼저 막는다.
  @visibleForTesting
  static String? codeOf(Uri uri) {
    final segments = uri.pathSegments.where((s) => s.isNotEmpty);
    return segments.isEmpty ? null : segments.first;
  }

  Future<void> start() async {
    // 앱이 꺼져 있다가 링크로 열린 경우.
    try {
      final initial = await _appLinks.getInitialLink();
      if (initial != null) _open(initial);
    } catch (e) {
      debugPrint('초대 링크 최초 수신 실패: $e');
    }
    // 앱이 떠 있는 동안 온 링크.
    _sub = _appLinks.uriLinkStream.listen(
      _open,
      onError: (Object e) => debugPrint('초대 링크 수신 실패: $e'),
    );
  }

  void dispose() {
    _sub?.cancel();
    _sub = null;
  }
}

/// 앱이 사는 동안 하나만 둔다. `BandApp` 이 watch 해서 시작시킨다.
final inviteLinkHandlerProvider = Provider<InviteLinkHandler>((ref) {
  final handler = InviteLinkHandler(ref.watch(routerProvider));
  unawaited(handler.start());
  ref.onDispose(handler.dispose);
  return handler;
});
