package com.yeka.bandapp.recurring;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.reservation.ReservationApiSupport;
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 정기 일정 통합 테스트 공통 헬퍼. 가입·밴드·합주실·일정 픽스처는 {@link ReservationApiSupport} 계열에서 온다.
 *
 * <p>회차 시각은 "오늘"(Asia/Seoul) 기준으로 갈리므로 날짜는 하드코딩하지 않고 {@link #today()}에서 상대적으로 만든다.
 */
public abstract class RecurringApiSupport extends ReservationApiSupport {

    protected static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    protected LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    /** {@code startTime}/{@code endTime}은 "HH:mm", 날짜는 ISO. {@code endDate}가 null이면 생략. */
    protected String ruleBody(long roomId, String frequency, DayOfWeek dayOfWeek,
                              String startTime, String endTime, LocalDate startDate, LocalDate endDate) {
        return "{\"roomId\":" + roomId
                + ",\"frequency\":\"" + frequency + "\""
                + ",\"dayOfWeek\":\"" + dayOfWeek.name() + "\""
                + ",\"startTime\":\"" + startTime + "\""
                + ",\"endTime\":\"" + endTime + "\""
                + ",\"startDate\":\"" + startDate + "\""
                + (endDate == null ? "" : ",\"endDate\":\"" + endDate + "\"")
                + "}";
    }

    protected ResponseEntity<String> postRule(String token, long bandId, String jsonBody) {
        return post("/api/v1/bands/" + bandId + "/recurring-rules", jsonBody, token);
    }

    /**
     * 밴드를 PREMIUM 으로. 스토어 게이트웨이가 no-op 이라 구매 토큰만 있으면 검증을 통과한다(밴드장만).
     * 이미 PREMIUM 이면 만료일이 연장되며 그대로 200 이다.
     */
    protected void makePremium(String leaderToken, long bandId) {
        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/plan/google/verify",
                "{\"purchaseToken\":\"tokrec" + bandId + "\"}", leaderToken);
        int status = res.getStatusCode().value();
        if (status != 200 && status != 409) {
            throw new IllegalStateException("PREMIUM 전환 실패: " + res.getBody());
        }
    }

    /**
     * 규칙을 등록하고(201 기대) ruleId 를 돌려준다.
     *
     * <p>정기 일정은 PREMIUM 기능이라 <b>여기서 밴드를 PREMIUM 으로 만들어 준다.</b> 이 헬퍼를 쓰는
     * 테스트들은 "규칙이 있는 상태"가 필요한 것이지 요금제를 검증하려는 게 아니다.
     * 요금제 게이트 자체를 보는 테스트는 {@link #postRule}로 날것의 응답을 받아 확인한다.
     */
    protected long createRule(String token, long bandId, String jsonBody) {
        makePremium(token, bandId);
        ResponseEntity<String> res = postRule(token, bandId, jsonBody);
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("정기 규칙 등록 실패: " + res.getBody());
        }
        return data(res).get("rule").get("id").asLong();
    }

    protected JsonNode ruleDetail(String token, long bandId, long ruleId) {
        ResponseEntity<String> res = get("/api/v1/bands/" + bandId + "/recurring-rules/" + ruleId, token);
        if (res.getStatusCode().value() != 200) {
            throw new IllegalStateException("규칙 상세 조회 실패: " + res.getBody());
        }
        return data(res);
    }

    /** 개별 일정(회차) 상세. Phase 4 일정 API 를 그대로 쓴다. */
    protected ResponseEntity<String> getReservation(String token, long bandId, long reservationId) {
        return get("/api/v1/bands/" + bandId + "/reservations/" + reservationId, token);
    }
}
