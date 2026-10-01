import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// 입력 중인 폼을 뒤로 가기로 닫을 때 묻는다. true 면 나간다.
Future<bool> confirmDiscardChanges(BuildContext context) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      backgroundColor: AppColors.surface,
      title: const Text('입력을 그만둘까요?', style: TextStyle(fontSize: 16)),
      content: const Text(
        '입력한 내용은 저장되지 않아요.',
        style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: const Text('계속 입력'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(ctx, true),
          child: const Text('나가기', style: TextStyle(color: AppColors.danger)),
        ),
      ],
    ),
  );
  return ok ?? false;
}
