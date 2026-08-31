# 밴드 합주 관리 앱

밴드원들이 밴드 단위로 합주 일정을 기록·공유하고 비용을 정산하는 서비스.
Java 21 / Spring Boot 3.x / PostgreSQL / Redis 백엔드. 클라이언트는 Flutter.

## 핵심 전제

이 앱은 합주실 예약을 대행하지 않는다. 실제 예약은 전화·카톡 등 앱 밖에서
이루어지고, 앱에서의 "일정 등록"은 이미 완료된 예약을 기록하는 것이다.

- 합주실 예약 시스템 연동, 가용시간 조회, 예약 전송 코드는 작성하지 않는다.
- 기록용 도구이므로 시간대가 겹치는 일정도 막지 않고 저장한다.
  겹침은 응답에 경고로만 포함하고, 등록을 거부하지 않는다.

## 문서

- **`docs/BUILD_PLAN.md` — 구현의 단일 출처.** 도메인 모델, Phase별 작업과
  완료 기준, 금지사항. 작업 시작 전 "절대 하지 말 것" 섹션을 확인한다.
- `docs/DESIGN.md` — 설계 배경과 결정 근거 (왜 이 스택인지, 인프라 구성).
  구현 명세는 담지 않는다.
- `docs/BACKLOG.md` — 배포 요건과 디자인 작업 메모 (사람용 참고).

도메인 모델이나 구현 범위가 문서 간에 달라 보이면 `BUILD_PLAN.md`를 따르고,
불일치를 발견하면 사용자에게 알린다.

## 명령어

```bash
./gradlew build      # 빌드
./gradlew test       # 테스트
docker compose up    # 로컬 실행 (app + postgres + redis)
```

## 규칙

- 스키마 변경은 Flyway 마이그레이션으로만 관리한다 (`ddl-auto: validate`)
- Controller는 DTO만 다루고 엔티티를 직접 노출하지 않는다
- 엔티티 Lombok 허용: `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder`
- 엔티티 Lombok 금지: `@Data`, `@Setter`, `@EqualsAndHashCode`, `@ToString`, `@AllArgsConstructor`
  (양방향 연관관계 무한 재귀, 지연로딩 강제 초기화 문제)
- `equals`/`hashCode`가 필요하면 id 기반으로 직접 작성한다
- 상태 변경은 setter가 아니라 의미 있는 메서드로 표현한다 (`reservation.approve()`, `member.delegateLeadership()`)
- DTO는 Java `record`, 엔티티↔DTO 변환은 DTO 쪽 정적 팩토리 메서드로 한다
- 모든 API는 요청자의 밴드 소속 여부를 검증한다 (타 밴드 데이터 접근 차단)
- 대용량 파일은 presigned URL로 클라이언트가 R2에 직접 업로드한다.
  백엔드를 경유하는 파일 스트림을 만들지 않는다.
- 스펙에 없는 라이브러리 추가나 엔티티 변경은 먼저 제안하고 승인받는다
- 각 Phase는 완료 기준의 통합 테스트가 통과해야 다음으로 넘어간다
- 각 Phase 완료 시 `docs/progress/phase-NN-*.md`에 상세 기록을 남긴다.
  독자는 코드를 직접 안 쓰는 지시자이므로, 개발 배경지식 없이도 이해되게:
  무엇을/왜 만들었는지, 직접 확인하는 법(명령어·기대 결과·문제 해결), 검증 결과,
  알려진 이슈, 커밋·CI 링크. 목차는 `docs/progress/README.md` 참조.
