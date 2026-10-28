import 'package:bandapp_client/features/reservation/presentation/reservation_detail_screen.dart';
import 'package:flutter_test/flutter_test.dart';

/// 셋리스트 참고 링크 ▶ 는 웹 주소만 연다 (LAUNCH_REVIEW U68).
void main() {
  test('웹 주소는 그대로, 주소만 적은 링크는 https 를 붙여 연다', () {
    expect(setlistWebLink('https://youtu.be/abc').toString(), 'https://youtu.be/abc');
    expect(setlistWebLink('youtu.be/abc').toString(), 'https://youtu.be/abc');
    expect(setlistWebLink('HTTP://example.com/a')?.scheme, 'http');
  });

  test('다른 앱 주소는 열지 않는다', () {
    for (final other in [
      'supertoss://send?amount=10000',
      'market://details?id=x',
      'intent://scan#Intent;scheme=zxing;end',
      'kakaotalk://inappbrowser?url=x',
      'bandule://invite/ABCD2345',
      'file:///sdcard/a.txt',
      'javascript:alert(1)',
    ]) {
      expect(setlistWebLink(other), isNull, reason: other);
    }
  });
}
