import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 목업의 꽉 찬 54px 라운드 버튼. 비활성 시 어둡게, 로딩 시 스피너.
class PrimaryButton extends StatelessWidget {
  const PrimaryButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.loading = false,
    this.enabled = true,
    this.background = AppColors.primary,
    this.foreground = AppColors.onPrimary,
    this.height = 54,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool loading;
  final bool enabled;
  final Color background;
  final Color foreground;
  final double height;

  @override
  Widget build(BuildContext context) {
    final active = enabled && !loading && onPressed != null;
    return Semantics(
      button: true,
      enabled: active,
      label: label,
      child: GestureDetector(
        onTap: active ? onPressed : null,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 140),
          height: height,
          decoration: BoxDecoration(
            color: active ? background : AppColors.surfaceRaised,
            borderRadius: BorderRadius.circular(AppTheme.radiusButton),
          ),
          alignment: Alignment.center,
          child: loading
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppColors.onPrimary,
                  ),
                )
              : Text(
                  label,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: active ? foreground : AppColors.textFaint,
                  ),
                ),
        ),
      ),
    );
  }
}
