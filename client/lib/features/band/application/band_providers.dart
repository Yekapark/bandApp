import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/application/auth_controller.dart';
import '../data/band_models.dart';
import '../data/band_repository.dart';

/// 내가 속한 밴드 목록. 로그인 상태에서만 로드하고, 로그아웃하면 무효화한다.
final myBandsProvider = FutureProvider<List<MyBand>>((ref) async {
  final auth = ref.watch(authControllerProvider);
  if (!auth.isAuthenticated) return const [];
  return ref.watch(bandRepositoryProvider).myBands();
});

/// 현재 선택된 밴드 id. null 이면 "아직 안 골랐거나 밴드가 없음".
final selectedBandIdProvider =
    NotifierProvider<SelectedBandId, int?>(SelectedBandId.new);

class SelectedBandId extends Notifier<int?> {
  @override
  int? build() => null;

  void select(int bandId) => state = bandId;
  void clear() => state = null;
}

/// 화면에서 실제로 쓸 "현재 밴드". 선택값이 없거나 유효하지 않으면 목록의 첫 밴드로 fallback.
final currentBandProvider = Provider<MyBand?>((ref) {
  final bands = ref.watch(myBandsProvider).valueOrNull ?? const [];
  if (bands.isEmpty) return null;

  final selected = ref.watch(selectedBandIdProvider);
  return bands.firstWhere(
    (b) => b.id == selected,
    orElse: () => bands.first,
  );
});

/// 특정 밴드의 멤버 목록.
final bandMembersProvider =
    FutureProvider.family<List<BandMember>, int>((ref, bandId) async {
  return ref.watch(bandRepositoryProvider).members(bandId);
});

/// 밴드 기본 정보(일정 등록 권한 모드 포함). 설정 화면에서 사용.
final bandDetailProvider =
    FutureProvider.family<Band, int>((ref, bandId) async {
  return ref.watch(bandRepositoryProvider).band(bandId);
});

String reservationPermissionLabel(String permission) {
  switch (permission) {
    case 'LEADER_ONLY':
      return '밴드장만 등록';
    case 'ANYONE':
      return '누구나 등록';
    case 'APPROVAL_REQUIRED':
      return '멤버 신청 → 밴드장 승인';
    default:
      return permission;
  }
}

String reservationPermissionHint(String permission) {
  switch (permission) {
    case 'LEADER_ONLY':
      return '일정은 밴드장만 만들 수 있어요.';
    case 'ANYONE':
      return '모든 멤버가 바로 일정을 만들 수 있어요.';
    case 'APPROVAL_REQUIRED':
      return '멤버가 만든 일정은 밴드장이 승인해야 확정돼요.';
    default:
      return '';
  }
}
