import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../band/application/band_providers.dart';
import '../application/notification_providers.dart';
import '../data/notification_models.dart';
import '../data/notification_repository.dart';
import '../data/notification_seen_storage.dart';

/// 받은 알림 목록.
///
/// 화면을 열면 그 시각을 "마지막 확인"으로 기기에 저장한다 — 홈의 배지는 그보다 새 알림만 센다.
/// 알림을 누르면 가리키는 일정 상세로 간다.
class NotificationListScreen extends ConsumerStatefulWidget {
  const NotificationListScreen({super.key});

  @override
  ConsumerState<NotificationListScreen> createState() =>
      _NotificationListScreenState();
}

class _NotificationListScreenState
    extends ConsumerState<NotificationListScreen> {
  /// 첫 페이지 뒤로 이어 붙인 것들.
  final List<AppNotification> _more = [];
  int? _cursor;
  bool _loadingMore = false;
  bool _exhausted = false;

  /// 이 화면을 열기 <b>직전</b>의 확인 시각. 방금 갱신한 값을 쓰면 "새 알림" 표시가 즉시
  /// 사라지므로, 화면에는 열기 전 값을 그대로 쓴다.
  DateTime? _seenBefore;
  bool _marked = false;

  Future<void> _markSeen(int bandId) async {
    if (_marked) return;
    _marked = true;
    final storage = ref.read(notificationSeenStorageProvider);
    _seenBefore = await storage.lastSeen(bandId);
    await storage.markSeen(bandId, DateTime.now());
    if (!mounted) return;
    setState(() {});
    // 배지는 다음에 다시 셀 때 0 이 된다.
    ref.invalidate(unreadNotificationCountProvider(bandId));
  }

  Future<void> _loadMore(int bandId) async {
    if (_loadingMore || _exhausted || _cursor == null) return;
    setState(() => _loadingMore = true);
    try {
      final page = await ref
          .read(notificationRepositoryProvider)
          .feed(bandId: bandId, cursor: _cursor);
      if (!mounted) return;
      setState(() {
        _more.addAll(page.items);
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

    final feedAsync = ref.watch(notificationFeedProvider(band.id));

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '알림',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
        ),
      ),
      body: feedAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Text(
            e is ApiException ? e.message : '알림을 불러오지 못했습니다.',
            style: const TextStyle(color: AppColors.textDim),
          ),
        ),
        data: (page) {
          _cursor ??= page.nextCursor;
          if (page.nextCursor == null && _more.isEmpty) _exhausted = true;
          WidgetsBinding.instance
              .addPostFrameCallback((_) => _markSeen(band.id));

          final items = [...page.items, ..._more];
          if (items.isEmpty) return const _Empty();

          return RefreshIndicator(
            color: AppColors.primary,
            backgroundColor: AppColors.surface,
            onRefresh: () async {
              setState(() {
                _more.clear();
                _cursor = null;
                _exhausted = false;
              });
              ref.invalidate(notificationFeedProvider(band.id));
            },
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
              itemCount: items.length + (_exhausted ? 0 : 1),
              separatorBuilder: (_, __) => const SizedBox(height: 8),
              itemBuilder: (_, i) {
                if (i == items.length) {
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
                }
                final n = items[i];
                return _NotificationTile(
                  item: n,
                  isNew: _seenBefore == null || n.sentAt.isAfter(_seenBefore!),
                  onTap: () => context.push('/reservations/${n.reservationId}'),
                );
              },
            ),
          );
        },
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({
    required this.item,
    required this.isNew,
    required this.onTap,
  });

  final AppNotification item;
  final bool isNew;
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
            color: isNew
                ? AppColors.primary.withValues(alpha: 0.5)
                : AppColors.borderFaint,
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(10),
              ),
              alignment: Alignment.center,
              child:
                  Icon(_iconFor(item.type), size: 16, color: AppColors.primary),
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
                          item.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13.5,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      if (isNew) ...[
                        const SizedBox(width: 6),
                        Container(
                          width: 6,
                          height: 6,
                          decoration: const BoxDecoration(
                            color: AppColors.primary,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 3),
                  Text(
                    item.body,
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.textDim,
                      height: 1.45,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    DateFormat('M월 d일 HH:mm', 'ko').format(item.sentAt),
                    style: const TextStyle(
                      fontSize: 10.5,
                      color: AppColors.textFaint,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  static IconData _iconFor(String type) {
    if (type.startsWith('SETTLEMENT')) return Icons.receipt_long_outlined;
    if (type.contains('REMINDER')) return Icons.alarm;
    if (type.contains('ATTENDANCE')) return Icons.how_to_reg_outlined;
    if (type.contains('CANCELLED') || type.contains('REJECTED')) {
      return Icons.event_busy_outlined;
    }
    return Icons.event_available_outlined;
  }
}

class _Empty extends StatelessWidget {
  const _Empty();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: 32),
        child: Text(
          '받은 알림이 없어요.\n합주 일정이 등록되거나 정산이 올라오면 여기에 쌓입니다.',
          textAlign: TextAlign.center,
          style:
              TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.6),
        ),
      ),
    );
  }
}
