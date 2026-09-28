import 'package:bandapp_client/core/media/stale_url_guard.dart';
import 'package:flutter_test/flutter_test.dart';

/// LAUNCH_REVIEW U2 — 사진·영상 주소가 만료되면 목록·상세를 새로 받는다. 정말 깨진 파일이면 새로 받아도 또
/// 실패하므로, 같은 대상은 일정 간격 안에 한 번만 다시 받아야 한다(무한 재조회 방지).
void main() {
  setUp(StaleUrlGuard.reset);

  test('같은 대상은 간격 안에 한 번만 허용한다', () {
    expect(StaleUrlGuard.allow(('post', 1)), isTrue);
    expect(StaleUrlGuard.allow(('post', 1)), isFalse);
    expect(StaleUrlGuard.allow(('post', 1)), isFalse);
  });

  test('대상이 다르면 따로 센다', () {
    expect(StaleUrlGuard.allow(('post', 1)), isTrue);
    expect(StaleUrlGuard.allow(('post', 2)), isTrue);
    expect(StaleUrlGuard.allow(('feed', 1)), isTrue);
  });

  test('간격이 지나면 다시 허용한다', () {
    expect(StaleUrlGuard.allow(('feed', 7), gap: Duration.zero), isTrue);
    expect(StaleUrlGuard.allow(('feed', 7), gap: Duration.zero), isTrue);
  });
}
