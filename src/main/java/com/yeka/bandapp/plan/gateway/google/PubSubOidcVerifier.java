package com.yeka.bandapp.plan.gateway.google;

import java.util.Optional;

/**
 * Google Pub/Sub push 가 붙이는 OIDC Bearer 토큰을 검증한다. Pub/Sub 구독에 인증용 서비스 계정을
 * 지정하면, Google 이 요청마다 {@code Authorization: Bearer <구글 서명 JWT>} 를 넣어 준다.
 *
 * <p>구현은 Google 공개키로 서명·발급자·audience·만료를 확인하고, 통과하면 토큰의 {@code email}
 * 클레임(그 서비스 계정 주소)을 돌려준다. 어느 단계든 실패하면 {@code empty}.
 */
public interface PubSubOidcVerifier {

    Optional<String> verifiedEmail(String bearerToken);
}
