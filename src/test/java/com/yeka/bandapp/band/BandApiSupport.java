package com.yeka.bandapp.band;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.support.ApiIntegrationTest;
import org.springframework.http.ResponseEntity;

/**
 * 밴드 통합 테스트 공통 헬퍼: 사용자 가입, 밴드 생성, 초대/참여.
 */
abstract class BandApiSupport extends ApiIntegrationTest {

    /** 이메일 계정을 만들고 access 토큰을 돌려준다. */
    protected String signup(String email, String name) {
        ResponseEntity<String> res = post("/api/v1/auth/signup",
                "{\"email\":\"" + email + "\",\"password\":\"pw12345678\",\"name\":\"" + name + "\"}");
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("가입 실패: " + res.getBody());
        }
        return body(res).at("/data/tokens/accessToken").asText();
    }

    protected long myUserId(String accessToken) {
        return body(get("/api/v1/users/me", accessToken)).at("/data/id").asLong();
    }

    /** 밴드를 만들고 bandId 를 돌려준다. 호출자가 LEADER 가 된다. */
    protected long createBand(String accessToken, String name) {
        ResponseEntity<String> res = post("/api/v1/bands", "{\"name\":\"" + name + "\"}", accessToken);
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("밴드 생성 실패: " + res.getBody());
        }
        return body(res).at("/data/id").asLong();
    }

    /** 초대코드를 발급하고 code 를 돌려준다. {@code body} 는 null 또는 {@code {"maxUses":1}} 등. */
    protected String issueInvite(String leaderToken, long bandId, String jsonBody) {
        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/invites", jsonBody, leaderToken);
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("초대코드 발급 실패: " + res.getBody());
        }
        return body(res).at("/data/code").asText();
    }

    protected ResponseEntity<String> join(String accessToken, String code) {
        return post("/api/v1/bands/join", "{\"code\":\"" + code + "\"}", accessToken);
    }

    protected JsonNode data(ResponseEntity<String> res) {
        return body(res).get("data");
    }
}
