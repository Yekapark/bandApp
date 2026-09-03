import 'package:flutter/material.dart';

/// 아직 만들지 않은 화면으로의 이동을 스낵바로 안내한다.
/// (온보딩→홈 단계 이후에 실제 화면으로 교체)
void showSoon(BuildContext context, String what) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text('$what 화면은 다음 단계에서 구현됩니다.')));
}
