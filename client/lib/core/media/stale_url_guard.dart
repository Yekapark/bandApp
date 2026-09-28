/// 사진·영상 주소(presigned URL)는 서버가 **10분짜리**로 준다(`app.r2.download-url-ttl`, 방침 제9조).
/// 목록이나 상세를 연 채 10분이 지나면 같은 주소로는 더 못 받는다 — 예전에는 그대로 깨진 그림과
/// "영상을 재생할 수 없어요" 만 보였다(LAUNCH_REVIEW U2).
///
/// 불러오기에 실패하면 그 목록·상세를 새로 받아 새 주소를 얻는다. 다만 정말로 깨진 파일이면 새로 받아도
/// 또 실패하므로, **같은 대상은 [gap] 에 한 번만** 다시 받게 막는다(무한 재조회 방지).
class StaleUrlGuard {
  StaleUrlGuard._();

  static final _last = <Object, DateTime>{};

  /// [key](예: `('feed', bandId)`, `('post', postId)`)를 지금 다시 받아도 되는가. true 면 기록한다.
  static bool allow(Object key, {Duration gap = const Duration(seconds: 60)}) {
    final now = DateTime.now();
    final last = _last[key];
    if (last != null && now.difference(last) < gap) return false;
    _last[key] = now;
    return true;
  }

  /// 테스트용.
  static void reset() => _last.clear();
}
