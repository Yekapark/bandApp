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
    builder: (sheetContext) {
      final bands = ref.read(myBandsProvider).valueOrNull ?? const [];
      final current = ref.read(currentBandProvider);
      return SafeArea(
        child: Padding(
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
              GestureDetector(
                onTap: () {
                  Navigator.of(sheetContext).pop();
                  context.push(Routes.joinBand);
                },
                child: Container(
                  height: 48,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(13),
                    border: Border.all(
                        color: AppColors.primary.withOpacity(0.45),
                        style: BorderStyle.solid),
                  ),
                  child: const Text('+ 초대코드로 밴드 추가',
                      style: TextStyle(
                          fontSize: 13.5,
                          fontWeight: FontWeight.w700,
                          color: AppColors.primary)),
                ),
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
