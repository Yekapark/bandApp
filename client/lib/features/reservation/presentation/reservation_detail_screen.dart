import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
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
                  onDelete: (item) => _deleteSong(band.id, item),
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
                if (canManage && editable) ...[
                  const SizedBox(height: 24),
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
      if (mounted) setState(() => _boardOverride = board);
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
      builder: (_) => const _AddSongDialog(),
    );
    if (song == null) return;
    setState(() => _busy = true);
    try {
      await ref.read(reservationRepositoryProvider).addSetlistItem(
            bandId: bandId,
            reservationId: widget.reservationId,
            title: song.title,
            artist: song.artist,
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

  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
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

class _SetlistBlock extends StatelessWidget {
  const _SetlistBlock({
    required this.items,
    required this.editable,
    required this.onAdd,
    required this.onDelete,
  });

  final List<SetlistItem> items;
  final bool editable;
  final VoidCallback onAdd;
  final ValueChanged<SetlistItem> onDelete;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (var i = 0; i < items.length; i++)
          Padding(
            padding: const EdgeInsets.only(bottom: 7),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(13),
                border: Border.all(color: AppColors.borderFaint),
              ),
              child: Row(
                children: [
                  Text(
                    '${i + 1}',
                    style: AppTypography.mono(
                      fontSize: 11,
                      color: AppColors.textFaint,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          items[i].title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        if ((items[i].artist ?? '').trim().isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(
                            items[i].artist!.trim(),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.textDim,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  if (editable)
                    GestureDetector(
                      onTap: () => onDelete(items[i]),
                      child: const Padding(
                        padding: EdgeInsets.only(left: 8),
                        child: Icon(
                          Icons.close,
                          size: 16,
                          color: AppColors.textFaint,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        if (editable)
          GestureDetector(
            onTap: onAdd,
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
        if (!editable && items.isEmpty)
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

// ── 곡 추가 다이얼로그 ──────────────────────────────────────────────────

class _SongInput {
  const _SongInput({required this.title, this.artist});
  final String title;
  final String? artist;
}

class _AddSongDialog extends StatefulWidget {
  const _AddSongDialog();

  @override
  State<_AddSongDialog> createState() => _AddSongDialogState();
}

class _AddSongDialogState extends State<_AddSongDialog> {
  final _title = TextEditingController();
  final _artist = TextEditingController();

  @override
  void dispose() {
    _title.dispose();
    _artist.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.surface,
      title: const Text('곡 추가', style: TextStyle(fontSize: 16)),
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
              _SongInput(title: t, artist: _artist.text.trim()),
            );
          },
          child: const Text('추가'),
        ),
      ],
    );
  }
}
