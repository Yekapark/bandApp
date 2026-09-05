package com.yeka.bandapp.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.support.FakePushSender;
import com.yeka.bandapp.support.PushTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 목록(피드). 발송된 알림이 보낸 문구 그대로 목록에 남고, 밴드 밖 사람은 못 본다.
 *
 * <p>읽음 여부는 서버에 없다(클라이언트가 기기에 마지막 확인 시각을 저장) — 그래서 여기서는
 * 목록 내용·정렬·페이징·권한만 본다.
 */
@Import(PushTestConfig.class)
class NotificationFeedIntegrationTest extends NotificationApiSupport {

    @Autowired
    private FakePushSender push;

    @BeforeEach
    void resetPush() {
        push.reset();
    }

    private JsonNode feed(String accessToken, long bandId, String query) {
        ResponseEntity<String> res =
                get(NOTIFICATIONS + "?bandId=" + bandId + query, accessToken);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        return data(res);
    }

    @Test
    void sent_notification_appears_in_the_feed_with_its_original_text() {
        String leader = signup("feed-a-l@band.app", "리더");
        String member = signup("feed-a-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        createReservation(leader, bandId, roomId, T10, T13);

        // 등록자(리더)는 자기 알림을 받지 않는다 — 멤버 목록에만 남는다.
        JsonNode mine = feed(member, bandId, "");
        assertThat(mine.get("notifications")).hasSize(1);
        JsonNode item = mine.get("notifications").get(0);
        assertThat(item.get("type").asText()).isEqualTo("RESERVATION_CREATED");
        assertThat(item.get("title").asText()).isEqualTo("새 합주 일정");
        assertThat(item.get("body").asText()).contains("합주 일정이 등록됐어요");
        assertThat(item.get("reservationId").asLong()).isPositive();

        assertThat(feed(leader, bandId, "").get("notifications")).isEmpty();
    }

    @Test
    void feed_is_newest_first_and_pages_by_cursor() {
        String leader = signup("feed-b-l@band.app", "리더");
        String member = signup("feed-b-m@band.app", "멤버");
        long bandId = createBand(leader, "국카스텐");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        createReservation(leader, bandId, roomId, T10, T13);
        createReservation(leader, bandId, roomId, T13, T16);

        JsonNode first = feed(member, bandId, "&size=1");
        assertThat(first.get("notifications")).hasSize(1);
        long firstId = first.get("notifications").get(0).get("id").asLong();
        assertThat(first.get("nextCursor").isNull()).isFalse();

        JsonNode second =
                feed(member, bandId, "&size=1&cursor=" + first.get("nextCursor").asLong());
        assertThat(second.get("notifications")).hasSize(1);
        long secondId = second.get("notifications").get(0).get("id").asLong();

        assertThat(firstId).isGreaterThan(secondId);   // 최신순
        assertThat(second.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void non_member_cannot_read_the_feed() {
        String leader = signup("feed-c-l@band.app", "리더");
        String stranger = signup("feed-c-x@band.app", "낯선이");
        long bandId = createBand(leader, "새소년");

        ResponseEntity<String> res = get(NOTIFICATIONS + "?bandId=" + bandId, stranger);

        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }
}
