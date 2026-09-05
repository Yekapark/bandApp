import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/api_exception.dart';
import '../../../shared/widgets/brand_mark.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_typography.dart';
import '../../../routing/app_router.dart';
import '../../../shared/widgets/primary_button.dart';
import '../application/auth_controller.dart';
import '../data/kakao_sdk.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _formKey = GlobalKey<FormState>();

  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref
          .read(authControllerProvider.notifier)
          .loginEmail(_email.text, _password.text);
      // 성공 시 라우터 redirect 가 /home 으로 보낸다.
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = '로그인 중 문제가 발생했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _todo(String what) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text('$what 은 준비 중입니다.')));
  }

  Future<void> _kakao() async {
    if (!AppConfig.kakaoEnabled) {
      _todo('카카오 로그인 (앱 키 미설정)');
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final token = await fetchKakaoAccessToken();
      await ref.read(authControllerProvider.notifier).loginKakao(token);
      // 성공 시 라우터 redirect 가 /home 으로 보낸다.
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (e) {
      debugPrint('kakao login failed: $e');
      final msg = e.toString().toLowerCase();
      final canceled = msg.contains('cancel') || msg.contains('access_denied');
      if (!canceled) setState(() => _error = '카카오 로그인 중 문제가 발생했습니다.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: RadialGradient(
            center: Alignment(0, -1),
            radius: 0.9,
            colors: [Color(0xFF231326), AppColors.background],
            stops: [0, 0.6],
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(26, 60, 26, 34),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      const BrandMark(size: 34),
                      const SizedBox(width: 10),
                      // 워드마크는 Bebas Neue(라틴 전용)라 한글 글리프가 없다 — 영문 표기를 쓴다.
                      Text(
                        AppConfig.appNameEn,
                        style: AppTypography.display(
                            fontSize: 23, letterSpacing: 3.5),
                      ),
                    ],
                  ),
                  const SizedBox(height: 30),
                  Text(
                    '우리 밴드 합주,\n한 곳에서 정리하자',
                    style: Theme.of(context).textTheme.headlineLarge,
                  ),
                  const SizedBox(height: 9),
                  const Text(
                    '이메일로 로그인하거나, 새 계정을 만들어 시작하세요.',
                    style: TextStyle(
                      fontSize: 13,
                      height: 1.6,
                      color: AppColors.textDim,
                    ),
                  ),
                  const SizedBox(height: 28),
                  TextFormField(
                    controller: _email,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.username],
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(hintText: '이메일'),
                    validator: (v) {
                      final s = v?.trim() ?? '';
                      if (s.isEmpty) return '이메일을 입력하세요.';
                      if (!s.contains('@')) return '이메일 형식이 아닙니다.';
                      return null;
                    },
                  ),
                  const SizedBox(height: 10),
                  TextFormField(
                    controller: _password,
                    obscureText: true,
                    autofillHints: const [AutofillHints.password],
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _submit(),
                    decoration: const InputDecoration(hintText: '비밀번호'),
                    validator: (v) => (v ?? '').isEmpty ? '비밀번호를 입력하세요.' : null,
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    _ErrorBanner(_error!),
                  ],
                  const SizedBox(height: 18),
                  PrimaryButton(
                    label: '로그인',
                    loading: _loading,
                    onPressed: _submit,
                  ),
                  const SizedBox(height: 12),
                  Center(
                    child: TextButton(
                      onPressed:
                          _loading ? null : () => context.push(Routes.terms),
                      child: const Text(
                        '이메일로 회원가입',
                        style: TextStyle(color: AppColors.primary),
                      ),
                    ),
                  ),
                  const SizedBox(height: 6),
                  const _OrDivider(),
                  const SizedBox(height: 16),
                  _SocialButton(
                    label: '카카오로 계속하기',
                    background: AppColors.kakao,
                    foreground: AppColors.onKakao,
                    onTap: _loading ? null : _kakao,
                  ),
                  const SizedBox(height: 9),
                  _SocialButton(
                    label: '네이버로 계속하기 (준비 중)',
                    background: AppColors.surfaceRaised,
                    foreground: AppColors.textFaint,
                    onTap: null,
                  ),
                  const SizedBox(height: 18),
                  const Text(
                    '계속하면 이용약관과 개인정보처리방침에 동의하는 것으로 간주됩니다.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 10.5,
                      height: 1.7,
                      color: AppColors.textFaint,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}


class _SocialButton extends StatelessWidget {
  const _SocialButton({
    required this.label,
    required this.background,
    required this.foreground,
    required this.onTap,
  });

  final String label;
  final Color background;
  final Color foreground;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 54,
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(14),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w700,
            color: foreground,
          ),
        ),
      ),
    );
  }
}

class _OrDivider extends StatelessWidget {
  const _OrDivider();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Expanded(child: Divider(color: AppColors.border)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text('또는',
              style: TextStyle(fontSize: 11, color: AppColors.textFaint)),
        ),
        const Expanded(child: Divider(color: AppColors.border)),
      ],
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner(this.message);
  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.danger.withOpacity(0.08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.danger.withOpacity(0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline, size: 16, color: AppColors.danger),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(fontSize: 12, color: AppColors.danger),
            ),
          ),
        ],
      ),
    );
  }
}
