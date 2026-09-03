import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/primary_button.dart';
import '../application/notification_providers.dart';
import '../data/notification_models.dart';
import '../data/notification_repository.dart';

/// 알림 설정 — 푸시 on/off 와 "일정 시작 N분 전" 리마인더 시점.
/// 백엔드: `GET/PUT /api/v1/notifications/settings`. 실제 푸시 발송/수신은 별도(§FCM).
class NotificationSettingsScreen extends ConsumerStatefulWidget {
  const NotificationSettingsScreen({super.key});

  @override
  ConsumerState<NotificationSettingsScreen> createState() =>
      _NotificationSettingsScreenState();
}

/// (분, 라벨) — 서버가 허용하는 1~1440 범위 안의 프리셋. 최대 5개까지 선택 가능(서버 상한).
const _presets = <(int, String)>[
  (10, '10분 전'),
  (30, '30분 전'),
  (60, '1시간 전'),
  (180, '3시간 전'),
  (360, '6시간 전'),
  (1440, '1일 전'),
];
const _maxOffsets = 5;

class _NotificationSettingsScreenState
    extends ConsumerState<NotificationSettingsScreen> {
  bool _push = true;
  final Set<int> _offsets = {};
  bool _busy = false;
  bool _seeded = false;

  void _seed(NotificationSetting s) {
    if (_seeded) return;
    _seeded = true;
    _push = s.pushEnabled;
    _offsets
      ..clear()
      ..addAll(s.reminderOffsets);
  }

  bool _dirty(NotificationSetting loaded) {
    final a = _offsets.toList()..sort();
    final b = loaded.reminderOffsets.toList()..sort();
    return _push != loaded.pushEnabled ||
        a.length != b.length ||
        !List.generate(a.length, (i) => a[i] == b[i]).every((x) => x);
  }

  Future<void> _save() async {
    setState(() => _busy = true);
    try {
      final saved = await ref.read(notificationRepositoryProvider).update(
            pushEnabled: _push,
            reminderOffsets: _offsets.toList()..sort(),
          );
      _seeded = false;
      ref.invalidate(notificationSettingProvider);
      if (mounted) {
        _seed(saved);
        _toast('알림 설정을 저장했어요.');
        setState(() {});
      }
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
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

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(notificationSettingProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          '알림 설정',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
        ),
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorBody(
          message: e is ApiException ? e.message : '알림 설정을 불러오지 못했어요.',
          onRetry: () => ref.invalidate(notificationSettingProvider),
        ),
        data: (loaded) {
          _seed(loaded);
          return _Body(
            push: _push,
            offsets: _offsets,
            busy: _busy,
            dirty: _dirty(loaded),
            onTogglePush: (v) => setState(() => _push = v),
            onToggleOffset: (m) => setState(() {
              if (_offsets.contains(m)) {
                _offsets.remove(m);
              } else if (_offsets.length >= _maxOffsets) {
                _toast('리마인더 시점은 최대 $_maxOffsets개까지예요.');
              } else {
                _offsets.add(m);
              }
            }),
            onSave: _save,
          );
        },
      ),
    );
  }
}

class _Body extends StatelessWidget {
  const _Body({
    required this.push,
    required this.offsets,
    required this.busy,
    required this.dirty,
    required this.onTogglePush,
    required this.onToggleOffset,
    required this.onSave,
  });

  final bool push;
  final Set<int> offsets;
  final bool busy;
  final bool dirty;
  final ValueChanged<bool> onTogglePush;
  final ValueChanged<int> onToggleOffset;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
            children: [
              _Card(
                child: SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  activeThumbColor: AppColors.primary,
                  title: const Text(
                    '푸시 알림',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
                  ),
                  subtitle: const Text(
                    '새 일정·승인·정산·취소 알림을 푸시로 받아요.',
                    style: TextStyle(fontSize: 11.5, color: AppColors.textDim),
                  ),
                  value: push,
                  onChanged: busy ? null : onTogglePush,
                ),
              ),
              const SizedBox(height: 14),
              Opacity(
                opacity: push ? 1 : 0.4,
                child: _Card(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        '리마인더 시점',
                        style: TextStyle(
                            fontSize: 14, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 4),
                      const Text(
                        '일정 시작 전에 미리 알려드려요. 여러 개 고를 수 있어요.',
                        style:
                            TextStyle(fontSize: 11.5, color: AppColors.textDim),
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final (m, label) in _presets)
                            _Chip(
                              label: label,
                              selected: offsets.contains(m),
                              onTap: (push && !busy)
                                  ? () => onToggleOffset(m)
                                  : null,
                            ),
                        ],
                      ),
                      if (offsets.isEmpty) ...[
                        const SizedBox(height: 10),
                        const Text(
                          '아무것도 고르지 않으면 리마인더를 받지 않아요.',
                          style: TextStyle(
                              fontSize: 11, color: AppColors.textFaint),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 14),
              const Text(
                '이 기기에서 실제 푸시를 받으려면 앱 알림 권한과 Firebase 설정이 필요해요. '
                '설정을 저장해 두면 발송 준비가 끝나는 대로 적용돼요.',
                style: TextStyle(
                    fontSize: 11, color: AppColors.textFaint, height: 1.5),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
          child: PrimaryButton(
            label: '저장',
            loading: busy,
            enabled: dirty,
            onPressed: onSave,
          ),
        ),
      ],
    );
  }
}

class _Card extends StatelessWidget {
  const _Card({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surfaceCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: child,
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({required this.label, required this.selected, this.onTap});

  final String label;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
        decoration: BoxDecoration(
          color: selected ? AppColors.primary : AppColors.surfaceRaised,
          borderRadius: BorderRadius.circular(99),
          border: Border.all(
            color: selected ? AppColors.primary : AppColors.borderStrong,
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 12.5,
            fontWeight: FontWeight.w700,
            color: selected ? AppColors.onPrimary : AppColors.textSecondary,
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
          Text(message,
              style: const TextStyle(color: AppColors.textDim, fontSize: 13)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    );
  }
}
