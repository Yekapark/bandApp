package com.yeka.bandapp.recurring;

import com.yeka.bandapp.recurring.service.RecurringRuleService;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정기 일정이 PREMIUM 전용이라는 것과, <b>요금제가 내려가도 이미 만든 규칙은 계속 돈다</b>는 것.
 *
 * <p>뒤쪽이 더 중요하다. 구독이 끝났다고 회차 생성까지 멈추면 사용자가 모르는 사이 다음 주 합주가
 * 사라진다 — 미디어가 예고 없이 지워지는 것과 같은 종류의 사고다.
 */
class RecurringPlanGateIntegrationTest extends RecurringApiSupport {

    @Autowired
    private RecurringRuleService recurringRuleService;

    @Autowired
    private ReservationRepository reservationRepository;

    private String body(long roomId, LocalDate startDate) {
        return ruleBody(roomId, "WEEKLY", DayOfWeek.WEDNESDAY, "19:00", "21:00", startDate, null);
    }

    @Test
    @DisplayName("FREE 밴드는 정기 규칙을 만들 수 없다 — 403 PLAN_REQUIRED")
    void free_band_cannot_create_rule() {
        String leader = signup("gate-free@band.app", "무료리더");
        long bandId = createBand(leader, "무료밴드");
        long roomId = createRoom(leader, bandId, "{\"name\":\"합주실\"}");

        ResponseEntity<String> res = postRule(leader, bandId, body(roomId, today().plusDays(1)));

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("PLAN_REQUIRED");
        assertThat(reservationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("PREMIUM 이면 만들 수 있다")
    void premium_band_can_create_rule() {
        String leader = signup("gate-prem@band.app", "유료리더");
        long bandId = createBand(leader, "프리미엄밴드");
        long roomId = createRoom(leader, bandId, "{\"name\":\"합주실\"}");
        makePremium(leader, bandId);

        ResponseEntity<String> res = postRule(leader, bandId, body(roomId, today().plusDays(1)));

        assertThat(res.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("PREMIUM 을 해지해도 이미 만든 규칙의 회차 생성은 계속된다")
    void existing_rule_keeps_generating_after_downgrade() {
        String leader = signup("gate-down@band.app", "해지리더");
        long bandId = createBand(leader, "해지밴드");
        long roomId = createRoom(leader, bandId, "{\"name\":\"합주실\"}");
        long ruleId = createRule(leader, bandId, body(roomId, today().plusDays(1)));

        int before = reservationRepository.findAll().size();
        assertThat(before).isGreaterThan(0);

        // 해지 — 요금제만 FREE 로 내려간다.
        ResponseEntity<String> cancelled = post("/api/v1/bands/" + bandId + "/plan/cancel", "{}", leader);
        assertThat(cancelled.getStatusCode().value()).isEqualTo(200);

        // 회차 이어 만들기 배치가 도는 것과 같은 경로.
        recurringRuleService.extendRule(ruleId);

        assertThat(reservationRepository.findAll().size()).isGreaterThanOrEqualTo(before);

        // 다만 새 규칙은 이제 못 만든다.
        ResponseEntity<String> denied = postRule(leader, bandId, body(roomId, today().plusDays(2)));
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
    }
}
