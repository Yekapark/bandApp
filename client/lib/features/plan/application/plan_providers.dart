import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/plan_models.dart';
import '../data/plan_repository.dart';

/// 밴드의 현재 요금제.
final bandPlanProvider =
    FutureProvider.family<BandPlan, int>((ref, bandId) async {
  return ref.watch(planRepositoryProvider).view(bandId);
});
