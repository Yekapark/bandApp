import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/settlement_models.dart';
import '../data/settlement_repository.dart';

typedef SettlementKey = ({int bandId, int reservationId});

/// 일정의 정산 현황. 아직 정산이 없으면 데이터가 null.
///
/// **autoDispose** — 화면을 닫으면 버리고 다시 열 때 새로 받는다. 정산은 다른 멤버가 재계산·납부 체크로 바꾸는데,
/// 캐시가 앱 수명 내내 남아 알림을 눌러 다시 열어도 옛 금액·납부 체크가 보였다(U38·QA-R04). 글 상세(U2)와 같은 이유.
final settlementProvider = FutureProvider.autoDispose
    .family<Settlement?, SettlementKey>((ref, key) async {
  return ref.watch(settlementRepositoryProvider).get(
        bandId: key.bandId,
        reservationId: key.reservationId,
      );
});

/// 밴드 정산 목록 첫 페이지. 이어지는 페이지는 화면이 커서로 직접 붙인다.
final bandSettlementsProvider =
    FutureProvider.family<BandSettlementPage, int>((ref, bandId) async {
  return ref.watch(settlementRepositoryProvider).listForBand(bandId: bandId);
});
