import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../band/application/band_providers.dart';
import '../../home/application/home_providers.dart';
import '../application/calendar_providers.dart';
import '../data/reservation_models.dart';
import '../data/reservation_repository.dart';
import '../data/room_models.dart';
import 'widgets/room_picker_sheet.dart';

/// 일정 등록 폼 — 이미 잡은 예약을 기록한다. 겹치는 시간대여도 등록은 성공한다.
class ReservationFormScreen extends ConsumerStatefulWidget {
  const ReservationFormScreen({super.key, this.initialDate});

  final DateTime? initialDate;

  @override
  ConsumerState<ReservationFormScreen> createState() =>
      _ReservationFormScreenState();
}

class _ReservationFormScreenState extends ConsumerState<ReservationFormScreen> {
  late DateTime _date;
  TimeOfDay _start = const TimeOfDay(hour: 19, minute: 0);
  double _hours = 3;
  Room? _room;
  final _cost = TextEditingController();
  final _note = TextEditingController();
  bool _loading = false;
  String? _error;

  static const _minHours = 0.5;
  static const _maxHours = 12.0;

  @override
  void initState() {
    super.initState();
    final d = widget.initialDate ?? DateTime.now();
    _date = DateTime(d.year, d.month, d.day);
  }

  @override
  void dispose() {
    _cost.dispose();
    _note.dispose();
    super.dispose();
  }

  DateTime get _startAt =>
      DateTime(_date.year, _date.month, _date.day, _start.hour, _start.minute);
  DateTime get _endAt => _startAt.add(Duration(minutes: (_hours * 60).round()));

  int? get _costValue {
    final raw = _cost.text.replaceAll(RegExp(r'[^0-9]'), '');
    if (raw.isEmpty) return null;
    return int.tryParse(raw);
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(now.year - 1),
      lastDate: DateTime(now.year + 2),
    );
    if (picked != null) {
      setState(() => _date = DateTime(picked.year, picked.month, picked.day));
    }
  }

  Future<void> _pickTime() async {
    final picked = await showTimePicker(context: context, initialTime: _start);
    if (picked != null) setState(() => _start = picked);
  }

  Future<void> _pickRoom() async {
    final band = ref.read(currentBandProvider);
    if (band == null) return;
    final room = await showRoomPickerSheet(context, band.id);
    if (room != null) setState(() => _room = room);
  }

  void _bumpHours(double delta) {
    setState(() {
      _hours = (_hours + delta).clamp(_minHours, _maxHours);
    });
  }

  Future<void> _submit() async {
    final band = ref.read(currentBandProvider);
    final room = _room;
    if (band == null || room == null || _loading) return;

    FocusScope.of(context).unfocus();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await ref.read(reservationRepositoryProvider).create(
            bandId: band.id,
            roomId: room.id,
            startAt: _startAt,
            endAt: _endAt,
            cost: _costValue,
            note: _note.text.trim(),
          );
      ref.invalidate(monthReservationsProvider);
      ref.invalidate(upcomingReservationsProvider(band.id));
      ref.invalidate(roomsProvider(band.id));

      if (!mounted) return;
      if (result.overlaps.isNotEmpty) {
        await _showOverlapDialog(result.overlaps);
      }
      if (!mounted) return;
      context.pushReplacement(Routes.reservation(result.reservation.id));
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = '일정을 등록하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _showOverlapDialog(List<OverlapWarning> overlaps) {
    return showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('겹치는 일정이 있어요', style: TextStyle(fontSize: 16)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '등록은 완료됐어요. 아래 일정과 시간대가 겹칩니다 — '
              '필요하면 상세 화면에서 시간을 조정하세요.',
              style: TextStyle(
                fontSize: 12.5,
                color: AppColors.textDim,
                height: 1.5,
              ),
            ),
            const SizedBox(height: 12),
            for (final o in overlaps)
              Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Text(
                  '· ${o.roomName}  ${Fmt.dateKoUtc(o.startAt)} '
                  '${Fmt.time(o.startAt)}–${Fmt.time(o.endAt)}',
                  style: const TextStyle(fontSize: 12),
                ),
              ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final band = ref.watch(currentBandProvider);
    final memberCount = band?.memberCount ?? 0;
    final cost = _costValue;
    final perPerson =
        (cost != null && memberCount > 0) ? (cost / memberCount).ceil() : null;

    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 30),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              BackLink(label: '합주 등록', onTap: () => context.pop()),
              const SizedBox(height: 12),
              Text(
                '이미 잡은 예약 기록하기',
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 6),
              const Text(
                '합주실 예약은 전화·카톡으로 직접 하고, 확정된 내용만 여기에 남겨요.',
                style: TextStyle(
                  fontSize: 12,
                  height: 1.6,
                  color: AppColors.textDim,
                ),
              ),
              const SizedBox(height: 20),

              // 합주실
              const _FieldLabel('합주실'),
              const SizedBox(height: 9),
              _RoomSelector(room: _room, onTap: _pickRoom),

              const SizedBox(height: 16),
              // 날짜 / 시작 시간
              Row(
                children: [
                  Expanded(
                    child: _TapCard(
                      label: '날짜',
                      value: Fmt.dateKo(_date),
                      onTap: _pickDate,
                    ),
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: _TapCard(
                      label: '시작 시간',
                      value: _start.format(context),
                      onTap: _pickTime,
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 9),
              // 이용 시간 스테퍼
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(13),
                  border: Border.all(color: AppColors.borderStrong),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            '이용 시간',
                            style: TextStyle(
                              fontSize: 11,
                              color: AppColors.textDim,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            Fmt.durationKo(_startAt, _endAt),
                            style: AppTypography.mono(fontSize: 15),
                          ),
                        ],
                      ),
                    ),
                    _StepButton(
                      icon: Icons.remove,
                      onTap: () => _bumpHours(-0.5),
                    ),
                    const SizedBox(width: 8),
                    _StepButton(icon: Icons.add, onTap: () => _bumpHours(0.5)),
                  ],
                ),
              ),

              const SizedBox(height: 16),
              const _FieldLabel('예약 메모 (선택)'),
              const SizedBox(height: 9),
              TextField(
                controller: _note,
                maxLength: 500,
                maxLines: 3,
                decoration: const InputDecoration(
                  hintText: '예: 02-334-1082 전화 예약 · 예약자명 세연 · 현금 결제',
                  counterText: '',
                ),
              ),

              const SizedBox(height: 16),
              const _FieldLabel('합주실 비용 (선택)'),
              const SizedBox(height: 9),
              TextField(
                controller: _cost,
                keyboardType: TextInputType.number,
                inputFormatters: [
                  FilteringTextInputFormatter.digitsOnly,
                  LengthLimitingTextInputFormatter(9),
                ],
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(
                  hintText: '예: 90000',
                  prefixText: '₩ ',
                ),
              ),
              if (perPerson != null) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.purple.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(13),
                    border: Border.all(
                        color: AppColors.purple.withValues(alpha: 0.28)),
                  ),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Text(
                          '1인당 (멤버 균등)',
                          style: TextStyle(
                            fontSize: 11.5,
                            color: AppColors.purpleSoft,
                          ),
                        ),
                      ),
                      Text(
                        Fmt.won(perPerson),
                        style: AppTypography.mono(
                          fontSize: 16,
                          color: AppColors.purpleSoft,
                        ),
                      ),
                    ],
                  ),
                ),
              ],

              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(
                  _error!,
                  style: const TextStyle(fontSize: 12, color: AppColors.danger),
                ),
              ],
              const SizedBox(height: 22),
              PrimaryButton(
                label: '합주 등록하기',
                loading: _loading,
                enabled: _room != null,
                onPressed: _submit,
              ),
              if (_room == null) ...[
                const SizedBox(height: 10),
                const Text(
                  '합주실을 먼저 선택해 주세요.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 11.5, color: AppColors.textFaint),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Text(
        text,
        style: const TextStyle(fontSize: 11.5, color: AppColors.textDim),
      );
}

class _RoomSelector extends StatelessWidget {
  const _RoomSelector({required this.room, required this.onTap});
  final Room? room;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(15),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.borderStrong),
        ),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(11),
              ),
              alignment: Alignment.center,
              child: const Text(
                '♪',
                style: TextStyle(fontSize: 14, color: AppColors.primary),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                room?.name ?? '합주실 선택하기',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color:
                      room == null ? AppColors.textDim : AppColors.textPrimary,
                ),
              ),
            ),
            const Text(
              '변경 ›',
              style: TextStyle(fontSize: 11, color: AppColors.primary),
            ),
          ],
        ),
      ),
    );
  }
}

class _TapCard extends StatelessWidget {
  const _TapCard({
    required this.label,
    required this.value,
    required this.onTap,
  });

  final String label;
  final String value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(13),
          border: Border.all(color: AppColors.borderStrong),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: const TextStyle(fontSize: 11, color: AppColors.textDim),
            ),
            const SizedBox(height: 5),
            Text(value, style: AppTypography.mono(fontSize: 14)),
          ],
        ),
      ),
    );
  }
}

class _StepButton extends StatelessWidget {
  const _StepButton({required this.icon, required this.onTap});
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 38,
        height: 38,
        decoration: BoxDecoration(
          color: AppColors.surfaceAlt,
          borderRadius: BorderRadius.circular(11),
        ),
        child: Icon(icon, size: 18, color: AppColors.textSecondary),
      ),
    );
  }
}
