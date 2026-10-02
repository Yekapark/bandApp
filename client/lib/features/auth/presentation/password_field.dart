import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';

/// 가입·재설정의 비밀번호 입력칸. 눈 아이콘으로 입력한 글자를 보이거나 가린다.
class PasswordField extends StatefulWidget {
  const PasswordField({
    super.key,
    required this.controller,
    required this.hintText,
    this.validator,
    this.errorText,
    this.textInputAction = TextInputAction.next,
    this.onFieldSubmitted,
  });

  final TextEditingController controller;
  final String hintText;
  final FormFieldValidator<String>? validator;
  final String? errorText;
  final TextInputAction textInputAction;
  final ValueChanged<String>? onFieldSubmitted;

  @override
  State<PasswordField> createState() => _PasswordFieldState();
}

class _PasswordFieldState extends State<PasswordField> {
  bool _obscure = true;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: widget.controller,
      obscureText: _obscure,
      textInputAction: widget.textInputAction,
      onFieldSubmitted: widget.onFieldSubmitted,
      decoration: InputDecoration(
        hintText: widget.hintText,
        errorText: widget.errorText,
        suffixIcon: IconButton(
          tooltip: _obscure ? '비밀번호 보기' : '비밀번호 숨기기',
          icon: Icon(
            _obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined,
            size: 20,
            color: AppColors.textDim,
          ),
          onPressed: () => setState(() => _obscure = !_obscure),
        ),
      ),
      validator: widget.validator,
    );
  }
}

/// 비밀번호 확인칸 검사. 원문 그대로 비교한다(공백 제거 없음).
String? validatePasswordConfirm(String? value, String password) =>
    (value ?? '') == password ? null : '비밀번호가 서로 달라요';
