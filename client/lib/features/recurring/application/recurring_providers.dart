import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/recurring_models.dart';
import '../data/recurring_repository.dart';

/// 밴드의 정기 일정 규칙 목록.
final recurringRulesProvider =
    FutureProvider.family<List<RecurringRule>, int>((ref, bandId) async {
  return ref.watch(recurringRepositoryProvider).list(bandId);
});
