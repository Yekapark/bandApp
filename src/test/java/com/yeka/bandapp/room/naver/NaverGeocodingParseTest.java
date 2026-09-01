package com.yeka.bandapp.room.naver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨테이너가 필요 없는 순수 단위 테스트. 네이버 지오코딩 응답(JSON)에서 좌표를 꺼내는 로직만 검증한다.
 * 네이버는 경도를 {@code x}, 위도를 {@code y}에 문자열로 담아 준다.
 */
class NaverGeocodingParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extracts_lat_from_y_and_lng_from_x_of_first_address() throws Exception {
        String json = """
                {
                  "status": "OK",
                  "addresses": [
                    {"roadAddress": "서울특별시 마포구 ...", "x": "126.9236", "y": "37.5559"},
                    {"roadAddress": "다른 후보", "x": "127.0", "y": "37.0"}
                  ]
                }
                """;

        Optional<Coordinates> result = NaverGeocodingClient.parseFirstCoordinates(mapper.readTree(json));

        assertThat(result).contains(new Coordinates(37.5559, 126.9236));
    }

    @Test
    void empty_when_no_address_matched() throws Exception {
        String json = """
                {"status": "OK", "addresses": [], "errorMessage": ""}
                """;

        assertThat(NaverGeocodingClient.parseFirstCoordinates(mapper.readTree(json))).isEmpty();
    }

    @Test
    void empty_when_coordinates_are_not_numbers() throws Exception {
        String json = """
                {"addresses": [{"x": "", "y": ""}]}
                """;

        assertThat(NaverGeocodingClient.parseFirstCoordinates(mapper.readTree(json))).isEmpty();
    }

    @Test
    void empty_when_body_is_null() {
        assertThat(NaverGeocodingClient.parseFirstCoordinates(null)).isEmpty();
    }
}
