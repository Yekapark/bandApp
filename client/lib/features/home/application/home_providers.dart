import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../reservation/data/reservation_models.dart';
import '../../reservation/data/reservation_repository.dart';

/// 홈 화면 "다가오는 일정": 지금부터 60일간의 취소/거절 제외 일정.
final upcomingReservationsProvider =
    FutureProvider.family<List<Reservation>, int>((ref, bandId) async {
  final now = DateTime.now();
  final items = await ref.watch(reservationRepositoryProvider).list(
        bandId: bandId,
        from: now,
        to: now.add(const Duration(days: 60)),
      );
  // 서버가 startAt 오름차순으로 주지만, 과거로 걸친 항목은 제거.
  return items.where((r) => r.endAt.isAfter(now)).toList(growable: false);
});

/// 그 중 가장 가까운 한 건 (홈 상단 "다음 합주" 카드).
final nextReservationProvider =
    Provider.family<Reservation?, int>((ref, bandId) {
  final list = ref.watch(upcomingReservationsProvider(bandId)).valueOrNull;
  if (list == null || list.isEmpty) return null;
  return list.first;
});
