# Flyway 마이그레이션

- 파일명 규칙: `V{순번}__{설명}.sql` (예: `V1__auth.sql`, `V2__band.sql`)
- 스키마 변경은 반드시 이 디렉터리의 마이그레이션으로만 한다 (`spring.jpa.hibernate.ddl-auto: validate`)
- Phase 0에는 도메인 테이블이 없다. 첫 마이그레이션은 Phase 1(인증)에서 추가된다.
