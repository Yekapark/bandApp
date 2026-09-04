package com.yeka.bandapp.room.place;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * {@link GeocodingClient}의 실제 구현 — 카카오 로컬 주소 검색
 * ({@code GET /v2/local/search/address.json?query=주소&size=1}, 헤더 {@code Authorization: KakaoAK {REST API 키}}).
 *
 * <p>장소 검색({@link KakaoLocalSearchClient})과 <b>같은 REST API 키·같은 설정</b>
 * ({@link KakaoLocalProperties})을 쓴다. 검색과 지오코딩의 제공자가 같아야 등록 폼 지도에서 확인한
 * 위치와 저장되는 좌표가 어긋나지 않는다.
 *
 * <p>동기 HTTP는 {@code spring-web}에 이미 포함된 {@link RestClient}로 한다(의존성 추가 없음).
 * 카카오 장애가 합주실 등록 요청을 무한 대기시키지 않도록 connect/read 타임아웃을 명시한다.
 *
 * <p>이 클래스는 <b>어떤 실패도 예외로 올리지 않는다</b>. 키 미설정·통신 실패·검색 결과 0건을 모두
 * {@code Optional.empty()} + 로그로 처리한다. 좌표는 부가 정보이고, 등록은 주소만으로도 성공해야
 * 하기 때문이다(Phase 3 완료 기준).
 */
@Component
public class KakaoGeocodingClient implements GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoGeocodingClient.class);

    private static final String AUTH_HEADER_PREFIX = "KakaoAK ";

    private final KakaoLocalProperties properties;
    private final RestClient restClient;

    public KakaoGeocodingClient(KakaoLocalProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        this.restClient = builder.baseUrl(properties.apiBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public Optional<Coordinates> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        if (!properties.isConfigured()) {
            log.debug("카카오 REST API 키가 없어 좌표 없이 진행한다.");
            return Optional.empty();
        }
        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v2/local/search/address.json")
                            .queryParam("query", address)
                            .queryParam("size", 1)
                            .build())
                    .header("Authorization", AUTH_HEADER_PREFIX + properties.restApiKey())
                    .retrieve()
                    .body(JsonNode.class);
            Optional<Coordinates> found = parseFirstCoordinates(body);
            if (found.isEmpty()) {
                log.info("주소에 해당하는 좌표를 찾지 못했다. address={}", address);
            }
            return found;
        } catch (RestClientException e) {
            // 등록 자체를 막지 않는다 — 좌표 없이 주소만 저장된다.
            log.warn("카카오 지오코딩 호출 실패 address={}", address, e);
            return Optional.empty();
        }
    }

    /**
     * 응답의 첫 번째 후보에서 좌표를 꺼낸다. 카카오는 경도를 {@code x}, 위도를 {@code y}에 WGS84 십진
     * <b>문자열</b>로 담아 준다. 형식이 조금이라도 어긋나면 비어 있는 결과로 취급한다.
     *
     * <p>컨테이너 없이 검증할 수 있도록 패키지 공개로 둔다({@code KakaoGeocodingParseTest}).
     */
    static Optional<Coordinates> parseFirstCoordinates(JsonNode body) {
        if (body == null) {
            return Optional.empty();
        }
        JsonNode documents = body.path("documents");
        if (!documents.isArray() || documents.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = documents.get(0);
        if (!first.hasNonNull("x") || !first.hasNonNull("y")) {
            return Optional.empty();
        }
        try {
            double lng = Double.parseDouble(first.get("x").asText());
            double lat = Double.parseDouble(first.get("y").asText());
            if (!isValidWgs84(lat, lng)) {
                return Optional.empty();
            }
            return Optional.of(new Coordinates(lat, lng));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** NaN·무한대·좌표 범위를 벗어난 값은 저장하지 않는다(비정상 응답이 DB·JSON 직렬화를 깨뜨리지 않게). */
    private static boolean isValidWgs84(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && Math.abs(lat) <= 90.0 && Math.abs(lng) <= 180.0;
    }
}
