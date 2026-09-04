import 'package:flutter/material.dart';

/// 밴듈 로고 마크 — 드럼스틱 두 개로 그린 체크 표시("Stick Check").
///
/// `client/brand/logo-mark.svg` 와 같은 도형을 그린다. 도형이 선 2개 + 원 2개뿐이라
/// SVG 렌더링 패키지를 의존성으로 들이지 않았다. 원본 좌표(60 단위)를 그대로 두고
/// 위젯 크기에 맞춰 스케일만 바꾼다.
class BrandMark extends StatelessWidget {
  const BrandMark({super.key, this.size = 34, this.color});

  final double size;

  /// 주면 단색으로 그린다(`logo-mono.svg` 에 해당). 없으면 원래 색.
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(painter: _StickCheckPainter(color)),
    );
  }
}

class _StickCheckPainter extends CustomPainter {
  const _StickCheckPainter(this.mono);

  final Color? mono;

  // 원본 SVG(viewBox 0 0 60 60)의 값.
  static const _strokeW = 7.0;
  static const _elbow = Offset(22, 46);
  static const _tail = Offset(9, 31);
  static const _tip = Offset(51, 11);
  static const _tailDotR = 4.4;
  static const _tipDotR = 5.2;

  static const _purple = Color(0xFFA06BFF);
  static const _orange = Color(0xFFFF6A2B);
  static const _tipDot = Color(0xFFFF8B5C);
  static const _tailDot = Color(0xFFC8AAFF);

  /// 도형이 실제로 차지하는 범위(끝 원·선 캡 포함). 60 박스 안에서 치우쳐 있어서,
  /// 이걸 기준으로 맞춰야 위젯 안에서 가운데로 온다.
  static const _bounds = Rect.fromLTRB(4.6, 5.8, 56.2, 49.5);

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.shortestSide / _bounds.longestSide;
    canvas.translate(
      (size.width - _bounds.width * scale) / 2 - _bounds.left * scale,
      (size.height - _bounds.height * scale) / 2 - _bounds.top * scale,
    );
    canvas.scale(scale);

    final stroke = Paint()
      ..strokeWidth = _strokeW
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;

    canvas.drawLine(_tail, _elbow, stroke..color = mono ?? _purple);
    canvas.drawLine(_elbow, _tip, stroke..color = mono ?? _orange);

    final fill = Paint()..style = PaintingStyle.fill;
    canvas.drawCircle(_tip, _tipDotR, fill..color = mono ?? _tipDot);
    canvas.drawCircle(_tail, _tailDotR, fill..color = mono ?? _tailDot);
  }

  @override
  bool shouldRepaint(_StickCheckPainter old) => old.mono != mono;
}
