# 배포·운영 체크리스트 및 디자인 작업 메모

> 이 문서는 **사람이 참고하는 작업 메모**다.
> 구현할 기능과 범위는 `docs/BUILD_PLAN.md`가 단일 출처이며, 이 문서에는
> 코드로 옮길 기능 명세를 적지 않는다.

---

## 1. 배포 · 운영 체크리스트

`BUILD_PLAN.md`의 Phase로 이미 들어간 항목은 어느 Phase인지 표시해뒀다.
표시가 없는 항목은 코드 밖에서 처리해야 하는 일이다.

### 1.1 스토어 등록 비용 — 예산에 추가 반영 필요

| 항목 | 비용 |
|---|---|
| Apple Developer Program | 연 $99 |
| Google Play 개발자 등록 | 최초 1회 $25 |

애플 연회비만 월 환산하면 1만원 초중반으로, 잡아둔 월 1~5만원 인프라 예산에서
꽤 큰 비중을 차지한다. 인프라를 무료 티어로 최대한 눌러야 하는 이유.
(금액은 변동될 수 있으므로 실제 등록 시점에 확인할 것)

### 1.2 계정 삭제 기능 — 심사 통과 요건 · Phase 1

- 회원가입이 있는 앱은 **앱 내에서 계정 삭제가 가능해야** 하며, 없으면 심사 거절
- 소셜로그인을 쓰는 경우 카카오 **연결 해제(unlink) API**까지 함께 호출해야 함
- 삭제 시 데이터 처리 정책(즉시 삭제 vs 일정 기간 보관 후 삭제)도 정해둘 것

### 1.3 개인정보처리방침 · 이용약관 URL

- 스토어 등록 시 URL 입력이 필수 → 웹에 호스팅된 페이지가 있어야 함
- Cloudflare Pages 등에 정적 페이지로 무료 호스팅 권장
- 한국에서 실사용자를 받는 서비스이므로 개인정보처리방침 게시는 법적 요건

### 1.4 DB 자동 백업 — 배포 전 필수 · Phase 11

단일 VM에 PostgreSQL을 올리는 구조라 **그 VM이 날아가면 사용자 데이터가 전부 사라진다.**
실제 운영에서 가장 위험한 지점.

- `pg_dump`를 매일 크론으로 실행 → Cloudflare R2에 업로드
- 보관 주기(예: 최근 7일 + 주간 4회) 설정
- **복구 절차를 한 번은 실제로 테스트해볼 것** (백업만 있고 복구가 안 되는 경우가 흔함)

### 1.5 UGC 신고 / 차단 · Phase 8

게시판이 있으므로 심사에서 요구될 수 있다. 밴드 내부 폐쇄형이라 실제 리스크는 낮지만
심사관 입장에서는 "사용자가 콘텐츠를 올리는 앱"으로 보인다.

### 1.6 카카오 로그인 검수

이메일 등 개인정보 항목을 받으려면 카카오 개발자 콘솔에서 비즈앱 전환/검수가
필요할 수 있다. **개발 초기에 미리 확인**해두면 배포 직전에 막히는 일이 없다.

### 1.7 서비스명 · 패키지명 확정 — **완료 (2026-09-06)**

서비스명은 **밴듈(BANDULE)**, 앱 패키지는 **`com.yeka.bandule`** 로 확정했다.
`flutter create` 기본값 `com.example.bandapp_client` 는 `com.example.` 로 시작해서
구글 플레이가 업로드를 거부한다. 바꾼 곳: 클라이언트 `namespace`·`applicationId`·
`MainActivity.kt` 패키지와 디렉터리, 백엔드 딥링크 기본값(`ANDROID_PACKAGE`·플레이스토어 URL·
`IOS_APP_ID` 예시).

**남은 사람 작업** — 카카오 개발자 콘솔 > 내 애플리케이션 > 앱 설정 > 플랫폼 > Android 의
패키지명을 새 값으로 고친다. 키 해시는 서명 키에서 나오는 값이라 그대로다.
안 고치면 카카오 로그인이 플랫폼 불일치로 막힌다.

> 백엔드 자바 패키지(`com.yeka.bandapp`)는 서버 코드 경로라 앱 ID 와 별개다. 그대로 둔다.

### 1.8 Phase 0·1 코드 리뷰 후속 (2026-08-31)

Phase 1 머지 후 보안·인프라 리뷰에서 나온 항목. `.env.example` 안전화(JWT_SECRET 미설정 시
부팅 실패)와 Dependabot 추가는 이미 반영됨. 나머지:

**보안 — 배포 전 처리**

- ~~**레이트리밋을 `/api/v1/auth/**` 전체에 적용.**~~ **완료** — Phase 2(PR #16)에서 `AuthRateLimitInterceptor`로
  `/api/v1/auth/**`의 POST에 엔드포인트별 IP 분당 제한을 걸었다. 단, IP 판정이 `X-Forwarded-For`를
  무조건 신뢰해 위조로 우회 가능 → §1.9 참조.
- **운영 프로파일 분리.** `application-docker.yml`의 `management.endpoint.health.show-details: always`가
  운영에서도 적용되면 `/actuator/health`(비인증)가 DB·Redis 버전, 디스크, SSL 체인을 노출한다.
  운영은 `when-authorized` 또는 `never`. 별도 `prod` 프로파일 권장.
- **운영 Redis·Postgres 포트 비공개 + Redis 비밀번호.** 로컬 `docker-compose.yml`은 5432/6379를
  호스트에 게시하고 Redis에 비밀번호가 없다. 단일 VM에서 이 포트가 열려 있으면 전체 리프레시
  토큰·차단목록이 노출된다. 운영 compose(Phase 11)에서는 포트를 게시하지 않거나 `127.0.0.1`
  바인딩하고 Redis `requirepass`를 건다.
- **로그인 타이밍 기반 계정 열거 방어.** `AuthService.login`은 이메일이 없으면 즉시 반환,
  있으면 bcrypt(~100ms)를 돈다. 사용자 미발견 시에도 더미 bcrypt를 한 번 수행해 응답 시간을 맞춘다.
- **사용자당 리프레시 세션 수 상한.** `RefreshTokenStore`의 `auth:refresh:{userId}` 해시에
  세션이 무제한 쌓인다(로그인 반복 시 메모리 증가). 최신 N개만 유지.

**인프라 · 운영**

- **Spring Boot 버전 상향.** `3.4.1`은 OSS 지원 종료(2025-12-31). `3.4.x` 최신 패치 또는 `3.5.x`로.
- **CI 액션 상향 + 리포트 업로드.** `actions/*@v4`가 Node 20 폐기 경고. 실패 시 `build/reports`를
  아티팩트로 올려 CI 로그만으로 원인 파악이 되게.
- **Dockerfile 하드닝.** 런타임 이미지에서 헬스체크용 `curl` 설치 제거 검토, 베이스 이미지 다이제스트 고정.
- `main` 브랜치 보호(리뷰·CI 필수), 정적 분석(spotless/checkstyle) 도입은 선택.

**제품 갭 (BUILD_PLAN에 없지만 출시 전 필요)**

- **비밀번호 재설정("비밀번호 찾기") 플로우.** 현재 이메일 계정 사용자가 비번을 분실하면 복구
  수단이 전혀 없다. 이메일 발송 인프라(Phase 9 알림)와 함께 설계.
- **이메일 인증(가입 확인 메일)** — 현재 가입 즉시 토큰 발급. 실사용자 서비스면 추후 고려.
- **애플 로그인** — `users.social_provider` CHECK가 `KAKAO`만 허용. iOS 심사에서 애플 로그인이
  요구될 수 있으니 확인. 추가 시 마이그레이션 필요.

**개인정보**

- 개인정보처리방침에 **탈퇴 후 90일간 이메일·이름·카카오번호 보관 후 파기** 정책을 명시한다
  (1.3 페이지에 포함). PIPA 대응.

### 1.9 Phase 2 코드 리뷰 후속 (2026-09-01)

Phase 2(밴드·초대·멤버, PR #16) 머지 후 리뷰에서 나온 항목. `.well-known` 엔드포인트의
`produces` 이슈는 이 리뷰 커밋에서 바로 수정함. 나머지:

**보안 — 배포 전 처리**

- ~~**`X-Forwarded-For` 무조건 신뢰.**~~ **완료 (2026-09-06, Phase 11)** — §1.11 참조.
  (원문) `common/web/ClientIp`가 XFF 첫 홉을 그대로 클라이언트 IP로
  쓴다. 그 결과 (1) 초대 참여 레이트리밋과 (2) §1.8에서 넣은 인증 브루트포스 레이트리밋이
  둘 다 XFF 위조로 우회 가능하고, 피해자 IP를 XFF에 넣어 그 사람의 IP 버킷을 고갈시켜
  참여를 막는 것도 가능하다. 프록시 없는 환경(로컬)에서는 완전 우회. 조치: 신뢰 프록시 IP에서
  온 XFF만 사용하거나, `server.forward-headers-strategy` + 신뢰 프록시 목록, 또는 가장 바깥
  신뢰 홉에서 IP를 추출한다. 운영 Nginx 구성(Phase 11)과 함께 확정.
- 인증 레이트리밋 버킷 키가 정규화되지 않은 `request.getRequestURI()` 기반이라 `//login` 같은
  변형으로 별도 버킷을 만들 수 있다(영향은 작음).
- 초대코드 존재 여부 오라클: 없는 코드는 404, revoked 코드는 410. 코드 공간이 32^8이라 실질
  위험은 없으나 응답을 통일할지 검토.
- `POST /api/v1/bands`(밴드 생성)에 레이트리밋이 없다. 인증 사용자가 밴드를 무한 생성 가능.

**제품 갭 (BUILD_PLAN에 없지만 필요)**

- ~~**탈퇴 ↔ 밴드 멤버십 미연동 (가장 큼).**~~ **완료 (2026-09-01, Phase 4 착수 전)** — 지시자 승인으로
  다음 동작을 `UserAccountService.withdraw` → `BandMemberService.handleAccountWithdrawal`에 넣었다:
  탈퇴 시 소속 전 밴드에서 자동으로 나간다(탈퇴는 절대 막지 않음 — §1.2). 탈퇴자가 밴드장인 밴드는
  **가장 먼저 가입한 다른 활성 멤버**를 밴드장으로 자동 승격한다. 다른 멤버가 없으면 그 밴드는
  활성 멤버 0인 상태로 남는다(`bands`·`rooms` 행은 유지되나 접근 불가라 사실상 소멸). 스키마 변경 없음.
  같은 트랜잭션이라 밴드 정리 실패 시 탈퇴 전체가 롤백된다.
- ~~**"내가 속한 밴드 목록" API 없음** (`GET /api/v1/bands`).~~ **완료** — Phase 3에서 추가했다.
  `{id, name, myRole, memberCount, joinedAt}`를 가입순으로 반환하고, 탈퇴한 밴드는 빠진다.
  `ix_band_members_user_active` 인덱스를 탄다.

**테스트 커버리지 갭 (사소)**

- `CANNOT_DELEGATE_TO_SELF`, 위임 대상이 비멤버(`MEMBER_NOT_FOUND`), `maxUses:1` 동시 참여 레이스,
  `GET .../invites/current`가 없을 때 404 — 미커버.

### 1.10 Phase 0~3 전체 점검 후속 (2026-09-01)

Phase 3 PR 후 인증·밴드·초대·합주실 전 경로 점검. **A1~A4·B1~B3은 이 브랜치에서 바로 수정함.**
아래는 그 수정에 딸린 잔여 메모와, 별도 이슈로 남긴 것.

**수정에 딸린 잔여 작업**

- **이메일 소문자 마이그레이션.** B2에서 `signup`/`login`이 이메일을 `trim().toLowerCase`로 정규화하도록
  고쳤다. 운영 데이터가 쌓인 뒤라면 기존 행 소문자화 Flyway 마이그레이션이 필요하다
  (`UPDATE users SET email = lower(email) ...` — 대소문자만 다른 중복 행이 있으면 유니크 인덱스 충돌하므로
  선정리 필요). 지금은 데이터가 없어 코드 정규화만.
- **refresh 회전 재시도 캐시(A2).** 60초 동안 `auth:refresh:replay:{userId}:{jti}`에 발급 토큰 문자열을
  보관한다. Redis가 유출되면 그 60초 창의 토큰이 노출된다 — §1.8의 "운영 Redis 비밀번호·포트 비공개"와
  함께 처리하면 실질 위험 없음. 창 길이는 `RefreshTokenStore.ROTATION_REPLAY_GRACE`.
- **prod 프로파일.** B1에서 `application-prod.yml`을 추가했다(springdoc off, actuator health `never`).
  **Phase 11 배포는 반드시 `SPRING_PROFILES_ACTIVE=prod`(또는 `docker,prod`)로 띄워야** 적용된다.
  §1.8의 "운영 프로파일 분리"도 이 파일로 흡수 — 필요한 다른 운영 전용 설정을 여기에 모은다.

**Phase 3 집중 점검(2026-09-01) — 지오코딩**

- ~~**지오코딩 HTTP를 `@Transactional` 안에서 호출** (`RoomService.create`/`update`).~~ **완료** —
  `create`/`update`에서 `@Transactional` 제거, 지오코딩을 트랜잭션 밖으로. `update`는 엔티티 `merge`
  대신 `RoomRepository.updateEditableFields` 부분 UPDATE로 써서 동시에 바뀔 수 있는 `usage_count` 보존.
  재발 방지 규칙을 `CLAUDE.md` 규칙 절에 추가함.
- ~~네이버 응답 좌표에 `Double.isFinite()`·WGS84 범위 검증 없음.~~ **완료** —
  `NaverGeocodingClient.isValidWgs84` 가드 추가(비정상 응답이 DB·JSON을 깨뜨리지 않게).
- **지오코딩 레이트리밋 헛소비(마이너, 미수정).** `RoomService.geocode`가 `NaverProperties.isConfigured()`
  확인보다 먼저 레이트리밋을 태운다. 현재 배포엔 네이버 키가 없어서, 주소 있는 방을 분당 20개 넘게
  만들면 429가 난다(지오코딩은 어차피 no-op). 키를 넣을 때 같이 정리하거나 `isConfigured` 선확인.
- **지오코딩 실패 로그 다듬기(마이너, 미수정).** 네이버 장애 시 방 등록마다 사용자 주소를 WARN +
  스택트레이스로 남긴다. 로그 스팸·약한 PII. 한 번만 집계 로그로 낮추는 것 검토.

**별도 이슈로 남긴 것 (C절)**

- 잘못된 경로 변수(`/api/v1/bands/abc`)·미존재 라우트·잘못된 HTTP 메서드가 공통 `ApiResponse` 봉투가
  아니라 스프링 기본 `/error` 형식으로 응답한다. `GlobalExceptionHandler`에 `MethodArgumentTypeMismatch`
  등 핸들러 추가 또는 `ResponseEntityExceptionHandler` 상속.
- `delegateLeadership`·`issue` 동일인 더블서브밋 시 `DataIntegrityViolationException` → 500(드묾, 자기 유발).
- `maxUses:N` 초대코드를 한 사람이 join↔leave 반복으로 소진 가능. `usedCount`는 "성공한 참여 수"라
  현재 멤버 수가 아님 — 의도면 문서화.
- `JwtAuthenticationFilter`가 `BusinessException`만 잡는다. Redis 장애 시 `blocklist.isBlocked`의
  `RedisConnectionFailureException`이 필터 밖으로 나가 모든 인증 요청이 스택트레이스 포함 500.
- `WithdrawnUserPurgeJob`·`RecurringExtensionJob`(Phase 5) 분산 락 없음(단일 VM 전제라 지금은 무해,
  스케일아웃 시 중복 실행). `RecurringExtensionJob`은 `ux_reservations_rule_slot` 유니크 인덱스로
  중복 회차 저장은 막히지만, 두 인스턴스가 동시에 돌면 한쪽 트랜잭션이 롤백될 수 있다(다음 실행이 메움).
- **정기 회차를 다른 시각으로 옮기면 배치가 원래 슬롯을 다시 만들 수 있다**(Phase 5 §8.1 F4).
  개별 취소는 안전(취소분 `start_at`이 슬롯 후보에서 빠짐), 이동만 해당. 지평선 밖 회차에만 발생.
  회차에 "규칙에서 분리됨" 플래그를 두거나 규칙에 예외 슬롯 목록을 두면 닫힌다(스키마 변경).
- **정기 규칙 등록 레이트리밋 없음.** Phase 8(일정 생성 레이트리밋)에서 함께. 회차 생성 구간을
  오늘 ±`horizonWeeks`로 제한해(§8.1 F1) 한 요청당 회차 수는 이미 `2×horizonWeeks+1`로 묶여 있다.

**탈퇴↔밴드 정리(§1.9 해결)에 딸린 잔여 엣지**

- **빈 밴드(활성 멤버 0) 누적.** 1인 밴드장이 탈퇴하면 `bands`·`rooms` 행이 남는다(접근 불가라 무해).
  진짜 빈 밴드를 주기적으로 하드 삭제하는 배치는 별도 과제 — Phase 4의 `Reservation` FK가 붙은 뒤
  cascade 정책까지 정해서 만든다.
- **같은 소규모 밴드에서 두 명이 거의 동시에 탈퇴.** A(밴드장)·B가 같은 순간 탈퇴하면, B의 트랜잭션이
  A의 커밋 전에 B를 MEMBER로 보고 그냥 나가고, A는 B를 밴드장으로 승격하는 식으로 엇갈려 밴드가
  밴드장 없이 남을 수 있다. 극히 드물고, `delegateLeadership` 동시 호출 레이스(C2)와 같은 계열.
  필요하면 밴드별 정리에 `SELECT ... FOR UPDATE`로 직렬화.

### 1.11 Phase 0~9 전체 보안 점검 후속 (2026-09-02)

Phase 9(알림·배치잡, PR #30) 머지 후 인증·밴드·합주실·일정·정기일정·참석·정산·
게시판/미디어·알림/배치 전 경로를 다시 점검. **심각도 "상"(인증 우회, 타 밴드 데이터
유출, RCE 등)은 없음.** 멀티테넌시 격리(`BandAccessGuard`), IDOR 방지(요청자=토큰 주체),
presigned 업로드(백엔드 파일 스트림 없음), JWT typ 분리·refresh 회전/재사용 탐지,
비밀 미커밋, 트랜잭션 밖 외부 HTTP, 배치잡 R2 실패 내성 — 규칙대로 지켜졌고
Phase 9 완료 기준도 충족. 아래는 잔여 항목(전부 중/하 등급).

**보안 — 배포 전 처리**

- ~~**[중] `X-Forwarded-For` 무조건 신뢰.**~~ **완료 (2026-09-06, Phase 11)** — 세 겹으로 닫았다:
  (1) Nginx 가 XFF 를 이어붙이지 않고 실제 피어 주소로 **덮어쓴다**(`deploy/nginx/proxy-headers.conf`),
  (2) `application-prod.yml` 에 `server.forward-headers-strategy: NATIVE` — 톰캣 RemoteIpValve 가
  신뢰 프록시(내부 대역)에서 온 XFF 만 해석해 `getRemoteAddr()` 를 실제 클라이언트 IP 로 바꾼다,
  (3) `ClientIp` 가 헤더를 직접 읽지 않고 소켓 주소만 본다 — 이 함수를 쓰는 로그인·초대참여·
  업로드·신고 경로가 한 번에 안전해진다. 운영 compose 는 8080/5432/6379 를 호스트에 게시하지
  않고 Redis 에 `requirepass` 를 건다. 상세: `docs/progress/phase-11-deploy.md` §3.2.
  (원문) `common/web/ClientIp`가
  XFF 첫 홉을 검증 없이 클라이언트 IP로 쓴다. 로그인·회원가입(이메일 열거)·초대코드 대입·
  지오코딩/미디어 업로드/신고 스팸 등 **모든 레이트리밋의 키**가 이 값이다. 그런데
  `server.forward-headers-strategy`/신뢰 프록시 설정이 없고, `docker-compose.yml`이 `8080:8080`
  으로 앱을 호스트에 직접 노출하며, XFF를 재작성할 Nginx는 Phase 11(미납품)이다. → 헤더만
  바꿔가며 레이트리밋을 완전히 우회할 수 있다. 특히 로그인은 아래 계정 단위 제한이 없어
  IP 제한만 뚫리면 무제한 비밀번호 추측이 가능하다.
  조치: (1) `application-prod.yml`에 `server.forward-headers-strategy: NATIVE` +
  `server.tomcat.remoteip.trusted-proxies`(내부 프록시 CIDR만). (2) `ClientIp`는 수동 XFF
  파싱 대신 밸브가 정리한 `request.getRemoteAddr()` 사용, 또는 신뢰 피어에서 온 XFF만 인정.
  (3) 프로덕션 compose에서 `8080`을 호스트로 게시하지 않기(내부 네트워크/`127.0.0.1`).
  **착수 전 확정 필요한 정보:** Nginx 위치(같은 compose 컨테이너 / 호스트 설치),
  Cloudflare 프록시 ON/OFF(ON이면 `CF-Connecting-IP` + Nginx `set_real_ip_from` 별도 필요),
  Nginx가 `proxy_set_header X-Forwarded-For` 를 넣는지. 서버 공인 IP는 불필요
  (`trusted-proxies`는 앱에 접속하는 프록시의 사설 IP이며 Tomcat 기본 사설 대역이 대개 커버).
  Phase 11 Nginx 구성과 함께 확정.

- **[중] 로그인 계정 단위 시도 제한·잠금 부재.** `AuthService.login`에 이메일 단위 실패
  카운터·지수 백오프·CAPTCHA·잠금이 없다. 방어는 `auth-per-ip-per-min`(기본 20) IP 제한뿐이라,
  프록시가 정상이어도 다수 IP 봇넷이 한 계정에 분당 20×N회 추측할 수 있다. 비밀번호 정책은
  최소 8자·복잡도 없음. 조치: Redis `login:fail:{email}` 카운터 + 임계치 초과 시 점진적
  지연/일시 잠금. 인프라 정보 불필요(코드+Redis만). (§1.8 "타이밍 기반 계정 열거 방어"와 함께.)

- **[하] CORS 설정 취약 가능성.** `CorsProperties` 기본값 `http://localhost:*`,
  `setAllowedOriginPatterns`라 `*` 패턴 허용, `allowedHeaders: *`. `CORS_ALLOWED_ORIGINS=*`
  로 뜨면 전 오리진 허용. 현재는 credentials 미사용 + Bearer 헤더 인증이라 영향 제한적
  (쿠키 탈취 불가)이나, 향후 쿠키 도입 시 위험. 조치: prod에서 `*` 값 거부/검증, 명시적
  오리진만 설정하도록 문서화, credentials 비활성 유지.

- **[하] 초대 랜딩 페이지 HTML 수동 조립.** `InviteLandingController.renderLanding`이
  `%CODE%` 등을 HTML+인라인 JS에 문자열 치환한다. `code`는 `[A-Z2-9]{8}` 정규식으로
  선검증되어 **현재는 인젝션 불가**. 나머지 값은 서버 설정. 정규식이 느슨해지거나 설정값이
  오염되면 무인증 페이지 반사형 XSS가 된다. 조치: 삽입 값 HTML/JS 이스케이프, 또는 정적
  페이지 + JSON으로 코드 전달. 엄격한 정규식 유지.

- **[하] 비프로덕션 프로파일 actuator 상세 노출 (§1.8 재확인).** `application-docker.yml`·
  `application-local.yml`이 `health.show-details: always`. `prod` 프로파일만 `never`.
  프로덕션은 항상 `prod` 프로파일 사용 보장. 베이스 기본값을 `never`로 두고 로컬만 완화하는
  방향 검토.

**기능 결함 (보안 아님)**

- **[하] 일정 재조정 시 리마인더가 재발송되지 않음.** `notification_dispatches` 멱등 키가
  `(user, RESERVATION_REMINDER, reservationId, offset)`이라, 특정 offset의 리마인더가 발송(행
  존재)된 뒤 일정이 **더 뒤로** 옮겨지면 그 offset은 새 시각 기준으로 다시 발송되지 않는다.
  조치: `ReservationService.update`에서 시각이 바뀌면 해당 reservationId의 `RESERVATION_REMINDER`
  dispatch 행(모든 variant) 삭제. 또는 멱등 키에 시작 시각 포함.
  (`ReservationReminderJobTest`에 케이스 추가.)

- **[하] Redis 장애 시 레이트리밋이 fail-closed(429).** `RedisRateLimiter.tryAcquire`가 Redis
  불통 시 `increment` null → `check`가 429. 로그인·회원가입·토큰 회전 경로가 모두 막힌다
  (refresh 세션 저장소도 Redis 하드 의존이긴 함). 레이트리밋 한정 fail-open(로깅 동반) 정책을
  택하거나, 현재 fail-closed를 의도된 동작으로 문서화.

**개인정보 / 데이터 정리**

- **[하] 탈퇴 시 `notification_dispatches` 미삭제.** `DeviceTokenService.deleteAllOf`가
  `device_tokens`·`notification_settings`는 지우지만 `notification_dispatches`(user_id + 알림
  종류 + 대상 id)는 30일 보관 배치가 지울 때까지 남는다. `users` FK도 없어 정리 연쇄 안 걸림.
  조치: `deleteAllOf`에 `NotificationDispatchRepository.deleteByUserId` 추가(메서드 신설), 또는
  파기 배치가 탈퇴 계정 dispatch도 함께 제거. 보관 정책 문서화.

- **[하] FCM 디바이스 토큰 소유권 탈취 가능(설계상 한계).** `DeviceTokenService.register`가
  토큰 기준 last-writer-wins upsert. 인증 사용자가 임의 토큰 문자열을 등록할 수 있고, 존재하면
  소유자를 자신으로 바꾼다(`reassign`). 피해자의 FCM 토큰을 알면 소유권을 가져가 피해자 푸시를
  끊고 공격자 계정 알림을 피해자 기기로 보낼 수 있다. FCM 토큰이 완전한 비밀은 아니라 난이도는
  있으나 소유 증명이 없다. 조치: 충돌 시 기존 소유자가 다르면 경고 로깅, 또는 등록에 검증 절차.
  최소한 "알려진 이슈"로 문서화.

## 2. Claude Design 프롬프트

아래 내용을 Claude Design 입력창에 그대로 붙여넣는다.

```
밴드 멤버들이 함께 쓰는 합주 일정 관리 모바일 앱을 디자인해줘.

중요한 전제: 이 앱은 합주실 예약을 대행하지 않는다. 실제 예약은 전화나
카톡으로 밖에서 하고, 앱에는 "이미 잡은 예약을 등록"하는 것이다. 따라서
합주실 빈 시간 조회나 실시간 예약 화면은 없어야 하고, 일정 등록 화면은
"합주실 선택 + 날짜/시간 입력 + 예약 방법 메모" 형태의 기록 폼이어야 한다.

필요한 화면:
1. 로그인 / 회원가입 (카카오 로그인 중심)
2. 초대코드 입력 화면 — 밴드장이 준 코드를 넣어 밴드에 합류
3. 밴드 미소속 상태 화면 — "밴드 만들기" 또는 "초대코드로 참여" 선택
4. 밴드 홈 — 상단에 밴드 전환 스위처, 다가오는 합주 일정 카드,
   멤버 목록
5. 합주 일정 캘린더 — 월간 뷰에 일정이 있는 날짜 표시, 날짜 탭하면
   하단에 해당 일정 리스트
6. 일정 등록 폼 — 합주실 선택, 날짜/시간, 비용, 외부 예약 방법 메모,
   반복 일정 설정
7. 일정 상세 — 합주실 정보, 시간, 참석 여부 체크(참석/불참/미정)와
   멤버별 참석 현황, 이번 합주 셋리스트(곡 목록)
8. 합주실 목록/지도 — 밴드가 등록해둔 합주실을 지도 위 마커로 표시,
   하단에 리스트 카드
9. 합주실 등록 폼 — 이름, 주소 검색, 연락처, 메모
10. 정산 화면 — 총 비용을 나눈 1인당 금액, 멤버별 납부 여부 체크리스트,
    전원 균등/참석자만 선택 토글
11. 게시판 — 합주 사진/영상 피드 (썸네일 그리드 또는 카드 피드)
12. 게시글 상세 — 영상 플레이어, 사진, 댓글, 신고 메뉴
13. 설정 — 알림 on/off와 리마인더 시점, 밴드 설정(일정 등록 권한 3가지
    중 선택), 계정 관리

스타일: 밴드/음악 감성의 다크 톤 배경에 비비드한 포인트 컬러
(오렌지 또는 퍼플 계열) 하나. 모던하고 깔끔하게, 정보 밀도는 적당히.
한국어 UI 기준으로 텍스트를 넣어줘.
```

### 사용 팁

한 번에 13개 화면을 모두 뽑으면 각각의 완성도가 떨어질 수 있다.
핵심 화면(1, 2, 4, 5, 6, 7, 8)만 먼저 뽑아 톤을 확인하고,
마음에 들면 나머지를 추가로 요청하는 방식을 권장.

---

## 3. 디자인 → CLI 핸드오프 방법

1. Claude Design에서 화면 목업 제작
2. 아트보드를 화면별로 캡처해 `docs/design/`에 저장
   (`login.png`, `band-home.png`, `schedule-calendar.png` ...)
3. 색상 코드·폰트·여백 규칙을 `docs/design/style-guide.md`로 정리
4. CLI에서 화면 구현 시 이미지 경로를 함께 전달
   ("`docs/design/login.png` 참고해서 로그인 화면 Flutter 위젯 구현해줘")

### Claude Design ↔ Claude Code 연동 (선택)

기본 내장 명령어가 아니므로 MCP 서버를 먼저 추가해야 한다.

```bash
claude mcp add --scope user --transport http claude-design https://api.anthropic.com/v1/design/mcp
```

이후 Claude Code를 재시작하고 `/design-login`으로 인증,
`/design-sync`로 디자인 시스템을 가져올 수 있다.
(베타 기능이라 인증 단계에서 막히는 사례가 보고되고 있음)
