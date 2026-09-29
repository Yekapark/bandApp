import 'package:bandapp_client/core/layout/readable_width.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// LAUNCH_REVIEW P11 — Android 16 은 큰 화면에서 방향 고정을 무시한다. 태블릿·폴더블·가로에서 화면이
/// 1000dp 넘게 늘어나지 않고 가운데 기둥으로 보여야 한다.
void main() {
  Future<({double layout, double media, double left})> measure(
      WidgetTester tester, Size screen,
      {EdgeInsets padding = EdgeInsets.zero}) async {
    tester.view.physicalSize = screen;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    late double layout, media, left;
    await tester.pumpWidget(MediaQuery(
      data: MediaQueryData(size: screen, padding: padding, viewPadding: padding),
      child: Directionality(
        textDirection: TextDirection.ltr,
        child: ReadableWidth(
          child: LayoutBuilder(builder: (context, c) {
            layout = c.maxWidth;
            media = MediaQuery.sizeOf(context).width;
            left = MediaQuery.paddingOf(context).left;
            return const SizedBox.expand();
          }),
        ),
      ),
    ));
    return (layout: layout, media: media, left: left);
  }

  testWidgets('폰 세로는 그대로 쓴다', (tester) async {
    final r = await measure(tester, const Size(412, 915));
    expect(r.layout, 412);
    expect(r.media, 412);
  });

  testWidgets('태블릿 가로는 가운데 기둥으로 묶고, 안쪽이 재는 화면 폭도 기둥 폭이다', (tester) async {
    final r = await measure(tester, const Size(1280, 800));
    expect(r.layout, ReadableWidth.maxContentWidth);
    expect(r.media, ReadableWidth.maxContentWidth);
  });

  testWidgets('가로 폰의 카메라 구멍 여백은 기둥 밖 여백만큼 덜어 준다', (tester) async {
    // 915 폭 → 기둥 720, 양옆 97.5. 왼쪽 구멍 48 은 기둥 밖에 들어가므로 안쪽에서는 0.
    final r = await measure(tester, const Size(915, 412),
        padding: const EdgeInsets.only(left: 48));
    expect(r.layout, ReadableWidth.maxContentWidth);
    expect(r.left, 0);
  });
}
