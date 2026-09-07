import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'features/band/application/band_providers.dart';
import 'features/board/application/board_providers.dart';
import 'features/home/application/home_providers.dart';
import 'features/plan/application/plan_providers.dart';
import 'features/reservation/application/calendar_providers.dart';
import 'features/settlement/application/settlement_providers.dart';

/// 앱으로 돌아왔을 때 데이터를 다시 부를지 정하는 최소 간격.
///
/// 없으면(=매번 갱신) 알림 눌러 들락날락하는 것만으로 요청이 계속 나가고, 화면이 로딩으로
/// 깜빡인다. 너무 길면 "한참 뒤에 돌아왔는데 옛날 값" 이 된다. 60초는 "잠깐 카톡 보고 옴"
/// 은 거르고 "회의 끝나고 돌아옴" 은 갱신하는 선이다. 바꾸려면 이 값만 고치면 된다.
const _minRefreshGap = Duration(seconds: 60);

/// 탭 화면들이 보여주는 **서버 데이터**. 파생 프로바이더(currentBand·nextReservation)와
/// 화면 상태(calendarMonth·showCancelled)는 넣지 않는다 — 원본이 갱신되면 따라온다.
///
/// **탭에 새 서버 데이터를 붙이면 여기에도 넣는다.** 안 넣으면 그 화면만 옛 값이 남는다.
final _bandData = <ProviderOrFamily>[
  myBandsProvider,
  bandMembersProvider,
  bandPlanProvider,
  upcomingReservationsProvider,
  monthReservationsProvider,
  roomsProvider,
  boardFeedProvider,
  bandSettlementsProvider,
  // 알림 목록은 여기 없다 — PushService 가 복귀할 때마다(간격 제한 없이) 갱신한다.
  // 종 배지는 늦으면 바로 티가 나서 60초를 기다릴 이유가 없다.
];

/// 앱이 포그라운드로 돌아올 때 밴드 데이터를 다시 불러온다.
///
/// **왜 필요한가** — 프로바이더가 autoDispose 가 아니라 캐시가 프로세스 수명 내내 남는다.
/// 내가 바꾼 것은 각 화면이 직접 무효화하지만, **남이 바꾼 것**은 알 길이 없어서
/// pull-to-refresh 를 당기기 전까지 옛 값이 그대로였다. 앱을 껐다 켜도 안드로이드가
/// 프로세스를 살려 두면 마찬가지라, 사용자가 "언제 갱신되는지" 규칙을 배울 수 없었다.
final foregroundRefreshProvider = Provider<ForegroundRefresh>((ref) {
  final refresher = ForegroundRefresh(ref);
  ref.onDispose(refresher.dispose);
  return refresher;
});

class ForegroundRefresh {
  ForegroundRefresh(this._ref) {
    _listener = AppLifecycleListener(onResume: _onResume);
  }

  final Ref _ref;
  late final AppLifecycleListener _listener;
  DateTime _lastRefresh = DateTime.now();

  void _onResume() {
    final now = DateTime.now();
    if (now.difference(_lastRefresh) < _minRefreshGap) return;
    _lastRefresh = now;
    // 듣는 화면이 없는 프로바이더는 버려지기만 하고 요청이 나가지 않는다.
    for (final provider in _bandData) {
      _ref.invalidate(provider);
    }
  }

  void dispose() => _listener.dispose();
}
