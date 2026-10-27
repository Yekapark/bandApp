package com.yeka.bandapp.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeka.bandapp.room.dto.CreateRoomRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 숫자 칸에 긴 문자열을 보내도 파싱이 바로 끝나야 한다. Jackson 2.21.4 는 이 입력에서 길이의 제곱으로 느려져
 * 10만 자에 1분이 넘게 걸렸다(CVE-2026-89407, LAUNCH_REVIEW G12). build.gradle.kts 의 버전 덮어쓰기가
 * 빠지면 여기서 시간 초과로 깨진다.
 */
class RoomRequestParsingTest {

    @Test
    void 좌표_칸의_긴_문자열은_바로_거절된다() {
        String json = "{\"name\":\"r\",\"lat\":\"" + "1".repeat(100_000) + "x\"}";

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(() -> new ObjectMapper().readValue(json, CreateRoomRequest.class))
                        .isInstanceOf(JsonProcessingException.class));
    }
}
