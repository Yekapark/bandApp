import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';

/// 회원가입 STEP 1 — 약관 동의. 서버 호출 없이 클라이언트에서만 게이트한다.
/// (백엔드 signup 은 email/password/name 만 받는다.)
///
/// **카카오 가입도 여기를 거친다.** 예전에는 카카오 버튼이 곧바로 로그인으로 갔고, 그
/// 첫 로그인이 곧 가입이라 **약관도 나이 확인도 없이 계정이 생겼다.** 개인정보보호법은
/// 만 14세 미만의 개인정보를 처리하려면 법정대리인 동의를 받으라고 하는데, 우리는 그
/// 절차가 없으므로 "만 14세 이상" 을 필수 항목으로 확인하고 받지 않는 쪽을 택한다.
///
/// [next] 가 있으면 동의 후 그 길로 간다(카카오). 없으면 이메일 가입 폼으로 간다.
class TermsScreen extends StatefulWidget {
  const TermsScreen({super.key, this.next});

  /// 동의를 마친 뒤 갈 곳. 이메일 가입이면 null.
  final VoidCallback? next;

  @override
  State<TermsScreen> createState() => _TermsScreenState();
}

class _TermsScreenState extends State<TermsScreen> {
  final _agreed = <String, bool>{
    'age': false,
    'tos': false,
    'privacy': false,
    'marketing': false,
  };

  static const _rows = [
    ('age', '만 14세 이상이에요', true),
    ('tos', '이용약관 동의', true),
    ('privacy', '개인정보 수집·이용 동의', true),
    ('marketing', '공연·이벤트 소식 받기', false),
  ];

  bool get _requiredDone =>
      _agreed['age']! && _agreed['tos']! && _agreed['privacy']!;
  bool get _allDone => _agreed.values.every((v) => v);

  void _toggle(String key) => setState(() => _agreed[key] = !_agreed[key]!);

  void _toggleAll() {
    final next = !_allDone;
    setState(() {
      for (final k in _agreed.keys) {
        _agreed[k] = next;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              BackLink(label: '로그인', onTap: () => context.pop()),
              const SizedBox(height: 20),
              Text('STEP 1 / 3 · 회원가입',
                  style: AppTypography.display(
                      fontSize: 11,
                      letterSpacing: 3,
                      color: AppColors.primary)),
              const SizedBox(height: 10),
              Text('약관에 동의하면\n가입을 이어갈 수 있어요',
                  style: Theme.of(context).textTheme.headlineLarge),
              const SizedBox(height: 8),
              const Text(
                '이름·이메일·프로필은 같은 밴드 멤버에게만 보여요.',
                style: TextStyle(
                    fontSize: 12.5, height: 1.6, color: AppColors.textDim),
              ),
              const SizedBox(height: 24),
              _AllAgreeRow(checked: _allDone, onTap: _toggleAll),
              const SizedBox(height: 10),
              for (final (key, label, isRequired) in _rows)
                _AgreeRow(
                  label: label,
                  isRequired: isRequired,
                  checked: _agreed[key]!,
                  onTap: () => _toggle(key),
                ),
              const Spacer(),
              const Text(
                '전문은 아래 링크에서 언제든 다시 볼 수 있어요.',
                style: TextStyle(
                    fontSize: 11, height: 1.7, color: AppColors.textFaint),
              ),
              const SizedBox(height: 16),
              PrimaryButton(
                label: '동의하고 계속하기',
                enabled: _requiredDone,
                onPressed: () {
                  final next = widget.next;
                  if (next == null) {
                    context.push(Routes.signup);
                  } else {
                    // 동의 화면을 스택에서 걷어내고 원래 하려던 일로 — 뒤로 눌렀을 때
                    // 동의 화면이 다시 나오면 이미 동의한 사람에게는 막다른 길이 된다.
                    context.pop();
                    next();
                  }
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AllAgreeRow extends StatelessWidget {
  const _AllAgreeRow({required this.checked, required this.onTap});
  final bool checked;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(15),
        decoration: BoxDecoration(
          color: AppColors.surfaceRaised,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.borderStrong),
        ),
        child: Row(
          children: [
            _CheckBox(checked: checked, size: 24),
            const SizedBox(width: 12),
            const Text('전체 동의',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
          ],
        ),
      ),
    );
  }
}

class _AgreeRow extends StatelessWidget {
  const _AgreeRow({
    required this.label,
    required this.isRequired,
    required this.checked,
    required this.onTap,
  });

  final String label;
  final bool isRequired;
  final bool checked;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 13, horizontal: 4),
        child: Row(
          children: [
            _CheckBox(checked: checked, size: 20),
            const SizedBox(width: 12),
            Expanded(child: Text(label, style: const TextStyle(fontSize: 13))),
            Text(
              isRequired ? '(필수)' : '(선택)',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                color: isRequired ? AppColors.primary : AppColors.textFaint,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CheckBox extends StatelessWidget {
  const _CheckBox({required this.checked, required this.size});
  final bool checked;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: checked ? AppColors.primary : Colors.transparent,
        borderRadius: BorderRadius.circular(size * 0.3),
        border: Border.all(
          color: checked ? AppColors.primary : const Color(0x40F2F0EC),
          width: 1.5,
        ),
      ),
      child: checked
          ? const Icon(Icons.check, size: 13, color: AppColors.onPrimary)
          : null,
    );
  }
}
