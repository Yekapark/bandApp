package com.yeka.bandapp.user.kakao;

/**
 * 카카오 {@code GET /v2/user/me} 결과 중 우리가 쓰는 값.
 *
 * @param id       카카오 회원번호. 숫자지만 자릿수 정책 변경에 대비해 문자열로 다룬다.
 * @param email    이메일. 미동의/미제공 시 {@code null}.
 * @param nickname 프로필 닉네임. 미동의/미제공 시 {@code null}.
 */
public record KakaoUserInfo(
        String id,
        String email,
        String nickname
) {
}
