import 'package:flutter/material.dart';

/// `example/BandScreen.dc.html` 목업에서 추출한 팔레트.
/// 다크 단일 테마 (밴드/음악 감성 · 포인트 오렌지 + 보조 퍼플).
class AppColors {
  const AppColors._();

  // 배경 계열
  static const Color background = Color(0xFF0B0B0E);
  static const Color surface = Color(0xFF121218);
  static const Color surfaceRaised = Color(0xFF15151B);
  static const Color surfaceCard = Color(0xFF111117);
  static const Color surfaceAlt = Color(0xFF1D1D25);

  // 경계선 (흰색 저투명)
  static const Color border = Color(0x14FFFFFF); // rgba(255,255,255,.08)
  static const Color borderStrong = Color(0x1AFFFFFF); // rgba(255,255,255,.10)
  static const Color borderFaint = Color(0x0EFFFFFF); // rgba(255,255,255,.055)

  // 텍스트
  static const Color textPrimary = Color(0xFFF2F0EC);
  static const Color textSecondary = Color(0xB3F2F0EC); // .70
  static const Color textDim = Color(0x73F2F0EC); // .45
  static const Color textFaint = Color(0x54F2F0EC); // .33

  // 포인트
  static const Color primary = Color(0xFFFF6A2B);
  static const Color primaryHover = Color(0xFFFF8B5C);
  static const Color primarySoft = Color(0xFFFFB08A);
  static const Color onPrimary = Color(0xFF120806);

  static const Color purple = Color(0xFFA06BFF);
  static const Color purpleSoft = Color(0xFFC8AAFF);
  static const Color onPurple = Color(0xFF170B26);

  // 상태
  static const Color danger = Color(0xFFFF5A4D);
  static const Color success = Color(0xFF5AD1A0);

  // 소셜
  static const Color kakao = Color(0xFFFEE500);
  static const Color onKakao = Color(0xFF1A1200);
  static const Color naver = Color(0xFF03C75A);
  static const Color onNaver = Color(0xFF04210F);
}
