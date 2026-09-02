package com.yeka.bandapp.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 설정 조회·변경. 리마인더 시점의 정규화(중복 제거·정렬)와 범위·개수 검증을 본다.
 */
class NotificationSettingIntegrationTest extends NotificationApiSupport {

    @Test
    void first_get_returns_defaults_and_creates_the_row() {
        String user = signup("noti-set-def@band.app", "유저");

        JsonNode settings = getSettings(user);
        assertThat(settings.get("pushEnabled").asBoolean()).isTrue();
        assertThat(settings.get("reminderOffsets")).hasSize(1);
        assertThat(settings.get("reminderOffsets").get(0).asInt()).isEqualTo(60);

        // 두 번째 조회도 같은 값(행이 이미 있으므로).
        assertThat(getSettings(user).get("reminderOffsets").get(0).asInt()).isEqualTo(60);
    }

    @Test
    void update_replaces_and_normalizes_offsets() {
        String user = signup("noti-set-upd@band.app", "유저");

        JsonNode updated = data(put("/api/v1/notifications/settings",
                "{\"pushEnabled\":false,\"reminderOffsets\":[60,10,60,30]}", user));
        assertThat(updated.get("pushEnabled").asBoolean()).isFalse();
        assertThat(toIntArray(updated.get("reminderOffsets"))).containsExactly(10, 30, 60);

        // 재조회로 저장된 값 확인.
        assertThat(toIntArray(getSettings(user).get("reminderOffsets"))).containsExactly(10, 30, 60);
    }

    @Test
    void empty_offsets_means_no_reminders() {
        String user = signup("noti-set-empty@band.app", "유저");
        JsonNode updated = data(put("/api/v1/notifications/settings",
                "{\"pushEnabled\":true,\"reminderOffsets\":[]}", user));
        assertThat(updated.get("reminderOffsets")).isEmpty();
    }

    @Test
    void offset_below_one_is_rejected() {
        String user = signup("noti-set-lo@band.app", "유저");
        ResponseEntity<String> res = put("/api/v1/notifications/settings",
                "{\"pushEnabled\":true,\"reminderOffsets\":[0]}", user);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("INVALID_REMINDER_OFFSET");
    }

    @Test
    void offset_over_the_ceiling_is_rejected() {
        String user = signup("noti-set-hi@band.app", "유저");
        ResponseEntity<String> res = put("/api/v1/notifications/settings",
                "{\"pushEnabled\":true,\"reminderOffsets\":[100000]}", user);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("INVALID_REMINDER_OFFSET");
    }

    @Test
    void too_many_distinct_offsets_is_rejected() {
        String user = signup("noti-set-many@band.app", "유저");
        ResponseEntity<String> res = put("/api/v1/notifications/settings",
                "{\"pushEnabled\":true,\"reminderOffsets\":[1,2,3,4,5,6]}", user);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("TOO_MANY_REMINDER_OFFSETS");
    }

    @Test
    void settings_require_authentication() {
        assertThat(get("/api/v1/notifications/settings").getStatusCode().value()).isEqualTo(401);
    }

    private static int[] toIntArray(JsonNode array) {
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).asInt();
        }
        return result;
    }
}
