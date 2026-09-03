import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../board/data/board_models.dart';
import '../../board/data/board_repository.dart';

/// 내가 차단한 사용자 목록.
final blockedUsersProvider = FutureProvider<List<BlockedUser>>((ref) async {
  return ref.watch(boardRepositoryProvider).listBlocks();
});
