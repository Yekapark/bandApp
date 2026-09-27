import 'dart:convert';

/// 가입·재설정에서 같은 기준을 쓴다. 원문을 잘라내거나 공백을 제거하지 않는다.
String? validateNewPassword(String? value) {
  final password = value ?? '';
  if (password.trim().isEmpty || password.length < 8) {
    return '비밀번호는 8자 이상이어야 해요.';
  }
  if (password.length > 64) return '비밀번호는 64자까지 쓸 수 있어요.';
  if (utf8.encode(password).length > 72) {
    return '비밀번호가 너무 길어요. 한글이나 특수문자가 있다면 더 짧게 입력해 주세요.';
  }
  return null;
}
