package com.yeka.bandapp.room.naver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 컨테이너가 필요 없는 순수 단위 테스트. 네이버 지역검색 응답(JSON)에서 후보 목록을 뽑는 로직만 검증한다.
 * 네이버는 {@code title}에 {@code <b>} 강조 태그를 섞어 주고, 좌표를 {@code mapx}(경도)/{@code mapy}(위도)에
 * WGS84 × 1e7 정수 문자열로 담아 준다.
 */
class NaverLocalSearchParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void strips_bold_tags_and_converts_coordinates() throws Exception {
        String json = """
                {
                  "items": [
                    {
                      "title": "사운드박스 <b>합주실</b>",
                      "category": "음악,댄스>연습실,연습공간",
                      "telephone": "",
                      "address": "서울특별시 마포구 서교동 400-1",
                      "roadAddress": "서울특별시 마포구 와우산로 29길 12",
                      "mapx": "1269226910",
                      "mapy": "375561234"
                    }
                  ]
                }
                """;

        List<PlaceSuggestion> out = NaverLocalSearchClient.parseItems(mapper.readTree(json));

        assertThat(out).hasSize(1);
        PlaceSuggestion first = out.get(0);
        assertThat(first.name()).isEqualTo("사운드박스 합주실");
        assertThat(first.roadAddress()).isEqualTo("서울특별시 마포구 와우산로 29길 12");
        assertThat(first.address()).isEqualTo("서울특별시 마포구 서교동 400-1");
        assertThat(first.lat()).isCloseTo(37.5561234, within(1e-7));
        assertThat(first.lng()).isCloseTo(126.922691, within(1e-7));
    }

    @Test
    void empty_list_when_no_items_or_null_body() throws Exception {
        assertThat(NaverLocalSearchClient.parseItems(null)).isEmpty();
        assertThat(NaverLocalSearchClient.parseItems(mapper.readTree("{\"items\":[]}"))).isEmpty();
        assertThat(NaverLocalSearchClient.parseItems(mapper.readTree("{}"))).isEmpty();
    }

    @Test
    void keeps_result_but_drops_coordinates_when_out_of_korea_range() throws Exception {
        // KATEC 등 변환이 안 되는 값 → 좌표는 null 이지만 후보 자체는 남는다.
        String json = """
                {
                  "items": [
                    {"title": "먼 곳 합주실", "roadAddress": "어딘가", "address": "",
                     "mapx": "310396", "mapy": "552986"}
                  ]
                }
                """;

        List<PlaceSuggestion> out = NaverLocalSearchClient.parseItems(mapper.readTree(json));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("먼 곳 합주실");
        assertThat(out.get(0).lat()).isNull();
        assertThat(out.get(0).lng()).isNull();
    }

    @Test
    void skips_entries_without_a_title() throws Exception {
        String json = """
                {
                  "items": [
                    {"title": "", "roadAddress": "주소만 있음"},
                    {"title": "정상 <b>업소</b>", "roadAddress": "서울"}
                  ]
                }
                """;

        List<PlaceSuggestion> out = NaverLocalSearchClient.parseItems(mapper.readTree(json));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("정상 업소");
    }

    @Test
    void blank_optional_fields_become_null() throws Exception {
        String json = """
                {"items": [{"title": "이름만 <b>있는</b> 곳", "roadAddress": "", "address": "",
                            "category": "", "telephone": "", "mapx": "", "mapy": ""}]}
                """;

        PlaceSuggestion only = NaverLocalSearchClient.parseItems(mapper.readTree(json)).get(0);

        assertThat(only.name()).isEqualTo("이름만 있는 곳");
        assertThat(only.roadAddress()).isNull();
        assertThat(only.address()).isNull();
        assertThat(only.category()).isNull();
        assertThat(only.phone()).isNull();
        assertThat(only.lat()).isNull();
    }
}
