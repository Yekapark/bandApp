import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/invite_models.dart';
import '../data/invite_repository.dart';

/// 현재 밴드의 활성 초대코드 (없으면 null). 밴드장만 조회 가능하므로 화면에서 가드한다.
final currentInviteProvider =
    FutureProvider.family<BandInvite?, int>((ref, bandId) async {
  return ref.watch(inviteRepositoryProvider).current(bandId);
});
