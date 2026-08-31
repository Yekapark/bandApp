package com.yeka.bandapp.user.kakao;

/**
 * 카카오 API 창구. 실제 호출은 {@link KakaoApiClient}, 테스트는 가짜 구현으로 대체한다.
 * 이 인터페이스가 카카오 연동의 유일한 경계이며, 나머지 코드는 카카오를 모른다.
 */
public interface KakaoClient {

    /**
     * 액세스 토큰의 유효성과 발급 앱을 확인한다. ({@code GET /v1/user/access_token_info})
     *
     * @throws com.yeka.bandapp.common.exception.BusinessException
     *         토큰 무효 시 {@code KAKAO_TOKEN_INVALID}, 통신 실패 시 {@code KAKAO_API_ERROR},
     *         미설정 시 {@code KAKAO_NOT_CONFIGURED}
     */
    KakaoTokenInfo fetchTokenInfo(String kakaoAccessToken);

    /**
     * 회원번호·닉네임·이메일을 조회한다. ({@code GET /v2/user/me})
     */
    KakaoUserInfo fetchUserInfo(String kakaoAccessToken);

    /**
     * Admin 키로 대상 지정 연결 해제. ({@code POST /v1/user/unlink})
     * 사용자 토큰이 이미 만료됐을 수 있으므로 Admin 키를 쓴다.
     */
    void unlink(String socialId);
}
