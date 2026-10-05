import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../routing/app_router.dart';
import '../../../band/application/band_providers.dart';

/// 밴드 전환 바텀시트. 한 계정이 여러 밴드에 속할 수 있다.
Future<void> showBandSwitchSheet(BuildContext context, WidgetRef ref) {
  return showModalBottomSheet<void>(
    context: context,
    backgroundColor: AppColors.surface,
    // 밴드가 많으면 목록이 시트 기본 높이(화면 9/16)를 넘는데 스크롤이 없어 아래 밴드·버튼이 잘렸다(U44·QA-R11).
    // 하단 탭 영역 안쪽 화면이 아니라 앱 전체 위에 띄우고, 화면의 85% 까지 키운 뒤 넘치면 스크롤한다.
    useRootNavigator: true,
    isScrollControlled: true,
    constraints: BoxConstraints(
      maxHeight: MediaQuery.sizeOf(context).height * 0.85,
    ),
    builder: (sheetContext) {
      final bands = ref.read(myBandsProvider).valueOrNull ?? const [];
      final current = ref.read(currentBandProvider);
      return SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(18, 14, 18, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 38,
                  height: 4,
                  decoration: BoxDecoration(
                    color: const Color(0x2EFFFFFF),
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const Text('내 밴드 전환',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
              const SizedBox(height: 4),
              const Text('한 계정으로 여러 밴드에 소속될 수 있어요.',
                  style: TextStyle(fontSize: 11.5, color: AppColors.textDim)),
              const SizedBox(height: 14),
              for (final b in bands)
                Padding(
                  padding: const EdgeInsets.only(bottom: 7),
                  child: GestureDetector(
                    onTap: () {
                      ref.read(selectedBandIdProvider.notifier).select(b.id);
                      Navigator.of(sheetContext).pop();
                    },
                    child: Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: b.id == current?.id
                            ? AppColors.primary.withOpacity(0.1)
                            : AppColors.surface,
                        borderRadius: BorderRadius.circular(13),
                        border: Border.all(
                          color: b.id == current?.id
                              ? AppColors.primary.withOpacity(0.4)
                              : AppColors.borderFaint,
                        ),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 34,
                            height: 34,
                            decoration: BoxDecoration(
                              color: AppColors.surfaceAlt,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            alignment: Alignment.center,
                            child: const Text('♪',
                                style: TextStyle(
                                    fontSize: 15, color: AppColors.primary)),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Flexible(
                                      child: Text(
                                        b.name,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(
                                            fontSize: 13.5,
                                            fontWeight: FontWeight.w700),
                                      ),
                                    ),
                                    const SizedBox(width: 7),
                                    _RoleBadge(isLeader: b.isLeader),
                                  ],
                                ),
                                const SizedBox(height: 2),
                                Text('멤버 ${b.memberCount}명',
                                    style: const TextStyle(
                                        fontSize: 11,
                                        color: AppColors.textFaint)),
                              ],
                            ),
                          ),
                          if (b.id == current?.id)
                            const Text('현재',
                                style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w700,
                                    color: AppColors.primary)),
                        ],
                      ),
                    ),
                  ),
                ),
              const SizedBox(height: 6),
              // 밴드가 이미 있어도 새 밴드를 만들 수 있어야 한다 — 서버는 막지 않는데 예전엔 이 시트에 가입만 있어
              // 첫 밴드 이후로는 만들 길이 없었다(LAUNCH_REVIEW U12).
              Row(
                children: [
                  Expanded(
                    child: _SheetAction(
                      label: '+ 새 밴드 만들기',
                      onTap: () {
                        Navigator.of(sheetContext).pop();
                        context.push(Routes.createBand);
                      },
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _SheetAction(
                      label: '+ 초대코드로 가입',
                      onTap: () {
                        Navigator.of(sheetContext).pop();
                        context.push(Routes.joinBand);
                      },
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
    },
  );
}

class _RoleBadge extends StatelessWidget {
  const _RoleBadge({required this.isLeader});
  final bool isLeader;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: isLeader
            ? AppColors.primary.withOpacity(0.16)
            : AppColors.surfaceAlt,
        borderRadius: BorderRadius.circular(5),
      ),
      child: Text(
        isLeader ? '밴드장' : '멤버',
        style: TextStyle(
          fontSize: 9.5,
          fontWeight: FontWeight.w800,
          color: isLeader ? AppColors.primary : AppColors.textDim,
        ),
      ),
    );
  }
}

class _SheetAction extends StatelessWidget {
  const _SheetAction({required this.label, required this.onTap});
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 48,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(13),
          border: Border.all(color: AppColors.primary.withOpacity(0.45)),
        ),
        child: Text(label,
            style: const TextStyle(
                fontSize: 13.5,
                fontWeight: FontWeight.w700,
                color: AppColors.primary)),
      ),
    );
  }
}
