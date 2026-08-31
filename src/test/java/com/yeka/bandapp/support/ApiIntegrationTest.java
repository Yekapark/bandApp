package com.yeka.bandapp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * HTTP 헬퍼가 붙은 통합 테스트 베이스. 응답은 {@code ApiResponse} 봉투를 트리로 읽는다.
 */
public abstract class ApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper objectMapper;

    protected ResponseEntity<String> post(String path, String json) {
        return post(path, json, null);
    }

    protected ResponseEntity<String> post(String path, String json, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    protected ResponseEntity<String> get(String path) {
        return rest.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    protected ResponseEntity<String> get(String path, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    protected JsonNode body(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("응답 본문을 JSON으로 읽지 못했습니다: " + response.getBody(), e);
        }
    }

    protected String errorCode(ResponseEntity<String> response) {
        return body(response).at("/error/code").asText();
    }
}
