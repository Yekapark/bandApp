/// 알림 권한을 묻는 순서(LAUNCH_REVIEW U21).
///
/// 예전에는 로그인 직후 안드로이드 권한 창을 설명 없이 바로 띄웠다. 테스터는 "권한을 안 물어봐서 휴대폰 설정에서 켰다"
/// 고 했다 — 창이 다른 화면 전환과 겹쳐 지나갔거나 무슨 창인지 모르고 넘겼을 수 있다. 안드로이드는 두 번 거절하면
/// 앱이 그 창을 다시 띄울 수 없게 막으므로 설명 없이 쓰는 한 번이 아깝다.
///
/// 그래서 **앱의 안내 시트를 먼저** 보여 주고, 사용자가 [알림 받기] 를 고를 때만 권한 창을 띄운다.
/// - 이미 허용돼 있으면(안드로이드 12 이하는 기본 허용) 아무것도 묻지 않는다.
/// - 안내는 기기마다 **한 번만**. [나중에] 를 골랐으면 다시 묻지 않는다 — 알림 설정 화면의 "알림 켜기" 카드(U19)가 남아 있다.
/// - 안내를 띄울 화면이 없었으면(로그인 직후 화면이 바뀌는 중 등) 물은 것으로 치지 않고 다음 기회에 다시 띄운다.
/// - [알림 받기] 를 눌렀는데 권한 창이 **바로** 거절로 돌아오면 창이 뜨지 않은 것이다(예전에 두 번 거절한 폰). 그때는
///   휴대폰의 앱 알림 설정을 연다. 사람이 창을 읽고 "허용 안함" 을 누르는 데는 그보다 오래 걸린다.
///
/// 화면·저장소·Firebase 에 기대지 않게 함수만 받는다 — `test/push_permission_flow_test.dart`.
class PushPermissionFlow {
  PushPermissionFlow({
    required this.isGranted,
    required this.alreadyAsked,
    required this.markAsked,
    required this.showPrimer,
    required this.request,
    required this.openSettings,
    this.instantDenial = const Duration(milliseconds: 800),
  });

  /// 지금 알림이 허용돼 있는가.
  final Future<bool> Function() isGranted;

  /// 이 기기에서 안내 시트를 이미 보여 줬는가.
  final Future<bool> Function() alreadyAsked;
  final Future<void> Function() markAsked;

  /// 안내 시트. true = [알림 받기], false = [나중에], null = 띄울 화면이 없어 보여 주지 못함.
  final Future<bool?> Function() showPrimer;

  /// OS 권한 창. 허용됐으면 true.
  final Future<bool> Function() request;
  final Future<void> Function() openSettings;

  /// 이보다 빨리 거절로 돌아오면 권한 창이 뜨지 않은 것으로 본다.
  final Duration instantDenial;

  /// 알림을 받을 수 있는 상태가 되면 true(토큰을 등록해도 된다).
  Future<bool> run() async {
    if (await isGranted()) return true;
    if (await alreadyAsked()) return false;

    final accepted = await showPrimer();
    if (accepted == null) return false; // 못 보여 줬다 — 다음 기회에
    await markAsked();
    if (!accepted) return false;

    final watch = Stopwatch()..start();
    if (await request()) return true;
    if (watch.elapsed < instantDenial) await openSettings();
    return false;
  }
}
