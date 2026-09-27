import 'package:bandapp_client/features/auth/presentation/password_validation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('한글과 영문 비밀번호 길이 경계를 서버와 동일하게 검증한다', () {
    expect(validateNewPassword('가' * 24), isNull);
    expect(validateNewPassword('가' * 25), isNotNull);
    expect(validateNewPassword('a' * 64), isNull);
    expect(validateNewPassword('a' * 65), isNotNull);
    expect(validateNewPassword('가' * 23 + 'abc'), isNull);
    expect(validateNewPassword('가' * 23 + 'abcd'), isNotNull);
    expect(validateNewPassword('😀' * 18), isNull);
    expect(validateNewPassword('😀' * 19), isNotNull);
    expect(validateNewPassword('short'), isNotNull);
    expect(validateNewPassword(' ' * 8), isNotNull);
    expect(validateNewPassword(null), isNotNull);
  });
}
