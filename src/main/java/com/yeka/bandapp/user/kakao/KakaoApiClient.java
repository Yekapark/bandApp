package com.yeka.bandapp.user.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link KakaoClient}의 실제 구현. 동기 HTTP는 {@code spring-web}에 이미 포함된 {@link RestClient}로 한다.
 * 카카오 장애가 탈퇴 요청을 무한 대기시키지 않도록 connect/read 타임아웃을 명시한다.
 */
@Component
public class KakaoApiClient implements KakaoClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoApiClient.class);

    private final KakaoProperties properties;
    private final RestClient restClient;

    public KakaoApiClient(KakaoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        this.restClient = builder.baseUrl(properties.apiBaseUrl()).requestFactory(factory).build();
    }

    @Override
    public KakaoTokenInfo fetchTokenInfo(String kakaoAccessToken) {
        ensureConfigured();
        JsonNode body = getWithBearer("/v1/user/access_token_info", kakaoAccessToken);
        String id = body.hasNonNull("id") ? body.get("id").asText() : null;
        Long appId = body.hasNonNull("app_id") ? body.get("app_id").asLong() : null;
        return new KakaoTokenInfo(id, appId);
    }

    @Override
    public KakaoUserInfo fetchUserInfo(String kakaoAccessToken) {
        ensureConfigured();
        JsonNode body = getWithBearer("/v2/user/me", kakaoAccessToken);
        String id = body.hasNonNull("id") ? body.get("id").asText() : null;

        JsonNode account = body.path("kakao_account");
        String email = null;
        if (account.path("is_email_valid").asBoolean(false)
                && !account.path("email_needs_agreement").asBoolean(false)
                && account.hasNonNull("email")) {
            email = account.get("email").asText();
        }
        String nickname = account.path("profile").hasNonNull("nickname")
                ? account.path("profile").get("nickname").asText()
                : null;

        return new KakaoUserInfo(id, email, nickname);
    }

    @Override
    public void unlink(String socialId) {
        ensureConfigured();
        try {
            restClient.post()
                    .uri("/v1/user/unlink")
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("target_id_type=user_id&target_id=" + socialId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("카카오 unlink 실패 socialId={}", socialId, e);
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    private JsonNode getWithBearer(String uri, String bearerToken) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new BusinessException(ErrorCode.KAKAO_TOKEN_INVALID);
                    })
                    .body(JsonNode.class);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("카카오 API 호출 실패 uri={}", uri, e);
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.KAKAO_NOT_CONFIGURED);
        }
    }
}
