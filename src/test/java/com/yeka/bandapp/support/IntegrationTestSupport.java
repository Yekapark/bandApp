package com.yeka.bandapp.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 베이스.
 *
 * <p><b>싱글턴 컨테이너</b>: PostgreSQL·Redis를 static 초기화 블록에서 딱 한 번 띄우고
 * JVM 수명 동안 재사용한다(Testcontainers 의 Ryuk 이 종료 시 정리). {@code @Testcontainers}
 * +{@code @Container}로 컨테이너를 테스트 클래스마다 start/stop 하면, 스프링이 캐시한
 * ApplicationContext 가 이미 멈춘 컨테이너의 포트를 계속 가리켜 두 번째 통합 테스트
 * 클래스부터 커넥션이 거부된다(첫 컨텍스트를 만든 클래스와 {@code @Import}로 별도
 * 컨텍스트를 쓰는 클래스만 통과). 싱글턴으로 두면 어느 컨텍스트든 살아 있는 같은
 * 컨테이너를 가리킨다.
 *
 * <p>JWT·카카오·파기배치 프로퍼티도 여기서 주입하므로 CI에 별도 시크릿이 필요 없다.
 * 실 배포는 {@code JwtProperties}의 {@code @Validated}가 시크릿을 강제한다.
 * 카카오 호출은 {@link KakaoTestConfig}의 {@code FakeKakaoClient}가 대체한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestSupport {

    /** 테스트가 만료·변조 토큰을 직접 만들 수 있도록 공개한다. */
    public static final String TEST_JWT_SECRET = "integration-test-secret-key-0123456789-abcdefghijklmnop";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanState() {
        databaseCleaner.clean();
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.jwt.access-token-ttl", () -> "PT30M");
        registry.add("app.jwt.refresh-token-ttl", () -> "P14D");
        registry.add("app.kakao.app-id", () -> "999999");
        registry.add("app.kakao.admin-key", () -> "test-admin-key");
        // 네이버 지오코딩: 실제 호출은 FakeGeocodingClient 가 대체하므로 값은 형식만 맞추면 된다.
        registry.add("app.naver.client-id", () -> "test-ncp-id");
        registry.add("app.naver.client-secret", () -> "test-ncp-secret");
        // 통합 테스트 중 파기 배치가 끼어들지 않도록 비활성화한다("-").
        registry.add("app.withdrawal.purge-cron", () -> "-");
        // 정기 일정 회차 연장 배치도 비활성화한다 — 테스트는 서비스 메서드를 직접 호출해 검증한다.
        registry.add("app.recurring.extend-cron", () -> "-");
        // 알림·미디어 정리 배치도 비활성화한다("-"). 테스트는 각 서비스 메서드를 직접 호출해 검증한다.
        registry.add("app.notification.reminder-cron", () -> "-");
        registry.add("app.notification.nudge-cron", () -> "-");
        registry.add("app.media.expire-cron", () -> "-");
        registry.add("app.media.orphan-cron", () -> "-");
        registry.add("app.plan.expire-cron", () -> "-");
        // 초대 딥링크: 링크·검증 파일의 값을 고정해 assertion 을 쓸 수 있게 한다.
        // android-package 를 빠뜨렸다가 application.yml 의 운영 기본값이 바뀌는 순간
        // InviteDeepLinkIntegrationTest 가 깨졌다. 검증 파일에 들어가는 값은 여기서 전부 고정한다.
        registry.add("app.deeplink.base-url", () -> "https://band.test");
        registry.add("app.deeplink.scheme", () -> "bandapp");
        registry.add("app.deeplink.ios-app-id", () -> "ABCDE12345.com.yeka.bandule");
        registry.add("app.deeplink.android-package", () -> "com.yeka.bandule");
        registry.add("app.deeplink.android-sha256-cert-fingerprints", () -> "AA:BB:CC");
        // 레이트리밋: 테스트가 초과를 빠르게 검증할 수 있게 낮춘다. 단일 테스트가 이보다 많이
        // 호출하지 않도록 유지한다(매 테스트 전 Redis flush 로 카운터는 초기화된다).
        registry.add("app.ratelimit.invite-join-per-user-per-min", () -> "10");
        registry.add("app.ratelimit.invite-join-per-ip-per-min", () -> "10");
        registry.add("app.ratelimit.auth-per-ip-per-min", () -> "30");
        registry.add("app.ratelimit.geocode-per-user-per-min", () -> "10");
        registry.add("app.ratelimit.media-upload-per-user-per-min", () -> "10");
        registry.add("app.ratelimit.report-per-user-per-min", () -> "10");
        registry.add("app.ratelimit.device-token-per-user-per-min", () -> "10");
        // R2: 통합 테스트는 FakeStorageClient(@Primary)가 대체하므로 값은 비워 둔다.
        // R2Properties 는 TTL 기본값(5~15분 clamp)만 쓰인다.
        // FCM: FakePushSender(@Primary)가 대체하므로 값은 비워 둔다(FcmPushSender 는 미설정으로 조용히 뜬다).
    }
}
