import 'package:bandapp_client/features/auth/data/kakao_sdk.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';

/// QA-R29(AUTH-16) — 카카오톡 동의 화면의 「취소」 뒤 브라우저 로그인 창이 또 뜨면 안 된다.
void main() {
  test('동의 화면 취소(access_denied)와 카카오톡 창 닫기(CANCELED)는 취소다', () {
    expect(isKakaoLoginCanceled(KakaoAuthException(AuthErrorCause.accessDenied, 'User denied access')), isTrue);
    expect(isKakaoLoginCanceled(PlatformException(code: 'CANCELED')), isTrue);
  });

  test('그 밖의 오류는 브라우저 로그인으로 넘어간다', () {
    expect(isKakaoLoginCanceled(KakaoAuthException(AuthErrorCause.serverError, 'x')), isFalse);
    expect(isKakaoLoginCanceled(PlatformException(code: 'NotSupportError')), isFalse);
    expect(isKakaoLoginCanceled(Exception('network')), isFalse);
  });
}
