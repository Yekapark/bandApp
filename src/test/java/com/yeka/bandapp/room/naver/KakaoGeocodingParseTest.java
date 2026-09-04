package com.yeka.bandapp.room.naver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨테이너가 필요 없는 순수 단위 테스트. 카카오 로컬 주소검색 응답(JSON)에서 좌표를 꺼내는 로직만
 * 검증한다. 카카오는 경도를 {@code x}, 위도를 {@code y}에 WGS84 십진 문자열로 담아 준다.
 */
class KakaoGeocodingParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extracts_lat_from_y_and_lng_from_x_of_first_document() throws Exception {
        String json = """
                {
                  "documents": [
                    {"address_name": "서울 마포구 와우산로 1", "x": "126.9236", "y": "37.5559"},
                    {"address_name": "다른 후보", "x": "127.0", "y": "37.0"}
                  ],
                  "meta": {"total_count": 2}
                }
                """;

        Optional<Coordinates> result = KakaoGeocodingClient.parseFirstCoordinates(mapper.readTree(json));

        assertThat(result).contains(new Coordinates(37.5559, 126.9236));
    }

    @Test
    void empty_when_no_address_matched() throws Exception {
        String json = """
                {"documents": [], "meta": {"total_count": 0}}
                """;

        assertThat(KakaoGeocodingClient.parseFirstCoordinates(mapper.readTree(json))).isEmpty();
    }

    @Test
    void empty_when_coordinates_are_not_numbers() throws Exception {
        String json = """
                {"documents": [{"x": "", "y": ""}]}
                """;

        assertThat(KakaoGeocodingClient.parseFirstCoordinates(mapper.readTree(json))).isEmpty();
    }

    @Test
    void empty_when_body_is_null() {
        assertThat(KakaoGeocodingClient.parseFirstCoordinates(null)).isEmpty();
    }

    @Test
    void empty_when_document_has_no_coordinates() throws Exception {
        String json = """
                {"documents": [{"address_name": "좌표 없는 후보"}]}
                """;

        assertThat(KakaoGeocodingClient.parseFirstCoordinates(mapper.readTree(json))).isEmpty();
    }

    @Test
    void empty_when_coordinates_are_out_of_wgs84_range_or_not_finite() throws Exception {
        assertThat(KakaoGeocodingClient.parseFirstCoordinates(
                mapper.readTree("""
                        {"documents": [{"x": "999", "y": "37.5"}]}
                        """))).isEmpty();
        assertThat(KakaoGeocodingClient.parseFirstCoordinates(
                mapper.readTree("""
                        {"documents": [{"x": "126.9", "y": "91"}]}
                        """))).isEmpty();
        assertThat(KakaoGeocodingClient.parseFirstCoordinates(
                mapper.readTree("""
                        {"documents": [{"x": "Infinity", "y": "NaN"}]}
                        """))).isEmpty();
    }
}
