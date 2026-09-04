import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../auth/application/auth_controller.dart';
import '../../band/application/band_providers.dart';
import '../../home/application/home_providers.dart';
import '../application/calendar_providers.dart';
import '../data/reservation_models.dart';
import '../data/reservation_repository.dart';

/// 일정 상세 — 정보 · 내 참석 체크(RSVP) · 멤버별 참석 현황 · 셋리스트.
class ReservationDetailScreen extends ConsumerStatefulWidget {
  const ReservationDetailScreen({super.key, required this.reservationId});

  final int reservationId;

  @override
  ConsumerState<ReservationDetailScreen> createState() =>
      _ReservationDetailScreenState();
}

class _ReservationDetailScreenState
    extends ConsumerState<ReservationDetailScreen> {
  /// 참석 응답 직후엔 서버 재조회 없이 이 값으로 화면을 갱신한다.
  AttendanceBoard? _boardOverride;
  bool _savingRsvp = false;
  bool _busy = false;

  ReservationKey _key(int bandId) =>
      (bandId: bandId, reservationId: widget.reservationId);

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    final meId = ref.watch(authControllerProvider).user?.id;

    if (band == null) {
      return const Scaffold(
        body: Center(
          child: Text(
            '밴드를 먼저 선택해 주세요.',
            style: TextStyle(color: AppColors.textDim),
          ),
        ),
      );
    }

    final key = _key(band.id);
    final detailAsync = ref.watch(reservationDetailProvider(key));

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '합주 상세',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
        ),
      ),
      body: detailAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorBody(
          message: e is ApiException ? e.message : '일정을 불러오지 못했습니다.',
          onRetry: () => ref.invalidate(reservationDetailProvider(key)),
        ),
        data: (detail) {
          final r = detail.reservation;
          final board = _boardOverride ?? detail.attendance;
          final editable = r.isActive;
          final canManage =
              band.isLeader || (meId != null && r.requestedBy == meId);

          return RefreshIndicator(
            color: AppColors.primary,
            backgroundColor: AppColors.surface,
            onRefresh: () async {
              _boardOverride = null;
              ref.invalidate(reservationDetailProvider(key));
              await ref.read(reservationDetailProvider(key).future);
            },
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
              children: [
                _StatusBanner(status: r.status),
                const SizedBox(height: 14),
                Text(
                  r.roomName,
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w900,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 12),
                _InfoRow(startAt: r.startAt, endAt: r.endAt, cost: r.cost),
                if ((r.note ?? '').trim().isNotEmpty) ...[
                  const SizedBox(height: 16),
                  _NoteCard(note: r.note!.trim()),
                ],
                const SizedBox(height: 22),
                const _SectionTitle('내 참석 여부'),
                const SizedBox(height: 10),
                _RsvpButtons(
                  current: meId == null
                      ? AttendanceStatus.pending
                      : board.statusOf(meId),
                  enabled: editable && meId != null && !_savingRsvp,
                  onSelect: (s) => _respond(band.id, meId!, s),
                ),
                if (!editable)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: Text(
                      '취소·거절된 일정은 참석 체크를 할 수 없어요.',
                      style: TextStyle(
                        fontSize: 11,
                        color: AppColors.textFaint,
                      ),
                    ),
                  ),
                const SizedBox(height: 24),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    const _SectionTitle('이번 합주 셋리스트'),
                    const SizedBox(width: 9),
                    Text(
                      '${detail.setlist.count}곡',
                      style: const TextStyle(
                        fontSize: 11.5,
                        color: AppColors.textDim,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                _SetlistBlock(
                  items: detail.setlist.items,
                  editable: editable,
                  onAdd: () => _addSong(band.id),
                  onEdit: (item) => _editSong(band.id, item),
                  onDelete: (item) => _deleteSong(band.id, item),
                  onReorder: (ids) => _reorderSetlist(band.id, ids),
                ),
                const SizedBox(height: 24),
                Row(
                  children: [
                    const _SectionTitle('멤버별 참석 현황'),
                    const Spacer(),
                    Text(
                      '참석 ${board.attendingCount} · 불참 ${board.absentCount} · '
                      '미정 ${board.pendingCount}',
                      style: const TextStyle(
                        fontSize: 10.5,
                        color: AppColors.textFaint,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                _AttendanceList(entries: board.members),
                const SizedBox(height: 24),
                _SettlementLink(
                  onTap: () => context.push(Routes.settlement(r.id)),
                ),
                if (r.isPending && band.isLeader) ...[
                  const SizedBox(height: 16),
                  const _SectionTitle('밴드장 승인'),
                  const SizedBox(height: 10),
                  _ApproveRejectRow(
                    busy: _busy,
                    onApprove: () => _decide(band.id, approve: true),
                    onReject: () => _decide(band.id, approve: false),
                  ),
                ],
                if (canManage && editable) ...[
                  const SizedBox(height: 12),
                  _EditButton(
                    onTap: () async {
                      await context.push(Routes.editReservation(r.id),
                          extra: r);
                      if (!context.mounted) return;
                      _boardOverride = null;
                      ref.invalidate(reservationDetailProvider(key));
                    },
                  ),
                  const SizedBox(height: 12),
                  _CancelButton(
                    busy: _busy,
                    onTap: () => _confirmCancel(band.id),
                  ),
                ],
              ],
            ),
          );
        },
      ),
    );
  }

  Future<void> _respond(int bandId, int meId, AttendanceStatus status) async {
    setState(() => _savingRsvp = true);
    try {
      final board =
          await ref.read(reservationRepositoryProvider).respondAttendance(
                bandId: bandId,
                reservationId: widget.reservationId,
                userId: meId,
                status: status,
              );
      if (!mounted) return;
      setState(() => _boardOverride = board);
      // 로컬 갱신만으로는 부족하다. reservationDetailProvider 는 autoDispose 가 아니라
      // 캐시가 계속 남아서, 화면을 벗어났다 돌아오면(_boardOverride 가 사라진 뒤) 응답 전의
      // 옛 값이 다시 그려진다. 캐시도 함께 무효화한다 — 이미 데이터가 있는 상태의 갱신이라
      // 로딩 스피너로 깜빡이지 않는다.
      ref.invalidate(reservationDetailProvider(_key(bandId)));
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('참석 상태를 바꾸지 못했습니다.');
    } finally {
      if (mounted) setState(() => _savingRsvp = false);
    }
  }

  Future<void> _addSong(int bandId) async {
    final song = await showDialog<_SongInput>(
      context: context,
      builder: (_) => const _SongDialog(),
    );
    if (song == null) return;
    setState(() => _busy = true);
    try {
      await ref.read(reservationRepositoryProvider).addSetlistItem(
            bandId: bandId,
            reservationId: widget.reservationId,
            title: song.title,
            artist: song.artist,
            referenceUrl: song.referenceUrl,
          );
      ref.invalidate(reservationDetailProvider(_key(bandId)));
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('곡을 추가하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _editSong(int bandId, SetlistItem item) async {
    final song = await showDialog<_SongInput>(
      context: context,
      builder: (_) => _SongDialog(initial: item),
    );
    if (song == null) return;
    setState(() => _busy = true);
    try {
      await ref.read(reservationRepositoryProvider).updateSetlistItem(
            bandId: bandId,
            reservationId: widget.reservationId,
            itemId: item.id,
            title: song.title,
            artist: song.artist,
            referenceUrl: song.referenceUrl,
          );
      ref.invalidate(reservationDetailProvider(_key(bandId)));
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('곡을 수정하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reorderSetlist(int bandId, List<int> itemIds) async {
    try {
      await ref.read(reservationRepositoryProvider).reorderSetlist(
            bandId: bandId,
            reservationId: widget.reservationId,
            itemIds: itemIds,
          );
      ref.invalidate(reservationDetailProvider(_key(bandId)));
    } on ApiException catch (e) {
      _toast(e.message);
      ref.invalidate(reservationDetailProvider(_key(bandId)));
    } catch (_) {
      _toast('순서를 바꾸지 못했습니다.');
      ref.invalidate(reservationDetailProvider(_key(bandId)));
    }
  }

  Future<void> _deleteSong(int bandId, SetlistItem item) async {
    setState(() => _busy = true);
    try {
      await ref.read(reservationRepositoryProvider).deleteSetlistItem(
            bandId: bandId,
            reservationId: widget.reservationId,
            itemId: item.id,
          );
      ref.invalidate(reservationDetailProvider(_key(bandId)));
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('곡을 삭제하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _confirmCancel(int bandId) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('이 합주를 취소할까요?', style: TextStyle(fontSize: 16)),
        content: const Text(
          '합주실에는 직접 취소 연락을 한 뒤 정리하세요. '
          '기록은 남아 과거 정산에서 참조할 수 있어요.',
          style:
              TextStyle(fontSize: 12.5, color: AppColors.textDim, height: 1.5),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('그대로 두기'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child:
                const Text('취소하기', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok != true) return;

    setState(() => _busy = true);
    try {
      await ref.read(reservationRepositoryProvider).cancel(
            bandId: bandId,
            reservationId: widget.reservationId,
          );
      ref.invalidate(monthReservationsProvider);
      ref.invalidate(upcomingReservationsProvider(bandId));
      ref.invalidate(reservationDetailProvider(_key(bandId)));
      if (mounted) {
        _toast('합주를 취소했어요.');
        context.pop();
      }
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('취소하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _decide(int bandId, {required bool approve}) async {
    if (!approve) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          backgroundColor: AppColors.surface,
          title: const Text('이 일정을 거절할까요?', style: TextStyle(fontSize: 16)),
          content: const Text(
            '거절하면 등록 시 잡았던 합주실 사용 횟수가 되돌아가고, 등록자에게 알림이 갑니다.',
            style: TextStyle(
                fontSize: 12.5, color: AppColors.textDim, height: 1.5),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('그대로 두기'),
            ),
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child:
                  const Text('거절하기', style: TextStyle(color: AppColors.danger)),
            ),
          ],
        ),
      );
      if (ok != true) return;
    }

    setState(() => _busy = true);
    try {
      final repo = ref.read(reservationRepositoryProvider);
      if (approve) {
        await repo.approve(bandId: bandId, reservationId: widget.reservationId);
      } else {
        await repo.reject(bandId: bandId, reservationId: widget.reservationId);
      }
      _boardOverride = null;
      ref.invalidate(monthReservationsProvider);
      ref.invalidate(upcomingReservationsProvider(bandId));
      ref.invalidate(reservationDetailProvider(_key(bandId)));
      _toast(approve ? '일정을 확정했어요.' : '일정을 거절했어요.');
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast(approve ? '승인하지 못했습니다.' : '거절하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _ApproveRejectRow extends StatelessWidget {
  const _ApproveRejectRow({
    required this.busy,
    required this.onApprove,
    required this.onReject,
  });

  final bool busy;
  final VoidCallback onApprove;
  final VoidCallback onReject;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: PrimaryButton(
            label: '승인',
            loading: busy,
            onPressed: onApprove,
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: OutlinedButton(
            onPressed: busy ? null : onReject,
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(54),
              side: const BorderSide(color: AppColors.danger),
              foregroundColor: AppColors.danger,
            ),
            child: const Text('거절'),
          ),
        ),
      ],
    );
  }
}

class _EditButton extends StatelessWidget {
  const _EditButton({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onTap,
      icon: const Icon(Icons.edit_outlined, size: 16),
      style: OutlinedButton.styleFrom(
        minimumSize: const Size.fromHeight(50),
        side: const BorderSide(color: AppColors.borderStrong),
        foregroundColor: AppColors.textSecondary,
      ),
      label: const Text('일정 수정'),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Text(
        text,
        style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700),
      );
}

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.status});
  final ReservationStatus status;

  @override
  Widget build(BuildContext context) {
    late final Color bg;
    late final Color fg;
    late final String text;
    switch (status) {
      case ReservationStatus.pending:
        bg = AppColors.purple.withValues(alpha: 0.1);
        fg = AppColors.purpleSoft;
        text = '밴드장 확인 대기 중 · 합주실 예약은 완료';
        break;
      case ReservationStatus.confirmed:
        bg = AppColors.primary.withValues(alpha: 0.1);
        fg = AppColors.primarySoft;
        text = '확정된 합주';
        break;
      case ReservationStatus.cancelled:
        bg = AppColors.surfaceAlt;
        fg = AppColors.textDim;
        text = '취소된 합주';
        break;
      case ReservationStatus.rejected:
        bg = AppColors.surfaceAlt;
        fg = AppColors.textDim;
        text = '거절된 합주';
        break;
      case ReservationStatus.unknown:
        bg = AppColors.surfaceAlt;
        fg = AppColors.textDim;
        text = '-';
        break;
    }
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        text,
        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: fg),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.startAt, required this.endAt, this.cost});
  final DateTime startAt;
  final DateTime endAt;
  final int? cost;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _InfoCol(label: '일시', value: Fmt.dateTimeKo(startAt)),
        const SizedBox(width: 22),
        _InfoCol(label: '시간', value: Fmt.durationKo(startAt, endAt)),
        if (cost != null) ...[
          const SizedBox(width: 22),
          _InfoCol(
            label: '총 비용',
            value: Fmt.won(cost),
            valueColor: AppColors.primary,
          ),
        ],
      ],
    );
  }
}

class _InfoCol extends StatelessWidget {
  const _InfoCol({
    required this.label,
    required this.value,
    this.valueColor = AppColors.textPrimary,
  });
  final String label;
  final String value;
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 10.5, color: AppColors.textFaint),
        ),
        const SizedBox(height: 4),
        Text(value, style: AppTypography.mono(fontSize: 13, color: valueColor)),
      ],
    );
  }
}

class _NoteCard extends StatelessWidget {
  const _NoteCard({required this.note});
  final String note;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(15),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.borderFaint),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '예약 메모',
            style: TextStyle(fontSize: 11, color: AppColors.textDim),
          ),
          const SizedBox(height: 6),
          Text(note, style: const TextStyle(fontSize: 13, height: 1.6)),
        ],
      ),
    );
  }
}

class _RsvpButtons extends StatelessWidget {
  const _RsvpButtons({
    required this.current,
    required this.enabled,
    required this.onSelect,
  });

  final AttendanceStatus current;
  final bool enabled;
  final ValueChanged<AttendanceStatus> onSelect;

  @override
  Widget build(BuildContext context) {
    const options = [
      AttendanceStatus.attending,
      AttendanceStatus.absent,
      AttendanceStatus.pending,
    ];
    return Row(
      children: [
        for (final o in options) ...[
          Expanded(
            child: GestureDetector(
              onTap: enabled ? () => onSelect(o) : null,
              child: Container(
                height: 46,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: o == current ? AppColors.primary : AppColors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: o == current
                        ? AppColors.primary
                        : AppColors.borderStrong,
                  ),
                ),
                child: Text(
                  attendanceStatusLabel(o),
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: o == current
                        ? AppColors.onPrimary
                        : (enabled
                            ? AppColors.textSecondary
                            : AppColors.textFaint),
                  ),
                ),
              ),
            ),
          ),
          if (o != options.last) const SizedBox(width: 7),
        ],
      ],
    );
  }
}

class _SetlistBlock extends StatefulWidget {
  const _SetlistBlock({
    required this.items,
    required this.editable,
    required this.onAdd,
    required this.onEdit,
    required this.onDelete,
    required this.onReorder,
  });

  final List<SetlistItem> items;
  final bool editable;
  final VoidCallback onAdd;
  final ValueChanged<SetlistItem> onEdit;
  final ValueChanged<SetlistItem> onDelete;

  /// 새 순서의 항목 id 목록.
  final ValueChanged<List<int>> onReorder;

  @override
  State<_SetlistBlock> createState() => _SetlistBlockState();
}

class _SetlistBlockState extends State<_SetlistBlock> {
  late List<SetlistItem> _local = List.of(widget.items);

  @override
  void didUpdateWidget(_SetlistBlock old) {
    super.didUpdateWidget(old);
    final incoming = widget.items.map((e) => e.id).toList();
    final current = _local.map((e) => e.id).toList();
    if (!_sameOrder(incoming, current)) {
      _local = List.of(widget.items);
    }
  }

  bool _sameOrder(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  /// [newIndex] 는 oldIndex 항목이 제거된 뒤 기준으로 이미 보정된 값이다(onReorderItem).
  void _onReorderItem(int oldIndex, int newIndex) {
    setState(() {
      final moved = _local.removeAt(oldIndex);
      _local.insert(newIndex, moved);
    });
    widget.onReorder(_local.map((e) => e.id).toList());
  }

  Widget _row(SetlistItem item, int index, {required bool draggable}) {
    return Container(
      key: ValueKey(item.id),
      margin: const EdgeInsets.only(bottom: 7),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(13),
        border: Border.all(color: AppColors.borderFaint),
      ),
      child: Row(
        children: [
          Text(
            '${index + 1}',
            style: AppTypography.mono(fontSize: 11, color: AppColors.textFaint),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: widget.editable ? () => widget.onEdit(item) : null,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        fontSize: 13.5, fontWeight: FontWeight.w600),
                  ),
                  if ((item.artist ?? '').trim().isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      item.artist!.trim(),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontSize: 11, color: AppColors.textDim),
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (widget.editable) ...[
            GestureDetector(
              onTap: () => widget.onDelete(item),
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 6),
                child: Icon(Icons.close, size: 16, color: AppColors.textFaint),
              ),
            ),
            if (draggable)
              ReorderableDragStartListener(
                index: index,
                child: const Padding(
                  padding: EdgeInsets.only(left: 2),
                  child: Icon(Icons.drag_handle,
                      size: 18, color: AppColors.textFaint),
                ),
              ),
          ],
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final canReorder = widget.editable && _local.length > 1;

    return Column(
      children: [
        if (canReorder)
          ReorderableListView(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            buildDefaultDragHandles: false,
            onReorderItem: _onReorderItem,
            children: [
              for (var i = 0; i < _local.length; i++)
                _row(_local[i], i, draggable: true),
            ],
          )
        else
          for (var i = 0; i < _local.length; i++)
            _row(_local[i], i, draggable: false),
        if (widget.editable)
          GestureDetector(
            onTap: widget.onAdd,
            child: Container(
              height: 46,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(13),
                border:
                    Border.all(color: AppColors.primary.withValues(alpha: 0.4)),
              ),
              child: const Text(
                '＋ 곡 추가',
                style: TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
            ),
          ),
        if (!widget.editable && _local.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8),
            child: Text(
              '등록된 곡이 없어요.',
              style: TextStyle(fontSize: 12, color: AppColors.textDim),
            ),
          ),
      ],
    );
  }
}

class _AttendanceList extends StatelessWidget {
  const _AttendanceList({required this.entries});
  final List<AttendanceEntry> entries;

  Color _fg(AttendanceStatus s) {
    switch (s) {
      case AttendanceStatus.attending:
        return AppColors.success;
      case AttendanceStatus.absent:
        return AppColors.danger;
      case AttendanceStatus.pending:
      case AttendanceStatus.unknown:
        return AppColors.textFaint;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (entries.isEmpty) {
      return const Text(
        '멤버 정보가 없어요.',
        style: TextStyle(fontSize: 12, color: AppColors.textDim),
      );
    }
    return Column(
      children: [
        for (final e in entries)
          Padding(
            padding: const EdgeInsets.only(bottom: 7),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(13),
              ),
              child: Row(
                children: [
                  Container(
                    width: 30,
                    height: 30,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.surfaceAlt,
                    ),
                    alignment: Alignment.center,
                    child: Text(
                      e.name.isEmpty ? '?' : e.name.characters.first,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: e.isLeader
                            ? AppColors.primary
                            : AppColors.textPrimary,
                      ),
                    ),
                  ),
                  const SizedBox(width: 11),
                  Expanded(
                    child: Text(
                      e.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                  Text(
                    attendanceStatusLabel(e.status),
                    style: TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w700,
                      color: _fg(e.status),
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _SettlementLink extends StatelessWidget {
  const _SettlementLink({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 52,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        decoration: BoxDecoration(
          color: AppColors.purple.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(13),
          border: Border.all(color: AppColors.purple.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            const Expanded(
              child: Text(
                '정산 (N빵) 보기 · 만들기',
                style: TextStyle(
                  fontSize: 13.5,
                  fontWeight: FontWeight.w700,
                  color: AppColors.purpleSoft,
                ),
              ),
            ),
            const Icon(Icons.chevron_right,
                size: 18, color: AppColors.purpleSoft),
          ],
        ),
      ),
    );
  }
}

class _CancelButton extends StatelessWidget {
  const _CancelButton({required this.busy, required this.onTap});
  final bool busy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: busy ? null : onTap,
      child: Container(
        height: 50,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: AppColors.surfaceAlt,
          borderRadius: BorderRadius.circular(13),
        ),
        child: busy
            ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Text(
                '이 합주 변경 · 취소',
                style: TextStyle(
                  fontSize: 13.5,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textSecondary,
                ),
              ),
      ),
    );
  }
}

class _ErrorBody extends StatelessWidget {
  const _ErrorBody({required this.message, required this.onRetry});
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

// ── 곡 추가/수정 다이얼로그 ────────────────────────────────────────────

class _SongInput {
  const _SongInput({required this.title, this.artist, this.referenceUrl});
  final String title;
  final String? artist;
  final String? referenceUrl;
}

class _SongDialog extends StatefulWidget {
  const _SongDialog({this.initial});

  /// null 이면 추가, 있으면 수정.
  final SetlistItem? initial;

  @override
  State<_SongDialog> createState() => _SongDialogState();
}

class _SongDialogState extends State<_SongDialog> {
  late final _title = TextEditingController(text: widget.initial?.title ?? '');
  late final _artist =
      TextEditingController(text: widget.initial?.artist ?? '');
  late final _ref =
      TextEditingController(text: widget.initial?.referenceUrl ?? '');

  bool get _isEdit => widget.initial != null;

  @override
  void dispose() {
    _title.dispose();
    _artist.dispose();
    _ref.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.surface,
      title:
          Text(_isEdit ? '곡 수정' : '곡 추가', style: const TextStyle(fontSize: 16)),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _title,
            autofocus: true,
            maxLength: 200,
            decoration: const InputDecoration(hintText: '곡명', counterText: ''),
          ),
          const SizedBox(height: 10),
          TextField(
            controller: _artist,
            maxLength: 200,
            decoration:
                const InputDecoration(hintText: '아티스트 (선택)', counterText: ''),
          ),
          const SizedBox(height: 10),
          TextField(
            controller: _ref,
            maxLength: 2000,
            keyboardType: TextInputType.url,
            decoration: const InputDecoration(
                hintText: '참고 링크 (선택, 유튜브 등)', counterText: ''),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('취소'),
        ),
        TextButton(
          onPressed: () {
            final t = _title.text.trim();
            if (t.isEmpty) return;
            Navigator.of(context).pop(
              _SongInput(
                title: t,
                artist: _artist.text.trim(),
                referenceUrl: _ref.text.trim(),
              ),
            );
          },
          child: Text(_isEdit ? '저장' : '추가'),
        ),
      ],
    );
  }
}
