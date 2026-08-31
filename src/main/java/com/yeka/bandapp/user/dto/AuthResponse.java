package com.yeka.bandapp.user.dto;

import com.yeka.bandapp.common.security.TokenPair;
import com.yeka.bandapp.user.entity.User;

/**
 * 가입·로그인 응답. 가입은 즉시 로그인 상태로 이어진다(토큰 동봉).
 *
 * @param newUser 카카오 로그인에서 이번에 새로 가입되었는지. 앱이 온보딩 화면 표시를 판단하는 데 쓴다.
 */
public record AuthResponse(
        UserResponse user,
        TokenResponse tokens,
        boolean newUser
) {
    public static AuthResponse of(User user, TokenPair pair, boolean newUser) {
        return new AuthResponse(UserResponse.from(user), TokenResponse.from(pair), newUser);
    }
}
