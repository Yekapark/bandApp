import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/app_scaffold.dart';
import '../../../shared/widgets/primary_button.dart';
import '../application/auth_controller.dart';

/// 회원가입 STEP 2 — 계정 정보. POST /auth/signup.
class SignupScreen extends ConsumerStatefulWidget {
  const SignupScreen({super.key});

  @override
  ConsumerState<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends ConsumerState<SignupScreen> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();

  bool _loading = false;
  String? _error;
  final _fieldErrors = <String, String>{};

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    setState(() => _fieldErrors.clear());
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).signupEmail(
            email: _email.text,
            password: _password.text,
            name: _name.text,
          );
      // 성공 → 라우터 redirect 가 /home 으로 이동시킨다.
    } on ApiException catch (e) {
      setState(() {
        _error = e.message;
        _fieldErrors.addAll(e.fieldErrors);
      });
    } catch (_) {
      setState(() => _error = '가입 중 문제가 발생했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 34),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                BackLink(label: '약관 동의', onTap: () => context.pop()),
                const SizedBox(height: 20),
                Text('STEP 2 / 3 · 회원가입',
                    style: AppTypography.display(
                        fontSize: 11,
                        letterSpacing: 3,
                        color: AppColors.primary)),
                const SizedBox(height: 10),
                Text('계정을 만들어요',
                    style: Theme.of(context).textTheme.headlineLarge),
                const SizedBox(height: 24),
                _Label('이름'),
                TextFormField(
                  controller: _name,
                  textInputAction: TextInputAction.next,
                  maxLength: 30,
                  decoration: InputDecoration(
                    hintText: '밴드 멤버에게 표시될 이름',
                    counterText: '',
                    errorText: _fieldErrors['name'],
                  ),
                  validator: (v) {
                    final s = v?.trim() ?? '';
                    if (s.isEmpty) return '이름을 입력하세요.';
                    if (s.length > 30) return '30자 이내로 입력하세요.';
                    return null;
                  },
                ),
                const SizedBox(height: 14),
                _Label('이메일'),
                TextFormField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  textInputAction: TextInputAction.next,
                  decoration: InputDecoration(
                    hintText: 'you@example.com',
                    errorText: _fieldErrors['email'],
                  ),
                  validator: (v) {
                    final s = v?.trim() ?? '';
                    if (s.isEmpty) return '이메일을 입력하세요.';
                    if (!s.contains('@') || !s.contains('.')) {
                      return '이메일 형식이 아닙니다.';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 14),
                _Label('비밀번호'),
                TextFormField(
                  controller: _password,
                  obscureText: true,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  decoration: InputDecoration(
                    hintText: '8자 이상',
                    errorText: _fieldErrors['password'],
                  ),
                  validator: (v) {
                    final s = v ?? '';
                    if (s.length < 8) return '비밀번호는 8자 이상이어야 합니다.';
                    if (s.length > 64) return '비밀번호는 64자 이하여야 합니다.';
                    return null;
                  },
                ),
                if (_error != null) ...[
                  const SizedBox(height: 14),
                  Text(_error!,
                      style: const TextStyle(
                          fontSize: 12, color: AppColors.danger)),
                ],
                const SizedBox(height: 22),
                PrimaryButton(
                  label: '가입하고 시작하기',
                  loading: _loading,
                  onPressed: _submit,
                ),
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
      child: Text(text,
          style: const TextStyle(fontSize: 11.5, color: AppColors.textDim)),
    );
  }
}
