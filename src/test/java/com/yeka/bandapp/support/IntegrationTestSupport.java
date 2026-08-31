package com.yeka.bandapp.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트 베이스. PostgreSQL은 {@link ServiceConnection}으로 자동 배선하고,
 * Redis는 actuator health 지표가 UP이 되도록 컨테이너를 띄워 연결한다.
 *
 * <p>JWT·카카오·파기배치 프로퍼티는 여기서 주입하므로 CI에 별도 시크릿이 필요 없다.
 * 실 배포는 {@code JwtProperties}의 {@code @Validated}가 시크릿을 강제한다.
 * 카카오 호출은 {@link KakaoTestConfig}의 {@code FakeKakaoClient}가 대체한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class IntegrationTestSupport {

    /** 테스트가 만료·변조 토큰을 직접 만들 수 있도록 공개한다. */
    public static final String TEST_JWT_SECRET = "integration-test-secret-key-0123456789-abcdefghijklmnop";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanState() {
        databaseCleaner.clean();
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.jwt.access-token-ttl", () -> "PT30M");
        registry.add("app.jwt.refresh-token-ttl", () -> "P14D");
        registry.add("app.kakao.app-id", () -> "999999");
        registry.add("app.kakao.admin-key", () -> "test-admin-key");
        // 통합 테스트 중 파기 배치가 끼어들지 않도록 비활성화한다("-").
        registry.add("app.withdrawal.purge-cron", () -> "-");
    }
}
