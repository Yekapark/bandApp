plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.yeka.bandapp"
version = "0.0.1-SNAPSHOT"

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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.9.0")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Cloudflare R2(S3 호환) presigned URL 발급·업로드 검증용. S3Presigner(s3 아티팩트에 포함)는
    // 오프라인 서명이라 네트워크를 타지 않고, S3Client 는 업로드 검증(headObject)·정리(deleteObject)에만
    // 쓴다 — 파일 바이트는 서버를 지나지 않는다(BUILD_PLAN §2-5). 동기 호출만 하므로 기본 비동기 스택
    // (netty-nio-client)은 빼고 가벼운 url-connection-client 를 sync HTTP 클라이언트로 쓴다.
    implementation(platform("software.amazon.awssdk:bom:2.31.16"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation("software.amazon.awssdk:url-connection-client")

    // FCM 푸시 발송(Phase 9). BUILD_PLAN Phase 9 "FCM 연동" + DESIGN.md 스택(Firebase Cloud Messaging)에
    // 근거가 있는 의존성이다. 서비스 계정 키가 없으면 FcmPushSender 가 조용히 비활성으로 뜨고
    // (R2StorageClient 선례), 알림은 부가 기능이라 미설정이 일정 등록·정산을 깨지 않는다.
    implementation("com.google.firebase:firebase-admin:9.4.3")

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
