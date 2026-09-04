package com.yeka.bandapp.room.naver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 컨테이너가 필요 없는 순수 단위 테스트. 카카오 로컬 키워드 검색 응답(JSON)에서 후보 목록을 뽑는
 * 로직만 검증한다. 카카오는 {@code place_name}에 강조 태그를 섞지 않고, 좌표를 {@code x}(경도)/
 * {@code y}(위도)에 WGS84 십진 문자열로 담아 준다.
 */
class KakaoLocalSearchParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void maps_fields_and_coordinates() throws Exception {
        String json = """
                {
                  "documents": [
                    {
                      "place_name": "사운드박스 합주실",
                      "category_name": "예술,스포츠 > 문화시설 > 연습실",
                      "category_group_name": "",
                      "phone": "02-334-1082",
                      "address_name": "서울 마포구 서교동 400-1",
                      "road_address_name": "서울 마포구 와우산로29길 12",
                      "x": "126.922691",
                      "y": "37.5561234"
                    }
                  ]
                }
                """;

        List<PlaceSuggestion> out = KakaoLocalSearchClient.parseDocuments(mapper.readTree(json));

        assertThat(out).hasSize(1);
        PlaceSuggestion first = out.get(0);
        assertThat(first.name()).isEqualTo("사운드박스 합주실");
        assertThat(first.roadAddress()).isEqualTo("서울 마포구 와우산로29길 12");
        assertThat(first.address()).isEqualTo("서울 마포구 서교동 400-1");
        assertThat(first.category()).isEqualTo("예술,스포츠 > 문화시설 > 연습실");
        assertThat(first.phone()).isEqualTo("02-334-1082");
        assertThat(first.lat()).isCloseTo(37.5561234, within(1e-7));
        assertThat(first.lng()).isCloseTo(126.922691, within(1e-7));
    }

    @Test
    void prefers_category_group_name_when_present() throws Exception {
        String json = """
                {"documents": [{"place_name": "그루브 카페", "category_group_name": "카페",
                                "category_name": "음식점 > 카페", "road_address_name": "서울"}]}
                """;

        PlaceSuggestion only = KakaoLocalSearchClient.parseDocuments(mapper.readTree(json)).get(0);

        assertThat(only.category()).isEqualTo("카페");
    }

    @Test
    void empty_list_when_no_documents_or_null_body() throws Exception {
        assertThat(KakaoLocalSearchClient.parseDocuments(null)).isEmpty();
        assertThat(KakaoLocalSearchClient.parseDocuments(mapper.readTree("{\"documents\":[]}"))).isEmpty();
        assertThat(KakaoLocalSearchClient.parseDocuments(mapper.readTree("{}"))).isEmpty();
    }

    @Test
    void keeps_result_but_drops_coordinates_when_out_of_korea_range() throws Exception {
        String json = """
                {"documents": [{"place_name": "먼 곳 합주실", "road_address_name": "어딘가",
                                "address_name": "", "x": "2.3488", "y": "48.8534"}]}
                """;

        List<PlaceSuggestion> out = KakaoLocalSearchClient.parseDocuments(mapper.readTree(json));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("먼 곳 합주실");
        assertThat(out.get(0).lat()).isNull();
        assertThat(out.get(0).lng()).isNull();
    }

    @Test
    void skips_entries_without_a_place_name() throws Exception {
        String json = """
                {
                  "documents": [
                    {"place_name": "", "road_address_name": "주소만 있음"},
                    {"place_name": "정상 업소", "road_address_name": "서울"}
                  ]
                }
                """;

        List<PlaceSuggestion> out = KakaoLocalSearchClient.parseDocuments(mapper.readTree(json));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("정상 업소");
    }

    @Test
    void blank_optional_fields_become_null() throws Exception {
        String json = """
                {"documents": [{"place_name": "이름만 있는 곳", "road_address_name": "", "address_name": "",
                                "category_group_name": "", "category_name": "", "phone": "", "x": "", "y": ""}]}
                """;

        PlaceSuggestion only = KakaoLocalSearchClient.parseDocuments(mapper.readTree(json)).get(0);

        assertThat(only.name()).isEqualTo("이름만 있는 곳");
        assertThat(only.roadAddress()).isNull();
        assertThat(only.address()).isNull();
        assertThat(only.category()).isNull();
        assertThat(only.phone()).isNull();
        assertThat(only.lat()).isNull();
    }
}
