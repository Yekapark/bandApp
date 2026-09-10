import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';
import '../data/auth_repository.dart';

/// 비밀번호 재설정. 한 화면에서 두 단계로 진행한다.
///   0) 이메일 입력 → 6자리 인증번호 메일 발송 (`POST /auth/password-reset/request`)
///   1) 인증번호 + 새 비밀번호 입력 → 재설정 (`POST /auth/password-reset/confirm`)
/// 성공하면 그 계정의 모든 세션이 로그아웃되므로 로그인 화면으로 돌려보낸다.
class PasswordResetScreen extends ConsumerStatefulWidget {
  const PasswordResetScreen({super.key});

  @override
  ConsumerState<PasswordResetScreen> createState() =>
      _PasswordResetScreenState();
}

class _PasswordResetScreenState extends ConsumerState<PasswordResetScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _code = TextEditingController();
  final _password = TextEditingController();

  int _step = 0; // 0: 이메일, 1: 인증번호 + 새 비밀번호
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _code.dispose();
    _password.dispose();
    super.dispose();
  }

  AuthRepository get _repo => ref.read(authRepositoryProvider);

  Future<void> _sendCode() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await _repo.requestPasswordReset(email: _email.text.trim());
      if (!mounted) return;
      setState(() => _step = 1);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = '인증번호를 보내지 못했어요. 잠시 후 다시 시도해요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _resetPassword() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await _repo.confirmPasswordReset(
        email: _email.text.trim(),
        code: _code.text.trim(),
        newPassword: _password.text,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(content: Text('비밀번호를 바꿨어요. 새 비밀번호로 로그인해요.')),
        );
      context.go(Routes.login);
    } on ApiException catch (e) {
      final msg = e.code == 'PASSWORD_RESET_CODE_INVALID'
          ? '인증번호가 맞지 않거나 만료됐어요. 메일을 다시 확인해요.'
          : e.message;
      setState(() => _error = msg);
    } catch (_) {
      setState(() => _error = '비밀번호를 바꾸지 못했어요. 잠시 후 다시 시도해요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _backToEmail() {
    setState(() {
      _step = 0;
      _error = null;
      _code.clear();
      _password.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    final onEmailStep = _step == 0;
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 34),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                BackLink(
                  label: onEmailStep ? '로그인' : '이메일 다시 입력',
                  onTap: () {
                    if (!onEmailStep) {
                      _backToEmail();
                    } else if (context.canPop()) {
                      context.pop();
                    } else {
                      context.go(Routes.login);
                    }
                  },
                ),
                const SizedBox(height: 20),
                Text(
                  '비밀번호 재설정',
                  style: AppTypography.display(
                    fontSize: 11,
                    letterSpacing: 3,
                    color: AppColors.primary,
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  onEmailStep ? '비밀번호를 잊으셨어요?' : '메일함을 확인해요',
                  style: Theme.of(context).textTheme.headlineLarge,
                ),
                const SizedBox(height: 9),
                Text(
                  onEmailStep
                      ? '가입한 이메일을 넣으면 6자리 인증번호를 보내 드려요.'
                      : '${_email.text.trim()} 로 인증번호를 보냈어요.\n메일이 안 보이면 스팸함도 확인해요. 인증번호는 15분간 쓸 수 있어요.',
                  style: const TextStyle(
                    fontSize: 12.5,
                    height: 1.6,
                    color: AppColors.textDim,
                  ),
                ),
                const SizedBox(height: 22),
                if (onEmailStep) ...[
                  const _Label('이메일'),
                  TextFormField(
                    controller: _email,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.username],
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _sendCode(),
                    decoration:
                        const InputDecoration(hintText: 'you@example.com'),
                    validator: (v) {
                      final s = v?.trim() ?? '';
                      if (s.isEmpty) return '이메일을 입력하세요.';
                      if (!s.contains('@') || !s.contains('.')) {
                        return '이메일 형식이 아닙니다.';
                      }
                      return null;
                    },
                  ),
                ] else ...[
                  const _Label('인증번호'),
                  TextFormField(
                    controller: _code,
                    keyboardType: TextInputType.number,
                    textInputAction: TextInputAction.next,
                    maxLength: 6,
                    decoration: const InputDecoration(
                      hintText: '메일로 받은 6자리 숫자',
                      counterText: '',
                    ),
                    validator: (v) {
                      final s = v?.trim() ?? '';
                      if (!RegExp(r'^\d{6}$').hasMatch(s)) {
                        return '인증번호는 숫자 6자리예요.';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 14),
                  const _Label('새 비밀번호'),
                  TextFormField(
                    controller: _password,
                    obscureText: true,
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _resetPassword(),
                    decoration: const InputDecoration(hintText: '8자 이상'),
                    validator: (v) {
                      final s = v ?? '';
                      if (s.length < 8) return '비밀번호는 8자 이상이어야 해요.';
                      if (s.length > 64) return '비밀번호는 64자까지 쓸 수 있어요.';
                      return null;
                    },
                  ),
                ],
                if (_error != null) ...[
                  const SizedBox(height: 14),
                  Text(
                    _error!,
                    style:
                        const TextStyle(fontSize: 12, color: AppColors.danger),
                  ),
                ],
                const SizedBox(height: 22),
                PrimaryButton(
                  label: onEmailStep ? '인증번호 받기' : '비밀번호 바꾸기',
                  loading: _loading,
                  onPressed: onEmailStep ? _sendCode : _resetPassword,
                ),
                if (!onEmailStep) ...[
                  const SizedBox(height: 10),
                  Center(
                    child: TextButton(
                      onPressed: _loading ? null : _sendCode,
                      child: const Text(
                        '인증번호 다시 받기',
                        style:
                            TextStyle(color: AppColors.textDim, fontSize: 13),
                      ),
                    ),
                  ),
                ],
              ],
            ),
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
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8, left: 2),
      child: Text(
        text,
        style: const TextStyle(fontSize: 11.5, color: AppColors.textDim),
      ),
    );
  }
}
