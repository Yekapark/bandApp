package com.yeka.bandapp.band;

import com.yeka.bandapp.board.service.StorageKeys;
import com.yeka.bandapp.plan.PlanApiSupport;
import com.yeka.bandapp.support.FakeStorageClient;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 밴드 삭제 — 밴드에 딸린 모든 데이터가 실제로 사라지는지.
 *
 * <p>이 테스트의 요점은 <b>빠뜨린 테이블이 없는지</b>다. 밴드 하나에 일정·정산·참석·셋리스트·
 * 게시글·첨부·신고·합주실·정기규칙·초대·요금제·쿠폰사용·알림이력을 전부 만들어 두고 지운 뒤,
 * 표에 적힌 테이블마다 행이 0인지 확인한다. 새 테이블이 생겼는데 삭제에서 빠지면 여기서 드러난다.
 */
@Import(StorageTestConfig.class)
class BandDeletionIntegrationTest extends PlanApiSupport {

    /** 밴드에 딸려 있어 함께 지워져야 하는 테이블과, 밴드까지 거슬러 올라가는 조건. */
    private static final List<String> BAND_OWNED_COUNTS = List.of(
            "select count(*) from bands where id = %d",
            "select count(*) from band_members where band_id = %d",
            "select count(*) from band_invites where band_id = %d",
            "select count(*) from band_plans where band_id = %d",
            "select count(*) from plan_coupon_redemptions where band_id = %d",
            "select count(*) from rooms where band_id = %d",
            "select count(*) from reservations where band_id = %d",
            "select count(*) from recurring_rules where band_id = %d",
            "select count(*) from board_posts where band_id = %d",
            "select count(*) from notification_dispatches where band_id = %d",
            "select count(*) from media_attachments m where m.board_post_id in "
                    + "(select id from board_posts where band_id = %d)",
            "select count(*) from settlements s where s.reservation_id in "
                    + "(select id from reservations where band_id = %d)",
            "select count(*) from settlement_shares sh where sh.settlement_id in "
                    + "(select s.id from settlements s where s.reservation_id in "
                    + "(select id from reservations where band_id = %d))",
            "select count(*) from reservation_attendances a where a.reservation_id in "
                    + "(select id from reservations where band_id = %d)",
            "select count(*) from setlist_items i where i.reservation_id in "
                    + "(select id from reservations where band_id = %d)");

    @Autowired
    private FakeStorageClient storage;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void deleting_a_band_removes_every_row_that_belonged_to_it() {
        String leader = signup("bd-a@band.app", "리더");
        Fixture f = fullyPopulatedBand(leader, "지울밴드");

        assertThat(deleteBand(leader, f.bandId, "지울밴드").getStatusCode().value()).isEqualTo(204);

        for (String sql : BAND_OWNED_COUNTS) {
            assertThat(count(sql, f.bandId))
                    .withFailMessage("삭제 후에도 행이 남았다: %s", sql.formatted(f.bandId))
                    .isZero();
        }
    }

    @Test
    void deleting_a_band_removes_its_r2_objects_by_prefix() {
        String leader = signup("bd-b@band.app", "리더");
        Fixture f = fullyPopulatedBand(leader, "저장소밴드");
        assertThat(storage.objectExists(f.mediaKey)).isTrue();

        assertThat(deleteBand(leader, f.bandId, "저장소밴드").getStatusCode().value()).isEqualTo(204);

        assertThat(storage.deletedPrefixes()).contains(StorageKeys.bandPrefix(f.bandId));
        assertThat(storage.objectExists(f.mediaKey)).isFalse();
    }

    /** POST·MEDIA 신고는 대상이 사라져 처리 불가능해지므로 지운다. USER 신고는 사람에 대한 것이라 남긴다. */
    @Test
    void post_and_media_reports_go_but_user_reports_stay() {
        String leader = signup("bd-c1@band.app", "리더");
        String member = signup("bd-c2@band.app", "멤버");
        Fixture f = fullyPopulatedBand(leader, "신고밴드");
        join(member, issueInvite(leader, f.bandId, null));
        report(member, "POST", f.postId);
        report(member, "MEDIA", f.mediaId);
        report(member, "USER", myUserId(leader));

        assertThat(deleteBand(leader, f.bandId, "신고밴드").getStatusCode().value()).isEqualTo(204);

        assertThat(count("select count(*) from reports where target_type <> 'USER'", f.bandId)).isZero();
        assertThat(count("select count(*) from reports where target_type = 'USER'", f.bandId)).isEqualTo(1);
    }

    /** 밴드와 무관한 데이터는 남아야 한다 — 사람↔사람 차단은 다른 밴드에서도 유효하다. */
    @Test
    void user_scoped_data_survives() {
        String leader = signup("bd-d1@band.app", "리더");
        String other = signup("bd-d2@band.app", "다른사람");
        Fixture f = fullyPopulatedBand(leader, "차단밴드");
        long otherId = myUserId(other);
        assertThat(post("/api/v1/users/me/blocks", "{\"blockedUserId\":" + otherId + "}", leader)
                .getStatusCode().value()).isEqualTo(201);

        assertThat(deleteBand(leader, f.bandId, "차단밴드").getStatusCode().value()).isEqualTo(204);

        assertThat(count("select count(*) from user_blocks", f.bandId)).isEqualTo(1);
        assertThat(count("select count(*) from users", f.bandId)).isEqualTo(2);
    }

    @Test
    void other_bands_are_untouched() {
        String leader = signup("bd-e@band.app", "리더");
        Fixture doomed = fullyPopulatedBand(leader, "지울밴드");
        Fixture kept = fullyPopulatedBand(leader, "남길밴드");

        assertThat(deleteBand(leader, doomed.bandId, "지울밴드").getStatusCode().value()).isEqualTo(204);

        for (String sql : BAND_OWNED_COUNTS) {
            assertThat(count(sql, kept.bandId))
                    .withFailMessage("남아 있어야 할 밴드의 행이 사라졌다: %s", sql.formatted(kept.bandId))
                    .isPositive();
        }
        assertThat(storage.objectExists(kept.mediaKey)).isTrue();
    }

    @Test
    void member_who_is_not_leader_cannot_delete() {
        String leader = signup("bd-f1@band.app", "리더");
        String member = signup("bd-f2@band.app", "멤버");
        Fixture f = fullyPopulatedBand(leader, "권한밴드");
        join(member, issueInvite(leader, f.bandId, null));

        ResponseEntity<String> res = deleteBand(member, f.bandId, "권한밴드");

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_LEADER");
        assertThat(count("select count(*) from bands where id = %d", f.bandId)).isEqualTo(1);
    }

    @Test
    void outsider_cannot_delete() {
        String leader = signup("bd-g1@band.app", "리더");
        String outsider = signup("bd-g2@band.app", "외부인");
        Fixture f = fullyPopulatedBand(leader, "격리밴드");

        ResponseEntity<String> res = deleteBand(outsider, f.bandId, "격리밴드");

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_MEMBER");
        assertThat(count("select count(*) from bands where id = %d", f.bandId)).isEqualTo(1);
    }

    @Test
    void wrong_confirmation_name_deletes_nothing() {
        String leader = signup("bd-h@band.app", "리더");
        Fixture f = fullyPopulatedBand(leader, "확인밴드");

        ResponseEntity<String> res = deleteBand(leader, f.bandId, "확인밴드아님");

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("BAND_NAME_MISMATCH");
        assertThat(count("select count(*) from bands where id = %d", f.bandId)).isEqualTo(1);
        assertThat(storage.objectExists(f.mediaKey)).isTrue();
    }

    /**
     * 가장 중요한 케이스 — R2 삭제가 실패하면 <b>DB 를 전혀 건드리지 않고</b> 502 로 끝나야 한다.
     * 반대 순서였다면 키를 잃어버려 R2 에 영구 고아가 남는다.
     */
    @Test
    void storage_failure_aborts_before_touching_the_database() {
        String leader = signup("bd-i@band.app", "리더");
        Fixture f = fullyPopulatedBand(leader, "실패밴드");
        storage.failNextDeleteByPrefix();

        ResponseEntity<String> res = deleteBand(leader, f.bandId, "실패밴드");

        assertThat(res.getStatusCode().value()).isEqualTo(502);
        for (String sql : BAND_OWNED_COUNTS) {
            assertThat(count(sql, f.bandId))
                    .withFailMessage("저장소 실패인데 DB 가 지워졌다: %s", sql.formatted(f.bandId))
                    .isPositive();
        }

        // 다시 시도하면 성공한다 — 접두사 삭제는 멱등이다.
        assertThat(deleteBand(leader, f.bandId, "실패밴드").getStatusCode().value()).isEqualTo(204);
        assertThat(count("select count(*) from bands where id = %d", f.bandId)).isZero();
    }

    // ---------- 픽스처 ----------

    private record Fixture(long bandId, long roomId, long reservationId, long postId,
                           long mediaId, String mediaKey) {
    }

    /** 밴드 하나에 삭제 대상 테이블이 모두 채워지도록 데이터를 만든다. */
    private Fixture fullyPopulatedBand(String leader, String bandName) {
        long bandId = createBand(leader, bandName);
        issueInvite(leader, bandId, null);                       // band_invites
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);   // band_plans → PREMIUM

        long roomId = createRoom(leader, bandId, "합주실");
        long reservationId = createReservation(leader, bandId, roomId);
        createRecurringRule(leader, bandId, roomId);
        respondAttendance(leader, bandId, reservationId);        // reservation_attendances
        addSetlistItem(leader, bandId, reservationId);           // setlist_items
        createSettlement(leader, bandId, reservationId);         // settlements + settlement_shares

        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);
        String mediaKey = jdbc.queryForObject(
                "select storage_key from media_attachments where id = ?", String.class, mediaId);

        insertDispatch(bandId, myUserId(leader));                // notification_dispatches (API 없음)
        insertCouponRedemption(bandId, myUserId(leader));        // plan_coupon_redemptions

        return new Fixture(bandId, roomId, reservationId, postId, mediaId, mediaKey);
    }

    /** {@code createRoom} 은 RoomApiSupport 계열에 있고 이 테스트는 Board/Plan 계열이라 직접 만든다. */
    private long createRoom(String token, long bandId, String name) {
        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/rooms",
                "{\"name\":\"" + name + "\",\"address\":\"서울시 어딘가\"}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        return data(res).get("id").asLong();
    }

    private long createReservation(String token, long bandId, long roomId) {
        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/reservations",
                "{\"roomId\":" + roomId + ",\"startAt\":\"2026-10-10T10:00:00Z\","
                        + "\"endAt\":\"2026-10-10T13:00:00Z\",\"cost\":30000}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        return data(res).get("reservation").get("id").asLong();
    }

    private void createRecurringRule(String token, long bandId, long roomId) {
        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/recurring-rules",
                "{\"roomId\":" + roomId + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":\"SATURDAY\","
                        + "\"startTime\":\"10:00\",\"endTime\":\"13:00\","
                        + "\"startDate\":\"2026-10-01\",\"endDate\":\"2026-12-31\"}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
    }

    private void respondAttendance(String token, long bandId, long reservationId) {
        ResponseEntity<String> res = put(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/attendances/" + myUserId(token),
                "{\"status\":\"ATTENDING\"}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }

    private void addSetlistItem(String token, long bandId, long reservationId) {
        ResponseEntity<String> res = post(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/setlist",
                "{\"title\":\"위잉위잉\"}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
    }

    private void createSettlement(String token, long bandId, long reservationId) {
        ResponseEntity<String> res = post(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/settlement",
                "{\"totalAmount\":30000,\"splitType\":\"EQUAL\"}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
    }

    private void report(String token, String targetType, long targetId) {
        ResponseEntity<String> res = post("/api/v1/reports",
                "{\"targetType\":\"" + targetType + "\",\"targetId\":" + targetId
                        + ",\"reason\":\"확인용 신고\"}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
    }

    /** 알림 이력은 발송 경로로만 생겨서 직접 넣는다. band_id 는 FK 가 없는 순수 컬럼이다(V11). */
    private void insertDispatch(long bandId, long userId) {
        jdbc.update("insert into notification_dispatches "
                        + "(user_id, type, target_id, variant, created_at, band_id, title, body) "
                        + "values (?, 'RESERVATION_CREATED', ?, 0, ?, ?, '새 합주 일정', '토요일 10시')",
                userId, bandId, Timestamp.from(Instant.now()), bandId);
    }

    private void insertCouponRedemption(long bandId, long userId) {
        jdbc.update("insert into plan_coupons (code, grant_days, max_uses, created_at) "
                + "values ('BD" + bandId + "TEST', 30, null, now())");
        jdbc.update("insert into plan_coupon_redemptions (coupon_id, band_id, redeemed_by, redeemed_at) "
                        + "select id, ?, ?, now() from plan_coupons where code = ?",
                bandId, userId, "BD" + bandId + "TEST");
    }

    private ResponseEntity<String> deleteBand(String token, long bandId, String confirmName) {
        return post("/api/v1/bands/" + bandId + "/delete",
                "{\"confirmName\":\"" + confirmName + "\"}", token);
    }

    private long count(String sqlTemplate, long bandId) {
        Long n = jdbc.queryForObject(sqlTemplate.formatted(bandId), Long.class);
        return n == null ? 0 : n;
    }
}
