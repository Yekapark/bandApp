package com.yeka.bandapp.band;

import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 초대 딥링크의 무인증 웹 표면: 랜딩 페이지 + Universal Link / App Link 검증 파일.
 */
class InviteDeepLinkIntegrationTest extends ApiIntegrationTest {

    @Test
    void landing_page_is_public_html_with_the_code_and_store_links() {
        ResponseEntity<String> res = get("/invite/ABCD2345");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(res.getBody()).contains("ABCD2345");
        assertThat(res.getBody()).contains("bandapp://invite/");
        assertThat(res.getBody()).contains("apps.apple.com");
        assertThat(res.getBody()).contains("play.google.com");
    }

    @Test
    void malformed_code_in_landing_path_is_404() {
        assertThat(get("/invite/not-a-code").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void apple_app_site_association_is_served() {
        ResponseEntity<String> res = get("/.well-known/apple-app-site-association");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(body(res).at("/applinks/details/0/appID").asText()).isEqualTo("ABCDE12345.com.yeka.bandule");
        assertThat(body(res).at("/applinks/details/0/paths/0").asText()).isEqualTo("/invite/*");
    }

    @Test
    void android_asset_links_are_served() {
        ResponseEntity<String> res = get("/.well-known/assetlinks.json");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(body(res).at("/0/target/package_name").asText()).isEqualTo("com.yeka.bandule");
        assertThat(body(res).at("/0/target/sha256_cert_fingerprints/0").asText()).isEqualTo("AA:BB:CC");
    }

    /** produces 를 걸면 여기서 406 이 났다 — 검증 파일 페처가 특이한 Accept 를 보내도 200 이어야 한다. */
    @Test
    void well_known_files_ignore_a_restrictive_accept_header() {
        assertThat(getWithAccept("/.well-known/apple-app-site-association", MediaType.TEXT_PLAIN)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(getWithAccept("/.well-known/assetlinks.json", MediaType.APPLICATION_XML)
                .getStatusCode().value()).isEqualTo(200);
    }

    private ResponseEntity<String> getWithAccept(String path, MediaType accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(accept));
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
