import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/application/auth_controller.dart';
import '../data/band_models.dart';
import '../data/band_repository.dart';
import '../data/selected_band_storage.dart';

/// 내가 속한 밴드 목록. 로그인 상태에서만 로드하고, 로그아웃하면 무효화한다.
final myBandsProvider = FutureProvider<List<MyBand>>((ref) async {
  final auth = ref.watch(authControllerProvider);
  if (!auth.isAuthenticated) return const [];
  return ref.watch(bandRepositoryProvider).myBands();
});

/// 현재 선택된 밴드 id. null 이면 "아직 안 골랐거나 밴드가 없음".
final selectedBandIdProvider =
    NotifierProvider<SelectedBandId, int?>(SelectedBandId.new);

/// 고른 밴드를 기기에 기억하고(QA-F01), 다시 켜면 되살린다. 되살린 밴드가 더 이상 내 목록에 없으면
/// (나갔거나 추방·삭제) [currentBandProvider] 가 첫 밴드로 돌아간다.
///
/// 계정이 바뀌면 처음부터 다시 만든다 — 로그아웃하면 기억도 지운다(다른 계정이 물려받지 않게).
class SelectedBandId extends Notifier<int?> {
  /// 이 값이 바뀌면 진행 중이던 복원 결과를 버린다(계정 전환·dispose).
  int _generation = 0;

  /// 사용자가 이번 세션에 직접 골랐는가. 그랬으면 늦게 도착한 복원 값이 덮지 않는다
  /// (알림을 눌러 앱이 켜지면 그 알림의 밴드를 먼저 고른다 — `PushService`).
  bool _touched = false;
  int? _userId;

  SelectedBandStorage get _storage => ref.read(selectedBandStorageProvider);

  @override
  int? build() {
    final (status, userId) = ref.watch(
        authControllerProvider.select((s) => (s.status, s.user?.id)));
    final gen = ++_generation;
    ref.onDispose(() => _generation++);
    _touched = false;
    _userId = userId;

    if (status == AuthStatus.unauthenticated) {
      _ignore(_storage.clear());
    } else if (userId != null) {
      _restore(userId, gen);
    }
    return null;
  }

  Future<void> _restore(int userId, int gen) async {
    int? saved;
    try {
      saved = await _storage.load(userId);
    } catch (_) {
      return;
    }
    if (saved == null || gen != _generation || _touched) return;
    state = saved;
  }

  void select(int bandId) {
    _touched = true;
    state = bandId;
    final userId = _userId;
    if (userId != null) _ignore(_storage.save(userId, bandId));
  }

  void clear() {
    _touched = true;
    state = null;
    _ignore(_storage.clear());
  }

  /// 기억은 덤이다 — 저장소가 실패해도 선택 자체는 이번 실행 동안 유효하다.
  static void _ignore(Future<void> f) => f.catchError((_) {});
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
