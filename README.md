# 밴드 합주 관리 앱 (backend)

Java 21 / Spring Boot 3.4 / PostgreSQL / Redis. 구현 명세는 `docs/BUILD_PLAN.md`.

## 로컬 실행

```bash
docker compose up --build          # app + postgres + redis
curl localhost:8080/actuator/health   # {"status":"UP"}
```

Swagger UI: http://localhost:8080/swagger-ui.html · OpenAPI: http://localhost:8080/v3/api-docs

## 배포

운영 배포·백업·복구 절차는 [docs/DEPLOY.md](docs/DEPLOY.md). 운영 스택은 `docker-compose.prod.yml`,
`main` 의 CI 가 통과하면 `.github/workflows/deploy.yml` 이 자동으로 배포한다.

## 빌드 / 테스트

```bash
./gradlew build     # 컴파일 + 통합 테스트 (Testcontainers 로 Postgres/Redis 기동, Docker 필요)
./gradlew test
```

로컬에 JDK 21이 없어도 Gradle toolchain(foojay resolver)이 자동으로 받아온다.
