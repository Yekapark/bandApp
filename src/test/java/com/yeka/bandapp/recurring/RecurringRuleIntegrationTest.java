package com.yeka.bandapp.recurring;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.recurring.service.RecurringRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 완료 기준:
 * <ul>
 *   <li>규칙 삭제 후에도 과거 일정(과 그에 연결될 정산 기록)이 남아 있다 — 미래 회차만 취소된다.</li>
 * </ul>
 * 여기에 권한 모드별 규칙 등록, 주간/격주/월간 회차 산출, KST 로컬 시각 변환, 겹침 경고,
 * 개별 회차 취소 후 규칙 유지, usageCount 증감, 타 밴드 격리를 함께 본다.
 */
class RecurringRuleIntegrationTest extends RecurringApiSupport {

    @Autowired
    RecurringRuleService recurringRuleService;

    // --- 완료 기준 -----------------------------------------------------------

    /**
     * 완료 기준 — 규칙을 삭제하면 아직 시작하지 않은 회차만 CANCELLED 가 되고,
     * 과거 회차는 상태·행 모두 그대로 남는다.
     */
    @Test
    void rule_deletion_keeps_past_occurrences_and_cancels_future_only() {
        String leader = signup("rec-del-l@band.app", "리더");
        long bandId = createBand(leader, "델리스파이스");
        long roomId = createRoom(leader, bandId, "{\"name\":\"합주실\"}");

        // 3주 전부터 시작하는 주간 규칙 → 과거 회차 몇 건 + 미래 회차 여러 건.
        LocalDate start = today().minusWeeks(3);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", start.plusDays(2).getDayOfWeek(), "15:00", "18:00", start, null));

        JsonNode occ = ruleDetail(leader, bandId, ruleId).get("occurrences");
        Instant now = Instant.now();
        List<Long> pastIds = new ArrayList<>();
        List<Long> futureIds = new ArrayList<>();
        for (JsonNode o : occ) {
            Instant startAt = Instant.parse(o.get("startAt").asText());
            assertThat(o.get("status").asText()).isEqualTo("CONFIRMED");
            (startAt.isBefore(now) ? pastIds : futureIds).add(o.get("id").asLong());
        }
        assertThat(pastIds).as("과거 회차가 있어야 완료 기준을 검증할 수 있다").isNotEmpty();
        assertThat(futureIds).as("미래 회차").isNotEmpty();

        assertThat(delete("/api/v1/bands/" + bandId + "/recurring-rules/" + ruleId, leader)
                .getStatusCode().value()).isEqualTo(204);

        // 규칙은 사라진다.
        assertThat(get("/api/v1/bands/" + bandId + "/recurring-rules/" + ruleId, leader)
                .getStatusCode().value()).isEqualTo(404);

        // 과거 회차: 행도 상태도 그대로.
        for (long id : pastIds) {
            ResponseEntity<String> res = getReservation(leader, bandId, id);
            assertThat(res.getStatusCode().value()).isEqualTo(200);
            assertThat(data(res).get("status").asText()).isEqualTo("CONFIRMED");
        }
        // 미래 회차: 행은 남고 상태만 CANCELLED.
        for (long id : futureIds) {
            ResponseEntity<String> res = getReservation(leader, bandId, id);
            assertThat(res.getStatusCode().value()).isEqualTo(200);
            assertThat(data(res).get("status").asText()).isEqualTo("CANCELLED");
        }
    }

    // --- 회차 산출 --------------------------------------------------------------

    /** 주간 규칙 — 8주분 이상 생성되고, 각 회차 startAt 이 KST 15:00(= 06:00Z)이다. */
    @Test
    void weekly_rule_generates_occurrences_at_kst_local_time() {
        String leader = signup("rec-wk-l@band.app", "리더");
        long bandId = createBand(leader, "브로콜리너마저");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        LocalDate firstDate = today().plusDays(1);
        ResponseEntity<String> res = postRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null));

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        JsonNode occ = data(res).get("occurrences");
        assertThat(occ.size()).isGreaterThanOrEqualTo(8);
        Instant prev = null;
        for (JsonNode o : occ) {
            assertThat(o.get("recurringRuleId").asLong()).isEqualTo(data(res).get("rule").get("id").asLong());
            assertThat(o.get("status").asText()).isEqualTo("CONFIRMED");
            assertThat(o.get("startAt").asText()).endsWith("T06:00:00Z");   // 15:00 KST
            assertThat(o.get("endAt").asText()).endsWith("T09:00:00Z");     // 18:00 KST
            Instant startAt = Instant.parse(o.get("startAt").asText());
            if (prev != null) {
                assertThat(Duration.between(prev, startAt).toDays()).isEqualTo(7);
            }
            prev = startAt;
        }
    }

    /** 격주 규칙 — 회차 간격이 14일이다. */
    @Test
    void biweekly_rule_spaces_occurrences_two_weeks_apart() {
        String leader = signup("rec-bw-l@band.app", "리더");
        long bandId = createBand(leader, "언니네이발관");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        LocalDate firstDate = today().plusDays(1);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "BIWEEKLY", firstDate.getDayOfWeek(), "19:00", "21:00", firstDate, null));

        JsonNode occ = ruleDetail(leader, bandId, ruleId).get("occurrences");
        assertThat(occ.size()).isGreaterThanOrEqualTo(2);
        Instant prev = null;
        for (JsonNode o : occ) {
            Instant startAt = Instant.parse(o.get("startAt").asText());
            if (prev != null) {
                assertThat(Duration.between(prev, startAt).toDays()).isEqualTo(14);
            }
            prev = startAt;
        }
    }

    /** 월간 규칙 — 회차가 모두 같은 요일, 같은 "주차"이고 서로 다른 달이다. */
    @Test
    void monthly_rule_repeats_same_week_ordinal() {
        String leader = signup("rec-mo-l@band.app", "리더");
        long bandId = createBand(leader, "9와숫자들");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        // 2개월 전 그 달 1일부터 시작하고 요일을 1일의 요일로 잡으면 anchor 주차가 항상 1 →
        // 어느 달에도 존재해 매달 회차가 생긴다(5주차 없는 달 스킵은 OccurrenceGeneratorTest 에서 본다).
        LocalDate start = today().minusMonths(2).withDayOfMonth(1);
        java.time.DayOfWeek dow = start.getDayOfWeek();
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "MONTHLY", dow, "14:00", "17:00", start, null));

        JsonNode occ = ruleDetail(leader, bandId, ruleId).get("occurrences");
        assertThat(occ.size()).isGreaterThanOrEqualTo(2);
        Integer ordinal = null;
        Integer prevMonthKey = null;
        for (JsonNode o : occ) {
            LocalDate d = Instant.parse(o.get("startAt").asText()).atZone(SEOUL).toLocalDate();
            assertThat(d.getDayOfWeek()).isEqualTo(dow);
            int thisOrdinal = ((d.getDayOfMonth() - 1) / 7) + 1;
            if (ordinal == null) {
                ordinal = thisOrdinal;
            } else {
                assertThat(thisOrdinal).isEqualTo(ordinal);
            }
            int monthKey = d.getYear() * 12 + d.getMonthValue();
            if (prevMonthKey != null) {
                assertThat(monthKey).isGreaterThan(prevMonthKey);
            }
            prevMonthKey = monthKey;
        }
    }

    // --- 권한 -----------------------------------------------------------------

    /** 규칙 등록 권한은 ANYONE 만 일반 멤버 허용, 나머지는 밴드장 전용. 생성 회차는 항상 CONFIRMED. */
    @Test
    void permission_modes_gate_rule_creation() {
        String leader = signup("rec-perm-l@band.app", "리더");
        String member = signup("rec-perm-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        LocalDate firstDate = today().plusDays(1);
        String body = ruleBody(roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null);

        // LEADER_ONLY(기본)
        assertThat(postRule(member, bandId, body).getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(postRule(member, bandId, body))).isEqualTo("NOT_BAND_LEADER");
        assertThat(postRule(leader, bandId, body).getStatusCode().value()).isEqualTo(201);

        // ANYONE
        setPermission(leader, bandId, "ANYONE");
        ResponseEntity<String> byMember = postRule(member, bandId, body);
        assertThat(byMember.getStatusCode().value()).isEqualTo(201);
        for (JsonNode o : data(byMember).get("occurrences")) {
            assertThat(o.get("status").asText()).isEqualTo("CONFIRMED");
        }

        // APPROVAL_REQUIRED — 규칙 등록 자체가 밴드장 전용
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        assertThat(postRule(member, bandId, body).getStatusCode().value()).isEqualTo(403);
        ResponseEntity<String> byLeader = postRule(leader, bandId, body);
        assertThat(byLeader.getStatusCode().value()).isEqualTo(201);
        for (JsonNode o : data(byLeader).get("occurrences")) {
            assertThat(o.get("status").asText()).isEqualTo("CONFIRMED");
        }
    }

    // --- 겹침 경고 -----------------------------------------------------------

    /** 규칙 회차가 기존 일정과 겹쳐도 201로 저장되고, overlaps 에 그 일정이 담긴다. */
    @Test
    void overlapping_occurrences_are_saved_with_warning() {
        String leader = signup("rec-ov-l@band.app", "리더");
        long bandId = createBand(leader, "장기하와얼굴들");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        // 규칙의 첫 회차와 같은 시각에 단발 일정을 먼저 만든다.
        LocalDate firstDate = today().plusDays(2);
        String startAt = firstDate + "T06:00:00Z";   // 15:00 KST
        String endAt = firstDate + "T09:00:00Z";     // 18:00 KST
        long clashId = createReservation(leader, bandId, roomId, startAt, endAt);

        ResponseEntity<String> res = postRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null));

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(data(res).get("occurrenceCount").asInt()).isGreaterThanOrEqualTo(1);
        List<Long> overlapIds = new ArrayList<>();
        data(res).get("overlaps").forEach(w -> overlapIds.add(w.get("id").asLong()));
        assertThat(overlapIds).contains(clashId);
    }

    // --- 개별 회차와 규칙의 독립성 -------------------------------------------

    /** 개별 회차를 취소해도 규칙은 유지되고, 배치가 다시 돌아도 그 회차는 되살아나지 않는다. */
    @Test
    void cancelled_occurrence_keeps_rule_and_is_not_regenerated() {
        String leader = signup("rec-canc-l@band.app", "리더");
        long bandId = createBand(leader, "실리카겔");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        LocalDate firstDate = today().plusDays(1);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null));

        JsonNode occ = ruleDetail(leader, bandId, ruleId).get("occurrences");
        int before = occ.size();
        long victimId = occ.get(1).get("id").asLong();
        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + victimId, leader)
                .getStatusCode().value()).isEqualTo(204);

        int created = recurringRuleService.extendRule(ruleId);
        assertThat(created).isZero();

        JsonNode after = ruleDetail(leader, bandId, ruleId).get("occurrences");
        assertThat(after.size()).isEqualTo(before);
        boolean victimStillCancelled = false;
        for (JsonNode o : after) {
            if (o.get("id").asLong() == victimId) {
                victimStillCancelled = o.get("status").asText().equals("CANCELLED");
            }
        }
        assertThat(victimStillCancelled).isTrue();
    }

    // --- usageCount --------------------------------------------------------

    /** 회차 생성만큼 usageCount 가 오르고, 규칙 삭제 시 미래 회차 수만큼 내린다. */
    @Test
    void usage_count_tracks_generated_and_cancelled_occurrences() {
        String leader = signup("rec-uc-l@band.app", "리더");
        long bandId = createBand(leader, "새소년");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        LocalDate start = today().minusWeeks(2);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", start.plusDays(1).getDayOfWeek(), "15:00", "18:00", start, null));

        JsonNode occ = ruleDetail(leader, bandId, ruleId).get("occurrences");
        int total = occ.size();
        Instant now = Instant.now();
        long past = 0;
        for (JsonNode o : occ) {
            if (Instant.parse(o.get("startAt").asText()).isBefore(now)) {
                past++;
            }
        }
        assertThat(usageCount(leader, bandId, roomId)).isEqualTo(total);

        delete("/api/v1/bands/" + bandId + "/recurring-rules/" + ruleId, leader);

        assertThat(usageCount(leader, bandId, roomId)).isEqualTo((int) past);
    }

    // --- 타 밴드 격리 -----------------------------------------------------

    @Test
    void other_band_cannot_see_rule() {
        String a = signup("rec-iso-a@band.app", "A리더");
        String b = signup("rec-iso-b@band.app", "B리더");
        long bandA = createBand(a, "밴드A");
        long bandB = createBand(b, "밴드B");
        long roomA = createRoom(a, bandA, "{\"name\":\"방\"}");
        LocalDate firstDate = today().plusDays(1);
        long ruleId = createRule(a, bandA, ruleBody(
                roomA, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null));

        // B가 자기 밴드 경로로 A의 ruleId 조회 → 404
        assertThat(get("/api/v1/bands/" + bandB + "/recurring-rules/" + ruleId, b)
                .getStatusCode().value()).isEqualTo(404);
        // B가 A의 밴드 경로로 조회 → 403 (비멤버)
        ResponseEntity<String> crossBand = get("/api/v1/bands/" + bandA + "/recurring-rules/" + ruleId, b);
        assertThat(crossBand.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(crossBand)).isEqualTo("NOT_BAND_MEMBER");
    }
}
