import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/settlement_models.dart';
import '../data/settlement_repository.dart';

typedef SettlementKey = ({int bandId, int reservationId});

/// 일정의 정산 현황. 아직 정산이 없으면 데이터가 null.
final settlementProvider =
    FutureProvider.family<Settlement?, SettlementKey>((ref, key) async {
  return ref.watch(settlementRepositoryProvider).get(
        bandId: key.bandId,
        reservationId: key.reservationId,
      );
});
