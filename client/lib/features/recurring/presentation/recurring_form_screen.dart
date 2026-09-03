import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';
import '../../band/application/band_providers.dart';
import '../../reservation/data/room_models.dart';
import '../../reservation/presentation/widgets/room_picker_sheet.dart';
import '../data/recurring_models.dart';
import '../data/recurring_repository.dart';

/// 정기 일정 규칙 등록 — 매주/격주/매월 반복되는 합주. 등록 시 8주분 회차가 캘린더에 생긴다.
class RecurringFormScreen extends ConsumerStatefulWidget {
  const RecurringFormScreen({super.key});

  @override
  ConsumerState<RecurringFormScreen> createState() =>
      _RecurringFormScreenState();
}

class _RecurringFormScreenState extends ConsumerState<RecurringFormScreen> {
  Room? _room;
  RecurringFrequency _freq = RecurringFrequency.weekly;
  late int _weekday; // 1=월 … 7=일
  TimeOfDay _start = const TimeOfDay(hour: 19, minute: 0);
  TimeOfDay _end = const TimeOfDay(hour: 22, minute: 0);
  late DateTime _startDate;
  DateTime? _endDate;
  final _cost = TextEditingController();
  final _note = TextEditingController();
  bool _loading = false;
  String? _error;

  static const _dowKo = ['월', '화', '수', '목', '금', '토', '일'];

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _weekday = now.weekday;
    _startDate = DateTime(now.year, now.month, now.day);
  }

  @override
  void dispose() {
    _cost.dispose();
    _note.dispose();
    super.dispose();
  }

  int get _startMinutes => _start.hour * 60 + _start.minute;
  int get _endMinutes => _end.hour * 60 + _end.minute;

  int? get _costValue {
    final raw = _cost.text.replaceAll(RegExp(r'[^0-9]'), '');
    return raw.isEmpty ? null : int.tryParse(raw);
  }

  String _hhmm(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  Future<void> _pickRoom() async {
    final band = ref.read(currentBandProvider);
    if (band == null) return;
    final room = await showRoomPickerSheet(context, band.id);
    if (room != null) setState(() => _room = room);
  }

  Future<void> _pickTime({required bool start}) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: start ? _start : _end,
    );
    if (picked == null) return;
    setState(() {
      if (start) {
        _start = picked;
      } else {
        _end = picked;
      }
    });
  }

  Future<void> _pickDate({required bool start}) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: start ? _startDate : (_endDate ?? _startDate),
      firstDate: DateTime(now.year - 1),
      lastDate: DateTime(now.year + 3),
    );
    if (picked == null) return;
    setState(() {
      final d = DateTime(picked.year, picked.month, picked.day);
      if (start) {
        _startDate = d;
      } else {
        _endDate = d;
      }
    });
  }

  Future<void> _submit() async {
    final band = ref.read(currentBandProvider);
    final room = _room;
    if (band == null || room == null || _loading) return;

    if (_endMinutes <= _startMinutes) {
      setState(() => _error = '종료 시각은 시작 시각보다 뒤여야 해요.');
      return;
    }
    if (_endDate != null && _endDate!.isBefore(_startDate)) {
      setState(() => _error = '종료일은 시작일 이후여야 해요.');
      return;
    }

    FocusScope.of(context).unfocus();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await ref.read(recurringRepositoryProvider).create(
            bandId: band.id,
            roomId: room.id,
            frequency: _freq,
            dayOfWeek: dayOfWeekWire(_weekday),
            startTime: _hhmm(_start),
            endTime: _hhmm(_end),
            startDate: Fmt.ymd(_startDate),
            endDate: _endDate == null ? null : Fmt.ymd(_endDate!),
            cost: _costValue,
            note: _note.text.trim(),
          );
      if (!mounted) return;
      await _showResultDialog(result);
      if (!mounted) return;
      context.pop(true);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = '정기 일정을 등록하지 못했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _showResultDialog(RecurringWriteResult result) {
    return showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: const Text('정기 일정을 등록했어요', style: TextStyle(fontSize: 16)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '앞으로 ${result.occurrenceCount}개 회차가 캘린더에 생겼어요. '
              '이후 회차는 배치가 이어서 만듭니다.',
              style: const TextStyle(
                  fontSize: 12.5, color: AppColors.textDim, height: 1.5),
            ),
            if (result.overlaps.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Text('시간대가 겹치는 기존 일정',
                  style: TextStyle(fontSize: 11.5, color: AppColors.textFaint)),
              const SizedBox(height: 6),
              for (final o in result.overlaps.take(5))
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(
                    '· ${o.roomName}  ${Fmt.dateKoUtc(o.startAt)} '
                    '${Fmt.time(o.startAt)}–${Fmt.time(o.endAt)}',
                    style: const TextStyle(fontSize: 11.5),
                  ),
                ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 30),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              BackLink(label: '정기 일정', onTap: () => context.pop()),
              const SizedBox(height: 12),
              Text('반복되는 합주 등록',
                  style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 6),
              const Text(
                '등록하면 앞으로 8주분 회차가 캘린더에 자동으로 생겨요. '
                '개별 회차는 캘린더에서 따로 수정·취소할 수 있어요.',
                style: TextStyle(
                    fontSize: 12, height: 1.6, color: AppColors.textDim),
              ),
              const SizedBox(height: 20),
              const _Label('합주실'),
              const SizedBox(height: 9),
              GestureDetector(
                onTap: _pickRoom,
                child: Container(
                  padding: const EdgeInsets.all(15),
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.borderStrong),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          _room?.name ?? '합주실 선택하기',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                            color: _room == null
                                ? AppColors.textDim
                                : AppColors.textPrimary,
                          ),
                        ),
                      ),
                      const Text('변경 ›',
                          style: TextStyle(
                              fontSize: 11, color: AppColors.primary)),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const _Label('반복 주기'),
              const SizedBox(height: 9),
              _FreqSelector(
                value: _freq,
                onChanged: (f) => setState(() => _freq = f),
              ),
              const SizedBox(height: 16),
              const _Label('요일'),
              const SizedBox(height: 9),
              Row(
                children: [
                  for (var i = 1; i <= 7; i++)
                    Expanded(
                      child: Padding(
                        padding: EdgeInsets.only(right: i == 7 ? 0 : 6),
                        child: _DayChip(
                          label: _dowKo[i - 1],
                          active: _weekday == i,
                          onTap: () => setState(() => _weekday = i),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: _TapCard(
                      label: '시작 시각',
                      value: _start.format(context),
                      onTap: () => _pickTime(start: true),
                    ),
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: _TapCard(
                      label: '종료 시각',
                      value: _end.format(context),
                      onTap: () => _pickTime(start: false),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 9),
              Row(
                children: [
                  Expanded(
                    child: _TapCard(
                      label: '시작일',
                      value: Fmt.dateKo(_startDate),
                      onTap: () => _pickDate(start: true),
                    ),
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: _TapCard(
                      label: '종료일 (선택)',
                      value: _endDate == null ? '없음' : Fmt.dateKo(_endDate!),
                      onTap: () => _pickDate(start: false),
                      onClear: _endDate == null
                          ? null
                          : () => setState(() => _endDate = null),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const _Label('회차 비용 (선택)'),
              const SizedBox(height: 9),
              TextField(
                controller: _cost,
                keyboardType: TextInputType.number,
                inputFormatters: [
                  FilteringTextInputFormatter.digitsOnly,
                  LengthLimitingTextInputFormatter(9),
                ],
                decoration: const InputDecoration(
                  hintText: '예: 30000',
                  prefixText: '₩ ',
                ),
              ),
              const SizedBox(height: 16),
              const _Label('회차 메모 (선택)'),
              const SizedBox(height: 9),
              TextField(
                controller: _note,
                maxLength: 500,
                maxLines: 2,
                decoration: const InputDecoration(
                  hintText: '예: 정기 합주 · 예약자 홍길동',
                  counterText: '',
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(_error!,
                    style:
                        const TextStyle(fontSize: 12, color: AppColors.danger)),
              ],
              const SizedBox(height: 22),
              PrimaryButton(
                label: '정기 일정 등록',
                loading: _loading,
                enabled: _room != null,
                onPressed: _submit,
              ),
              if (_room == null) ...[
                const SizedBox(height: 10),
                const Text('합주실을 먼저 선택해 주세요.',
                    textAlign: TextAlign.center,
                    style:
                        TextStyle(fontSize: 11.5, color: AppColors.textFaint)),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _Label extends StatelessWidget {
  const _Label(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Text(text,
      style: const TextStyle(fontSize: 11.5, color: AppColors.textDim));
}

class _FreqSelector extends StatelessWidget {
  const _FreqSelector({required this.value, required this.onChanged});
  final RecurringFrequency value;
  final ValueChanged<RecurringFrequency> onChanged;

  static const _opts = [
    RecurringFrequency.weekly,
    RecurringFrequency.biweekly,
    RecurringFrequency.monthly,
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderStrong),
      ),
      child: Row(
        children: [
          for (final f in _opts)
            Expanded(
              child: GestureDetector(
                onTap: () => onChanged(f),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 120),
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  decoration: BoxDecoration(
                    color: value == f ? AppColors.primary : Colors.transparent,
                    borderRadius: BorderRadius.circular(9),
                  ),
                  alignment: Alignment.center,
                  child: Text(
                    recurringFrequencyLabel(f),
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: value == f
                          ? AppColors.onPrimary
                          : AppColors.textSecondary,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _DayChip extends StatelessWidget {
  const _DayChip({
    required this.label,
    required this.active,
    required this.onTap,
  });

  final String label;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 40,
        decoration: BoxDecoration(
          color: active ? AppColors.primary : AppColors.surface,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: active ? AppColors.primary : AppColors.borderStrong,
          ),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: active ? AppColors.onPrimary : AppColors.textSecondary,
          ),
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
    this.onClear,
  });

  final String label;
  final String value;
  final VoidCallback onTap;
  final VoidCallback? onClear;

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
            Row(
              children: [
                Expanded(
                  child: Text(label,
                      style: const TextStyle(
                          fontSize: 11, color: AppColors.textDim)),
                ),
                if (onClear != null)
                  GestureDetector(
                    onTap: onClear,
                    child: const Icon(Icons.close,
                        size: 14, color: AppColors.textFaint),
                  ),
              ],
            ),
            const SizedBox(height: 5),
            Text(value, style: AppTypography.mono(fontSize: 14)),
          ],
        ),
      ),
    );
  }
}
