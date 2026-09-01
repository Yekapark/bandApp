package com.yeka.bandapp.room;

import com.yeka.bandapp.band.BandApiSupport;
import org.springframework.http.ResponseEntity;

/**
 * 합주실 통합 테스트 공통 헬퍼. 밴드 관련 픽스처(가입·밴드 생성·초대·참여)는 {@link BandApiSupport}에서 온다.
 */
abstract class RoomApiSupport extends BandApiSupport {

    /** 합주실을 등록하고 roomId 를 돌려준다. {@code jsonBody}는 {@code {"name":"...","address":"..."}} 형태. */
    protected long createRoom(String accessToken, long bandId, String jsonBody) {
        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/rooms", jsonBody, accessToken);
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("합주실 등록 실패: " + res.getBody());
        }
        return data(res).get("id").asLong();
    }

    protected String roomBody(String name, String address) {
        return "{\"name\":\"" + name + "\",\"address\":" + json(address) + "}";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
