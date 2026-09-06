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
        // `bandapp` 이 아니라 `bandule` 이어야 한다 - `bandapp://` 는 네이버 밴드가 쓰는
        // 주소라, 그대로 두면 초대 링크가 네이버 밴드 앱을 연다(실제로 그랬다).
        assertThat(res.getBody()).contains("bandule://invite/");
        assertThat(res.getBody()).doesNotContain("bandapp://");
        assertThat(res.getBody()).contains("apps.apple.com");
        assertThat(res.getBody()).contains("play.google.com");
    }

    /**
     * 스토어로 <b>자동 전송하지 않는다.</b>
     *
     * <p>예전에는 1.2초 뒤 스토어로 보냈다. 앱이 열렸는지를 경과 시간으로 짐작하는 방식이라
     * 맞지 않았고, 앱이 정상으로 열린 뒤에도 뒤에 남은 페이지가 스토어로 이동해
     * "항목을 찾을 수 없습니다"(게시 전이라)가 떴다. 설치 버튼은 페이지에 이미 있으므로
     * 자동 이동은 이득보다 손해가 크다.
     */
    @Test
    void landing_page_does_not_auto_redirect_to_the_store() {
        assertThat(get("/invite/ABCD2345").getBody()).doesNotContain("setTimeout");
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
