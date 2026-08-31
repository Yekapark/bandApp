plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.yeka.bandapp"
version = "0.0.1-SNAPSHOT"

// Spring Boot 3.4.1 BOM은 Testcontainers를 1.20.4로 고정한다. 최신 Docker Desktop 대응이
// 나은 1.21.3으로 올린다. (test 스코프 전용, 운영 산출물엔 영향 없음)
//
// 참고: 이 개발 PC(Windows, Docker Desktop 29.x, containerd 이미지 스토어 ON)에서는
// 이 버전으로도 `./gradlew test`가 named pipe `/info` 호출에서 HTTP 400을 받아 실패한다.
// Docker Desktop 설정에서 "Use containerd for pulling and storing images"를 끄거나
// "Allow the default Docker socket to be used"를 켜면 해소된다.
// docker/docker compose CLI 와 CI(ubuntu-latest)는 영향 없다 — 자동 테스트 판정은 CI로 한다.
extra["testcontainers.version"] = "1.21.3"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
