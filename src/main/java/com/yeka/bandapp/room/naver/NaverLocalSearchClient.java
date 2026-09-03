package com.yeka.bandapp.room.naver;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link PlaceSearchClient}의 실제 구현 — 네이버 개발자센터 지역검색
 * ({@code GET /v1/search/local.json?query=검색어&display=5}).
 *
 * <p>동기 HTTP는 {@code spring-web}에 이미 포함된 {@link RestClient}로 한다(의존성 추가 없음).
 * 네이버 장애가 폼 입력을 붙잡지 않도록 connect/read 타임아웃을 짧게 명시한다.
 *
 * <p>{@link NaverGeocodingClient}와 같은 방침으로 <b>어떤 실패도 예외로 올리지 않는다</b>. 키 미설정·
 * 통신 실패·결과 0건을 모두 빈 리스트 + 로그로 처리한다.
 */
@Component
public class NaverLocalSearchClient implements PlaceSearchClient {

    private static final Logger log = LoggerFactory.getLogger(NaverLocalSearchClient.class);

    private static final String CLIENT_ID_HEADER = "X-Naver-Client-Id";
    private static final String CLIENT_SECRET_HEADER = "X-Naver-Client-Secret";
    private static final int DISPLAY = 5;

    /** 네이버 지역검색이 주는 mapx/mapy 는 WGS84 좌표 × 1e7 정수. */
    private static final double COORD_SCALE = 1e7;
    // 대한민국 대략 경계 — 변환이 애매한 값(구형 KATEC 등)을 걸러낸다.
    private static final double KR_LAT_MIN = 33.0;
    private static final double KR_LAT_MAX = 39.7;
    private static final double KR_LNG_MIN = 124.0;
    private static final double KR_LNG_MAX = 132.0;

    private final NaverSearchProperties properties;
    private final RestClient restClient;

    public NaverLocalSearchClient(NaverSearchProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        this.restClient = builder.baseUrl(properties.apiBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public List<PlaceSuggestion> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (!properties.isConfigured()) {
            log.debug("네이버 지역검색 키가 없어 빈 결과를 돌려준다.");
            return List.of();
        }
        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/search/local.json")
                            .queryParam("query", query)
                            .queryParam("display", DISPLAY)
                            .build())
                    .header(CLIENT_ID_HEADER, properties.clientId())
                    .header(CLIENT_SECRET_HEADER, properties.clientSecret())
                    .retrieve()
                    .body(JsonNode.class);
            List<PlaceSuggestion> found = parseItems(body);
            if (found.isEmpty()) {
                log.info("지역검색 결과가 없다. query={}", query);
            }
            return found;
        } catch (RestClientException e) {
            log.warn("네이버 지역검색 호출 실패 query={}", query, e);
            return List.of();
        }
    }

    /**
     * 응답의 {@code items[]}를 결과 목록으로 옮긴다. {@code title}은 {@code <b>} 강조 태그가 섞여 오므로
     * 태그를 걷어내고, {@code mapx}/{@code mapy}는 WGS84로 되돌려(범위를 벗어나면 좌표 없이) 담는다.
     *
     * <p>컨테이너 없이 검증할 수 있도록 패키지 공개로 둔다({@code NaverLocalSearchParseTest}).
     */
    static List<PlaceSuggestion> parseItems(JsonNode body) {
        if (body == null) {
            return List.of();
        }
        JsonNode items = body.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return List.of();
        }
        List<PlaceSuggestion> out = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            String name = stripTags(item.path("title").asText(""));
            if (name.isBlank()) {
                continue;
            }
            String roadAddress = blankToNull(item.path("roadAddress").asText(""));
            String address = blankToNull(item.path("address").asText(""));
            String category = blankToNull(item.path("category").asText(""));
            String phone = blankToNull(item.path("telephone").asText(""));
            double[] coord = parseCoord(item.path("mapx").asText(""), item.path("mapy").asText(""));
            out.add(new PlaceSuggestion(
                    name, roadAddress, address, category, phone,
                    coord == null ? null : coord[0],
                    coord == null ? null : coord[1]));
        }
        return out;
    }

    /** @return {@code [lat, lng]} 또는 변환이 애매하면 {@code null}. */
    private static double[] parseCoord(String mapx, String mapy) {
        if (mapx == null || mapx.isBlank() || mapy == null || mapy.isBlank()) {
            return null;
        }
        try {
            double lng = Double.parseDouble(mapx) / COORD_SCALE;
            double lat = Double.parseDouble(mapy) / COORD_SCALE;
            if (Double.isFinite(lat) && Double.isFinite(lng)
                    && lat >= KR_LAT_MIN && lat <= KR_LAT_MAX
                    && lng >= KR_LNG_MIN && lng <= KR_LNG_MAX) {
                return new double[]{lat, lng};
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stripTags(String raw) {
        return raw.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
