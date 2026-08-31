# 밴드 합주 관리 앱 (backend)

Java 21 / Spring Boot 3.4 / PostgreSQL / Redis. 구현 명세는 `docs/BUILD_PLAN.md`.

## 로컬 실행

```bash
docker compose up --build          # app + postgres + redis
curl localhost:8080/actuator/health   # {"status":"UP"}
```

Swagger UI: http://localhost:8080/swagger-ui.html · OpenAPI: http://localhost:8080/v3/api-docs

## 빌드 / 테스트

```bash
./gradlew build     # 컴파일 + 통합 테스트 (Testcontainers 로 Postgres/Redis 기동, Docker 필요)
./gradlew test
```

로컬에 JDK 21이 없어도 Gradle toolchain(foojay resolver)이 자동으로 받아온다.
