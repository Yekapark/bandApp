package com.yeka.bandapp.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.room.RoomApiSupport;
import org.springframework.http.ResponseEntity;

/**
 * 일정 통합 테스트 공통 헬퍼. 가입·밴드·초대·합주실 픽스처는 {@link RoomApiSupport} 계열에서 온다.
 * 다른 도메인(정기 일정 등) 테스트가 일정 픽스처를 재사용하므로 {@code public}이다({@link RoomApiSupport}와 동일).
 */
public abstract class ReservationApiSupport extends RoomApiSupport {

    protected static final String T10 = "2026-09-10T10:00:00Z";
    protected static final String T13 = "2026-09-10T13:00:00Z";
    protected static final String T16 = "2026-09-10T16:00:00Z";

    /** 밴드의 일정 등록 권한 모드를 바꾼다(밴드장 토큰 필요). */
    protected void setPermission(String leaderToken, long bandId, String mode) {
        ResponseEntity<String> res = put("/api/v1/bands/" + bandId + "/settings",
                "{\"reservationPermission\":\"" + mode + "\"}", leaderToken);
        if (res.getStatusCode().value() != 200) {
            throw new IllegalStateException("권한 모드 변경 실패: " + res.getBody());
        }
    }

    protected String reservationBody(long roomId, String startAt, String endAt) {
        return "{\"roomId\":" + roomId + ",\"startAt\":\"" + startAt + "\",\"endAt\":\"" + endAt + "\"}";
    }

    protected String reservationBody(long roomId, String startAt, String endAt, Integer cost, String note) {
        return "{\"roomId\":" + roomId
                + ",\"startAt\":\"" + startAt + "\",\"endAt\":\"" + endAt + "\""
                + (cost == null ? "" : ",\"cost\":" + cost)
                + (note == null ? "" : ",\"note\":\"" + note + "\"")
                + "}";
    }

    /** 일정을 등록하고(201 기대) reservationId 를 돌려준다. */
    protected long createReservation(String token, long bandId, long roomId, String startAt, String endAt) {
        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, startAt, endAt), token);
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("일정 등록 실패: " + res.getBody());
        }
        return data(res).get("reservation").get("id").asLong();
    }

    /** 등록/수정 응답에서 reservation 노드. */
    protected JsonNode reservationOf(ResponseEntity<String> res) {
        return data(res).get("reservation");
    }

    /** 등록/수정 응답에서 overlaps 배열. */
    protected JsonNode overlapsOf(ResponseEntity<String> res) {
        return data(res).get("overlaps");
    }

    protected int usageCount(String token, long bandId, long roomId) {
        return data(get("/api/v1/bands/" + bandId + "/rooms/" + roomId, token)).get("usageCount").asInt();
    }
}
