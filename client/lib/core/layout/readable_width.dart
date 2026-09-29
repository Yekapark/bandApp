import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// 큰 화면(태블릿, 폴더블 펼침, 폰 가로)에서 앱 전체를 **가운데 폰 폭 기둥**으로 보여 준다.
///
/// 앱의 화면들은 모두 폰 세로를 기준으로 그렸다. targetSdk 36(Android 16)부터는 너비 600dp 이상 화면에서
/// 방향 고정·크기 제한이 무시되고(LAUNCH_REVIEW P11) — 원래 방향 고정도 안 했다 — 태블릿에서 카드·목록·버튼이
/// 1000dp 넘게 늘어나 읽기 어렵다. 화면마다 손보는 대신 여기 한 곳에서 폭을 [maxWidth] 로 묶는다.
///
/// 폭만 묶으면 안쪽에서 `MediaQuery.sizeOf` 로 화면 폭을 재는 위젯(바텀시트 높이·그리드 칸 수 등)이
/// 여전히 전체 폭을 보고 계산한다. 그래서 안쪽 [MediaQuery] 의 size 도 기둥 폭으로 바꾸고, 기둥 밖 여백만큼
/// 좌우 안전 영역(가로 폰의 카메라 구멍 등)도 덜어 준다.
class ReadableWidth extends StatelessWidget {
  const ReadableWidth({super.key, required this.child, this.maxWidth = maxContentWidth});

  /// 폰 가로(약 800~900dp)와 태블릿 세로(약 800dp)를 모두 묶이게, 큰 폰 세로(약 430dp)보다는 넉넉하게.
  static const double maxContentWidth = 720;

  final Widget child;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, constraints) {
      final width = constraints.maxWidth;
      if (!width.isFinite || width <= maxWidth) return child;

      final side = (width - maxWidth) / 2;
      final mq = MediaQuery.of(context);
      EdgeInsets trim(EdgeInsets e) => e.copyWith(
            left: math.max(0, e.left - side),
            right: math.max(0, e.right - side),
          );
      return Center(
        child: SizedBox(
          width: maxWidth,
          child: MediaQuery(
            data: mq.copyWith(
              size: Size(maxWidth, mq.size.height),
              padding: trim(mq.padding),
              viewPadding: trim(mq.viewPadding),
            ),
            child: child,
          ),
        ),
      );
    });
  }
}
