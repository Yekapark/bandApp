import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/primary_button.dart';

/// 안드로이드 권한 창을 띄우기 전에 왜 알림이 필요한지 먼저 설명하는 시트(U21, `PushPermissionFlow`).
///
/// true = [알림 받기], false = [나중에]·뒤로 가기. **바깥을 눌러 닫지 못하게** 했다 — 그러면 null 이 돌아오는데,
/// null 은 "화면이 바뀌면서 시트가 사라졌다(못 보여 줬다)" 의 뜻으로 남겨 둔다.
Future<bool?> showPushPrimerSheet(BuildContext context) {
  return showModalBottomSheet<bool>(
    context: context,
    useRootNavigator: true,
    isDismissible: false,
    enableDrag: false,
    backgroundColor: AppColors.surface,
    builder: (sheetContext) => PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) Navigator.of(sheetContext).pop(false);
      },
      child: const _PrimerBody(),
    ),
  );
}

class _PrimerBody extends StatelessWidget {
  const _PrimerBody();

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(22, 22, 22, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: AppColors.primary.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                alignment: Alignment.center,
                child: const Icon(Icons.notifications_active_outlined,
                    color: AppColors.primary, size: 26),
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              '합주 알림 받기',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 6),
            const Text(
              '알림을 허용하면 이런 소식을 놓치지 않아요.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
            ),
            const SizedBox(height: 16),
            const _Line(Icons.schedule, '합주 시작 전 미리 알림'),
            const _Line(Icons.event_note_outlined, '새 일정·변경·취소 소식'),
            const _Line(Icons.receipt_long_outlined, '정산 요청'),
            const SizedBox(height: 18),
            PrimaryButton(
              label: '알림 받기',
              height: 50,
              onPressed: () => Navigator.of(context).pop(true),
            ),
            const SizedBox(height: 4),
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('나중에',
                  style: TextStyle(color: AppColors.textDim)),
            ),
            const Text(
              '나중에 켜려면 설정 › 알림 설정에서 켤 수 있어요.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 11, color: AppColors.textFaint),
            ),
          ],
        ),
      ),
    );
  }
}

class _Line extends StatelessWidget {
  const _Line(this.icon, this.text);

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 8),
      child: Row(
        children: [
          Icon(icon, size: 18, color: AppColors.textSecondary),
          const SizedBox(width: 10),
          Text(text, style: const TextStyle(fontSize: 13.5)),
        ],
      ),
    );
  }
}
