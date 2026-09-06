import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/app_colors.dart';

/// 하단 탭바를 유지하는 셸. 실제 화면이 있는 탭(홈·캘린더)만 브랜치를 전환하고,
/// 탭 5개가 모두 실제 화면을 가진다 — 홈·캘린더·지도·게시판·정산.
class TabShell extends StatelessWidget {
  const TabShell({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  /// 브랜치가 있는 탭 — 순서가 `StatefulShellBranch` 순서와 같아야 한다.
  static const _branchTabs = <(String, IconData)>[
    ('홈', Icons.home_filled),
    ('캘린더', Icons.calendar_today),
    ('지도', Icons.map_outlined),
    ('게시판', Icons.grid_view),
    ('정산', Icons.receipt_long),
  ];

  /// 아직 화면이 없는 탭.

  @override
  Widget build(BuildContext context) {
    final current = navigationShell.currentIndex;
    // 시스템 내비게이션 바만큼 아래를 띄운다. 예전엔 26 을 박아 뒀는데, 그건 제스처
    // 내비(≈24) 한 종류에만 맞는 값이라 3버튼 내비(≈48) 폰에서 탭 라벨이 가려졌다.
    // `padding` 이 아니라 `viewPadding` 인 이유 — 키보드가 올라오면 `padding.bottom`
    // 은 0 이 된다.
    final systemNav = MediaQuery.viewPaddingOf(context).bottom;
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: Container(
        padding: EdgeInsets.only(
          top: 8,
          bottom: 8 + systemNav,
          left: 10,
          right: 10,
        ),
        decoration: const BoxDecoration(
          color: AppColors.background,
          border: Border(top: BorderSide(color: AppColors.border)),
        ),
        child: Row(
          children: [
            for (var i = 0; i < _branchTabs.length; i++)
              _TabItem(
                label: _branchTabs[i].$1,
                icon: _branchTabs[i].$2,
                active: current == i,
                // 이미 그 탭이면 브랜치 스택을 루트로 되감는다.
                onTap: () =>
                    navigationShell.goBranch(i, initialLocation: i == current),
              ),
          ],
        ),
      ),
    );
  }
}

class _TabItem extends StatelessWidget {
  const _TabItem({
    required this.label,
    required this.icon,
    required this.active,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon,
                size: 20,
                color: active ? AppColors.primary : AppColors.textFaint),
            const SizedBox(height: 5),
            Text(
              label,
              style: TextStyle(
                fontSize: 9.5,
                fontWeight: FontWeight.w700,
                color: active ? AppColors.textPrimary : AppColors.textFaint,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
