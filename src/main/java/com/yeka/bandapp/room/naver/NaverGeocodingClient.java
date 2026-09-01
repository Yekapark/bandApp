package com.yeka.bandapp.room.naver;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * {@link GeocodingClient}의 실제 구현 — 네이버 클라우드 플랫폼 Maps Geocoding
 * ({@code GET /map-geocode/v2/geocode?query=주소}).
 *
 * <p>동기 HTTP는 {@code spring-web}에 이미 포함된 {@link RestClient}로 한다(의존성 추가 없음).
 * 네이버 장애가 합주실 등록 요청을 무한 대기시키지 않도록 connect/read 타임아웃을 명시한다.
 *
 * <p>이 클래스는 <b>어떤 실패도 예외로 올리지 않는다</b>. 키 미설정·통신 실패·검색 결과 0건을 모두
 * {@code Optional.empty()} + {@code WARN} 로그로 처리한다. 좌표는 부가 정보이고, 등록은 주소만으로도
 * 성공해야 하기 때문이다(Phase 3 완료 기준).
 */
@Component
public class NaverGeocodingClient implements GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(NaverGeocodingClient.class);

    private static final String CLIENT_ID_HEADER = "x-ncp-apigw-api-key-id";
    private static final String CLIENT_SECRET_HEADER = "x-ncp-apigw-api-key";

    private final NaverProperties properties;
    private final RestClient restClient;

    public NaverGeocodingClient(NaverProperties properties, RestClient.Builder builder) {
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
            log.debug("네이버 지오코딩 키가 없어 좌표 없이 진행한다.");
            return Optional.empty();
        }
        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/map-geocode/v2/geocode")
                            .queryParam("query", address)
                            .build())
                    .header(CLIENT_ID_HEADER, properties.clientId())
                    .header(CLIENT_SECRET_HEADER, properties.clientSecret())
                    .retrieve()
                    .body(JsonNode.class);
            Optional<Coordinates> found = parseFirstCoordinates(body);
            if (found.isEmpty()) {
                log.info("주소에 해당하는 좌표를 찾지 못했다. address={}", address);
            }
            return found;
        } catch (RestClientException e) {
            // 등록 자체를 막지 않는다 — 좌표 없이 주소만 저장된다.
            log.warn("네이버 지오코딩 호출 실패 address={}", address, e);
            return Optional.empty();
        }
    }

    /**
     * 응답의 첫 번째 주소 후보에서 좌표를 꺼낸다. 네이버는 경도를 {@code x}, 위도를 {@code y}에
     * <b>문자열</b>로 담아 준다. 형식이 조금이라도 어긋나면 비어 있는 결과로 취급한다.
     *
     * <p>컨테이너 없이 검증할 수 있도록 패키지 공개로 둔다({@code NaverGeocodingParseTest}).
     */
    static Optional<Coordinates> parseFirstCoordinates(JsonNode body) {
        if (body == null) {
            return Optional.empty();
        }
        JsonNode addresses = body.path("addresses");
        if (!addresses.isArray() || addresses.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = addresses.get(0);
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
