import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../board/data/board_models.dart';
import '../../notification/application/notification_providers.dart';
import '../../board/data/board_repository.dart';

/// 내가 차단한 사용자 목록.
final blockedUsersProvider = FutureProvider<List<BlockedUser>>((ref) async {
  ref.watch(signedInUserIdProvider); // 계정이 바뀌면 다시 받는다
  return ref.watch(boardRepositoryProvider).listBlocks();
});
