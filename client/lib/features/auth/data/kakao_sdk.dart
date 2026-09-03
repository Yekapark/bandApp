import 'package:flutter/foundation.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';

/// 카카오 SDK 로그인 플로우 → access token 반환.
///
/// 백엔드 `POST /auth/kakao` 는 이 토큰만 받으면 카카오에 사용자 조회 후
/// 가입/로그인 처리한다. 여기서는 토큰 획득까지만 담당한다.
///
/// 네이티브: 카카오톡 설치 시 앱 전환 로그인, 아니면 카카오계정(웹뷰) 로그인.
/// 웹: 카카오계정 로그인만 가능.
Future<String> fetchKakaoAccessToken() async {
  OAuthToken token;
  if (!kIsWeb && await isKakaoTalkInstalled()) {
    try {
      token = await UserApi.instance.loginWithKakaoTalk();
    } catch (_) {
      // 사용자가 카카오톡 로그인을 취소하면 계정 로그인으로 폴백하지 않는다
      // (취소를 계정창으로 되돌리면 UX 가 나쁨). 그 외 오류만 폴백.
      token = await UserApi.instance.loginWithKakaoAccount();
    }
  } else {
    token = await UserApi.instance.loginWithKakaoAccount();
  }
  return token.accessToken;
}
