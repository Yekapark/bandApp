import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'app_colors.dart';

/// 목업 폰트 조합:
/// - 본문: Noto Sans KR
/// - 디스플레이(로고·라벨): Bebas Neue (자간 넓게)
/// - 숫자·코드: JetBrains Mono
class AppTypography {
  const AppTypography._();

  static TextTheme textTheme() {
    final base = GoogleFonts.notoSansKrTextTheme();
    return base
        .apply(
          bodyColor: AppColors.textPrimary,
          displayColor: AppColors.textPrimary,
        )
        .copyWith(
          headlineLarge: GoogleFonts.notoSansKr(
            fontSize: 25,
            fontWeight: FontWeight.w900,
            letterSpacing: -0.5,
            height: 1.4,
            color: AppColors.textPrimary,
          ),
          headlineMedium: GoogleFonts.notoSansKr(
            fontSize: 22,
            fontWeight: FontWeight.w900,
            letterSpacing: -0.4,
            color: AppColors.textPrimary,
          ),
          titleMedium: GoogleFonts.notoSansKr(
            fontSize: 14,
            fontWeight: FontWeight.w700,
            color: AppColors.textPrimary,
          ),
          bodyMedium: GoogleFonts.notoSansKr(
            fontSize: 13,
            height: 1.6,
            color: AppColors.textSecondary,
          ),
          bodySmall: GoogleFonts.notoSansKr(
            fontSize: 11.5,
            height: 1.6,
            color: AppColors.textDim,
          ),
        );
  }

  /// Bebas Neue — 로고, 섹션 오버라인.
  static TextStyle display({
    double fontSize = 24,
    Color color = AppColors.textPrimary,
    double letterSpacing = 3.0,
  }) {
    return GoogleFonts.bebasNeue(
      fontSize: fontSize,
      color: color,
      letterSpacing: letterSpacing,
      height: 1,
    );
  }

  /// JetBrains Mono — 금액, 초대코드, 시간.
  static TextStyle mono({
    double fontSize = 13,
    FontWeight fontWeight = FontWeight.w700,
    Color color = AppColors.textPrimary,
    double letterSpacing = 0,
  }) {
    return GoogleFonts.jetBrainsMono(
      fontSize: fontSize,
      fontWeight: fontWeight,
      color: color,
      letterSpacing: letterSpacing,
    );
  }
}
