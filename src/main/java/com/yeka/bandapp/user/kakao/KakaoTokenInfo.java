package com.yeka.bandapp.user.kakao;

/**
 * 카카오 {@code GET /v1/user/access_token_info} 결과.
 *
 * @param id    토큰이 가리키는 카카오 회원번호
 * @param appId 토큰을 발급한 카카오 앱 ID. 우리 앱 ID와 대조해 confused-deputy를 막는다.
 */
public record KakaoTokenInfo(
        String id,
        Long appId
) {
}
