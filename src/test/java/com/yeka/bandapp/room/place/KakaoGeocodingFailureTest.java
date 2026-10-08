package com.yeka.bandapp.room.place;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 {@link KakaoGeocodingClient} 를 로컬 가짜 카카오 서버에 붙여 실패를 주입한다(QA ROOM-02).
 * 통합 테스트의 {@code FakeGeocodingClient} 는 "빈 결과" 만 흉내 내므로, 진짜 HTTP 실패(5xx·인증 오류·
 * 타임아웃·깨진 응답)가 예외로 새어 나가 합주실 등록을 500 으로 만들지 않는지는 여기서 본다.
 */
class KakaoGeocodingFailureTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private KakaoGeocodingClient clientRespondingWith(int status, String contentType, String body, long delayMs)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            } catch (IOException ignored) {
                // 타임아웃 시험에서는 클라이언트가 먼저 끊는다.
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new KakaoGeocodingClient(
                new KakaoLocalProperties(baseUrl, "test-key", Duration.ofMillis(500), Duration.ofMillis(300)),
                RestClient.builder());
    }

    @Test
    void server_error_yields_empty() throws IOException {
        var client = clientRespondingWith(500, "application/json", "{\"msg\":\"down\"}", 0);
        assertThat(client.geocode("서울 마포구 와우산로 1")).isEmpty();
    }

    @Test
    void auth_error_yields_empty() throws IOException {
        var client = clientRespondingWith(401, "application/json", "{\"code\":-401}", 0);
        assertThat(client.geocode("서울 마포구 와우산로 1")).isEmpty();
    }

    @Test
    void read_timeout_yields_empty() throws IOException {
        var client = clientRespondingWith(200, "application/json", "{\"documents\":[]}", 1500);
        assertThat(client.geocode("서울 마포구 와우산로 1")).isEmpty();
    }

    @Test
    void broken_json_yields_empty() throws IOException {
        var client = clientRespondingWith(200, "application/json", "{not json", 0);
        assertThat(client.geocode("서울 마포구 와우산로 1")).isEmpty();
    }

    @Test
    void unreachable_host_yields_empty() {
        // 열려 있지 않은 포트 — 연결 거부.
        var client = new KakaoGeocodingClient(
                new KakaoLocalProperties("http://127.0.0.1:1", "test-key", Duration.ofMillis(500), Duration.ofMillis(300)),
                RestClient.builder());
        assertThat(client.geocode("서울 마포구 와우산로 1")).isEmpty();
    }
}
