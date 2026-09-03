import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../../shared/widgets/soon.dart';
import '../../band/application/band_providers.dart';
import '../../band/data/band_models.dart';
import '../application/home_providers.dart';
import 'widgets/band_switch_sheet.dart';
import 'widgets/home_sections.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bandsAsync = ref.watch(myBandsProvider);

    return Scaffold(
      body: bandsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorState(
          message: '밴드 정보를 불러오지 못했습니다.',
          onRetry: () => ref.invalidate(myBandsProvider),
        ),
        data: (bands) {
          if (bands.isEmpty) {
            // 밴드가 없으면 게이트로. (build 중 네비게이션 금지 → 다음 프레임)
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (context.mounted) context.go(Routes.bandGate);
            });
            return const Center(child: CircularProgressIndicator());
          }
          final band = ref.watch(currentBandProvider)!;
          return _HomeBody(band: band);
        },
      ),
      bottomNavigationBar: const _BottomTabs(),
    );
  }
}

class _HomeBody extends ConsumerWidget {
  const _HomeBody({required this.band});
  final MyBand band;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final membersAsync = ref.watch(bandMembersProvider(band.id));
    final upcomingAsync = ref.watch(upcomingReservationsProvider(band.id));
    final next = ref.watch(nextReservationProvider(band.id));

    return RefreshIndicator(
      color: AppColors.primary,
      backgroundColor: AppColors.surface,
      onRefresh: () async {
        ref.invalidate(myBandsProvider);
        ref.invalidate(bandMembersProvider(band.id));
        ref.invalidate(upcomingReservationsProvider(band.id));
        await Future.wait([
          ref.read(bandMembersProvider(band.id).future),
          ref.read(upcomingReservationsProvider(band.id).future),
        ]);
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 64, 20, 24),
        children: [
          _Header(band: band),
          const SizedBox(height: 18),
          MemberRail(membersAsync: membersAsync),
          const SizedBox(height: 20),
          NextRehearsalCard(reservation: next),
          const SizedBox(height: 12),
          SummaryRow(
            upcomingCount: upcomingAsync.valueOrNull?.length,
          ),
          const SizedBox(height: 22),
          UpcomingList(upcomingAsync: upcomingAsync),
          const SizedBox(height: 12),
        ],
      ),
    );
  }
}

class _Header extends ConsumerWidget {
  const _Header({required this.band});
  final MyBand band;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unread = 0; // 알림 화면 미구현 — 배지는 0.
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('MY BAND',
                  style: AppTypography.display(
                      fontSize: 11,
                      letterSpacing: 3,
                      color: AppColors.primary)),
              const SizedBox(height: 2),
              GestureDetector(
                onTap: () => showBandSwitchSheet(context, ref),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Flexible(
                      child: Text(
                        band.name,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.w900,
                          letterSpacing: -0.5,
                        ),
                      ),
                    ),
                    const SizedBox(width: 7),
                    const Icon(Icons.keyboard_arrow_down,
                        size: 18, color: AppColors.textDim),
                  ],
                ),
              ),
            ],
          ),
        ),
        _IconSquare(
          badgeCount: unread,
          icon: Icons.notifications_none,
          onTap: () => showSoon(context, '알림'),
        ),
        const SizedBox(width: 8),
        _IconSquare(
          label: '멤버',
          onTap: () => showSoon(context, '멤버 관리'),
        ),
      ],
    );
  }
}

class _IconSquare extends StatelessWidget {
  const _IconSquare({
    this.icon,
    this.label,
    this.badgeCount = 0,
    required this.onTap,
  });

  final IconData? icon;
  final String? label;
  final int badgeCount;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: AppColors.surfaceRaised,
              borderRadius: BorderRadius.circular(11),
              border: Border.all(color: AppColors.borderStrong),
            ),
            alignment: Alignment.center,
            child: icon != null
                ? Icon(icon, size: 18, color: AppColors.textSecondary)
                : Text(label ?? '',
                    style: const TextStyle(
                        fontSize: 11, color: AppColors.textSecondary)),
          ),
          if (badgeCount > 0)
            Positioned(
              top: -3,
              right: -3,
              child: Container(
                constraints: const BoxConstraints(minWidth: 17),
                height: 17,
                padding: const EdgeInsets.symmetric(horizontal: 4),
                decoration: BoxDecoration(
                  color: AppColors.primary,
                  borderRadius: BorderRadius.circular(99),
                ),
                alignment: Alignment.center,
                child: Text(
                  '$badgeCount',
                  style: AppTypography.mono(
                      fontSize: 10, color: AppColors.onPrimary),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _BottomTabs extends StatelessWidget {
  const _BottomTabs();

  @override
  Widget build(BuildContext context) {
    const tabs = [
      ('홈', Icons.home_filled, true),
      ('캘린더', Icons.calendar_today, false),
      ('지도', Icons.map_outlined, false),
      ('정산', Icons.receipt_long, false),
      ('게시판', Icons.grid_view, false),
    ];
    return Container(
      padding: const EdgeInsets.only(top: 8, bottom: 26, left: 10, right: 10),
      decoration: const BoxDecoration(
        color: AppColors.background,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: Row(
        children: [
          for (final (label, icon, active) in tabs)
            Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: active ? null : () => showSoon(context, label),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(icon,
                        size: 20,
                        color: active
                            ? AppColors.primary
                            : AppColors.textFaint),
                    const SizedBox(height: 5),
                    Text(
                      label,
                      style: TextStyle(
                        fontSize: 9.5,
                        fontWeight: FontWeight.w700,
                        color: active
                            ? AppColors.textPrimary
                            : AppColors.textFaint,
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, style: const TextStyle(color: AppColors.textDim)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    );
  }
}

