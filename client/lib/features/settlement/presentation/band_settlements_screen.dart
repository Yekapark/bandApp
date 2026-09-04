import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../band/application/band_providers.dart';
import '../application/settlement_providers.dart';
import '../data/settlement_models.dart';
import '../data/settlement_repository.dart';

/// 밴드 정산 목록 — 하단 탭 "정산".
///
/// 정산 자체는 일정에 매달린 개념이라 만들고 고치는 건 일정 상세에서 한다. 이 화면은
/// **내가 아직 안 낸 돈**을 한눈에 보기 위한 읽기 전용 목록이고, 한 건을 누르면 그 일정의
/// 정산 화면으로 넘어간다.
class BandSettlementsScreen extends ConsumerStatefulWidget {
  const BandSettlementsScreen({super.key});

  @override
  ConsumerState<BandSettlementsScreen> createState() =>
      _BandSettlementsScreenState();
}

class _BandSettlementsScreenState extends ConsumerState<BandSettlementsScreen> {
  final List<BandSettlementItem> _more = [];
  int? _cursor;
  bool _loadingMore = false;
  bool _exhausted = false;

  /// 이어 붙인 페이지들의 미납 합계. 첫 페이지 합계와 더해 화면 상단에 보여준다.
  int _moreOutstanding = 0;

  Future<void> _loadMore(int bandId) async {
    if (_loadingMore || _exhausted || _cursor == null) return;
    setState(() => _loadingMore = true);
    try {
      final page = await ref
          .read(settlementRepositoryProvider)
          .listForBand(bandId: bandId, cursor: _cursor);
      if (!mounted) return;
      setState(() {
        _more.addAll(page.items);
        _moreOutstanding += page.myOutstandingTotal;
        _cursor = page.nextCursor;
        _exhausted = page.nextCursor == null;
      });
    } catch (_) {
      // 더 못 불러오면 조용히 멈춘다 — 이미 보여 준 목록은 그대로 쓴다.
      if (mounted) setState(() => _exhausted = true);
    } finally {
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  void _reset() {
    _more.clear();
    _moreOutstanding = 0;
    _cursor = null;
    _exhausted = false;
  }

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text('밴드를 먼저 선택해 주세요.',
              style: TextStyle(color: AppColors.textDim)),
        ),
      );
    }

    final pageAsync = ref.watch(bandSettlementsProvider(band.id));

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '정산',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
        ),
      ),
      body: pageAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Text(
            e is ApiException ? e.message : '정산을 불러오지 못했습니다.',
            style: const TextStyle(color: AppColors.textDim),
          ),
        ),
        data: (page) {
          _cursor ??= page.nextCursor;
          if (page.nextCursor == null && _more.isEmpty) _exhausted = true;

          final items = [...page.items, ..._more];
          final outstanding = page.myOutstandingTotal + _moreOutstanding;

          return RefreshIndicator(
            color: AppColors.primary,
            backgroundColor: AppColors.surface,
            onRefresh: () async {
              setState(_reset);
              ref.invalidate(bandSettlementsProvider(band.id));
            },
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 28),
              children: [
                _OutstandingCard(amount: outstanding, hasAny: items.isNotEmpty),
                const SizedBox(height: 14),
                if (items.isEmpty)
                  const _Empty()
                else ...[
                  for (final s in items) ...[
                    _SettlementTile(
                      item: s,
                      onTap: () => context.push(
                        '/reservations/${s.reservationId}/settlement',
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                  if (!_exhausted)
                    Builder(builder: (_) {
                      _loadMore(band.id);
                      return const Padding(
                        padding: EdgeInsets.symmetric(vertical: 16),
                        child: Center(
                          child: SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        ),
                      );
                    }),
                ],
              ],
            ),
          );
        },
      ),
    );
  }
}

/// 상단 요약 — 이 앱에서 돈은 가장 예민한 정보라, 제일 먼저 보이게 둔다.
class _OutstandingCard extends StatelessWidget {
  const _OutstandingCard({required this.amount, required this.hasAny});

  final int amount;
  final bool hasAny;

  @override
  Widget build(BuildContext context) {
    final settled = amount == 0;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
      decoration: BoxDecoration(
        color: AppColors.surfaceRaised,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: settled
              ? AppColors.borderFaint
              : AppColors.primary.withValues(alpha: 0.5),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '내가 아직 안 낸 돈',
            style: TextStyle(fontSize: 11.5, color: AppColors.textDim),
          ),
          const SizedBox(height: 6),
          Text(
            settled
                ? (hasAny ? '없어요' : '정산 없음')
                : '${_won.format(amount)}원',
            style: TextStyle(
              fontSize: 26,
              fontWeight: FontWeight.w900,
              letterSpacing: -0.5,
              color: settled ? AppColors.textDim : AppColors.primary,
            ),
          ),
        ],
      ),
    );
  }
}

class _SettlementTile extends StatelessWidget {
  const _SettlementTile({required this.item, required this.onTap});

  final BandSettlementItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        decoration: BoxDecoration(
          color: AppColors.surfaceRaised,
          borderRadius: BorderRadius.circular(13),
          border: Border.all(
            color: item.iStillOwe
                ? AppColors.primary.withValues(alpha: 0.45)
                : AppColors.borderFaint,
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    DateFormat('M월 d일 (E) HH:mm', 'ko').format(item.startAt),
                    style: const TextStyle(
                        fontSize: 13, fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    item.roomName ?? '합주실 정보 없음',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        fontSize: 11.5, color: AppColors.textDim),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '총 ${_won.format(item.totalAmount)}원 · '
                    '${item.paidCount}/${item.shareCount}명 납부',
                    style: const TextStyle(
                        fontSize: 10.5, color: AppColors.textFaint),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 10),
            _MyShare(item: item),
          ],
        ),
      ),
    );
  }
}

/// 오른쪽의 내 몫 표시. 분담 대상이 아니면 금액 대신 그 사실을 적는다.
class _MyShare extends StatelessWidget {
  const _MyShare({required this.item});

  final BandSettlementItem item;

  @override
  Widget build(BuildContext context) {
    if (!item.isMine) {
      return const Text(
        '대상 아님',
        style: TextStyle(fontSize: 10.5, color: AppColors.textFaint),
      );
    }
    final paid = item.myPaid == true;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Text(
          '${_won.format(item.myAmount!)}원',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w800,
            color: paid ? AppColors.textDim : AppColors.primary,
          ),
        ),
        const SizedBox(height: 4),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
          decoration: BoxDecoration(
            color: paid
                ? AppColors.surfaceAlt
                : AppColors.primary.withValues(alpha: 0.16),
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(
            paid ? '납부함' : '미납',
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w700,
              color: paid ? AppColors.textFaint : AppColors.primary,
            ),
          ),
        ),
      ],
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(horizontal: 24, vertical: 40),
      child: Text(
        '아직 정산이 없어요.\n합주 일정 상세에서 정산을 만들면 여기에 모입니다.',
        textAlign: TextAlign.center,
        style:
            TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.6),
      ),
    );
  }
}

final _won = NumberFormat('#,###');
