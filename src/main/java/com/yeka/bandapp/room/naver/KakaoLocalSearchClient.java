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
 * {@link PlaceSearchClient}의 실제 구현 — 카카오 로컬 키워드 장소 검색
 * ({@code GET /v2/local/search/keyword.json?query=검색어&size=5}, 헤더 {@code Authorization: KakaoAK {REST API 키}}).
 *
 * <p>동기 HTTP는 {@code spring-web}에 이미 포함된 {@link RestClient}로 한다(의존성 추가 없음).
 * 카카오 장애가 폼 입력을 붙잡지 않도록 connect/read 타임아웃을 짧게 명시한다.
 *
 * <p>{@link KakaoGeocodingClient}와 같은 방침으로 <b>어떤 실패도 예외로 올리지 않는다</b>. 키 미설정·
 * 통신 실패·결과 0건을 모두 빈 리스트 + 로그로 처리한다.
 */
@Component
public class KakaoLocalSearchClient implements PlaceSearchClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalSearchClient.class);

    private static final String AUTH_HEADER_PREFIX = "KakaoAK ";
    private static final int SIZE = 5;

    // 대한민국 대략 경계 — 좌표가 이 범위를 벗어나면 좌표 없이 담는다(이름·주소로 등록엔 지장 없음).
    private static final double KR_LAT_MIN = 33.0;
    private static final double KR_LAT_MAX = 39.7;
    private static final double KR_LNG_MIN = 124.0;
    private static final double KR_LNG_MAX = 132.0;

    private final KakaoLocalProperties properties;
    private final RestClient restClient;

    public KakaoLocalSearchClient(KakaoLocalProperties properties, RestClient.Builder builder) {
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
            log.debug("카카오 로컬 검색 키가 없어 빈 결과를 돌려준다.");
            return List.of();
        }
        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", SIZE)
                            .build())
                    .header("Authorization", AUTH_HEADER_PREFIX + properties.restApiKey())
                    .retrieve()
                    .body(JsonNode.class);
            List<PlaceSuggestion> found = parseDocuments(body);
            if (found.isEmpty()) {
                log.info("카카오 로컬 검색 결과가 없다. query={}", query);
            }
            return found;
        } catch (RestClientException e) {
            log.warn("카카오 로컬 검색 호출 실패 query={}", query, e);
            return List.of();
        }
    }

    /**
     * 응답의 {@code documents[]}를 결과 목록으로 옮긴다. 카카오는 {@code place_name}에 강조 태그를 섞지
     * 않고, 좌표를 {@code x}(경도)/{@code y}(위도)에 WGS84 십진 문자열 그대로 준다.
     *
     * <p>컨테이너 없이 검증할 수 있도록 패키지 공개로 둔다({@code KakaoLocalSearchParseTest}).
     */
    static List<PlaceSuggestion> parseDocuments(JsonNode body) {
        if (body == null) {
            return List.of();
        }
        JsonNode docs = body.path("documents");
        if (!docs.isArray() || docs.isEmpty()) {
            return List.of();
        }
        List<PlaceSuggestion> out = new ArrayList<>(docs.size());
        for (JsonNode doc : docs) {
            String name = blankToNull(doc.path("place_name").asText(""));
            if (name == null) {
                continue;
            }
            String roadAddress = blankToNull(doc.path("road_address_name").asText(""));
            String address = blankToNull(doc.path("address_name").asText(""));
            String category = blankToNull(doc.path("category_group_name").asText(""));
            if (category == null) {
                category = blankToNull(doc.path("category_name").asText(""));
            }
            String phone = blankToNull(doc.path("phone").asText(""));
            double[] coord = parseCoord(doc.path("x").asText(""), doc.path("y").asText(""));
            out.add(new PlaceSuggestion(
                    name, roadAddress, address, category, phone,
                    coord == null ? null : coord[0],
                    coord == null ? null : coord[1]));
        }
        return out;
    }

    /** @return {@code [lat, lng]} 또는 값이 없거나 한국 범위를 벗어나면 {@code null}. */
    private static double[] parseCoord(String x, String y) {
        if (x == null || x.isBlank() || y == null || y.isBlank()) {
            return null;
        }
        try {
            double lng = Double.parseDouble(x);
            double lat = Double.parseDouble(y);
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
