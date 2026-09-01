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

    /** 규칙을 등록하고(201 기대) ruleId 를 돌려준다. */
    protected long createRule(String token, long bandId, String jsonBody) {
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
