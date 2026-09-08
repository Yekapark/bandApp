# 밴드 합주 관리 앱 — 구현 지시서 (for Claude Code)

이 문서는 Claude Code가 읽고 구현을 진행하기 위한 지시서다.
작업 시작 전 이 문서를 끝까지 읽고, 각 Phase의 **완료 기준**을 충족했는지 확인하며 진행한다.

---

## 0. 프로젝트 개요

밴드원 개인이 가입해 사용하는, 밴드 단위 합주 일정 관리 서비스.
밴드 생성/초대, 합주실 정보 관리, 합주 일정 기록, 참석 체크, 비용 N빵 정산,
합주 사진·영상 게시판을 제공한다.

### 반드시 지켜야 할 전제

> **이 앱은 합주실 예약을 대행하지 않는다.**
> 실제 예약(전화·카톡·네이버예약 등)은 전적으로 앱 밖에서 이루어지며,
> 앱에서의 "일정 등록"은 **이미 외부에서 완료된 예약을 기록**하는 행위다.
> 실시간 가용시간 조회, 합주실 예약 시스템 연동, 예약 전송 기능은 **만들지 않는다.**
>
> 이것이 기록용 도구라는 점은 검증 로직에도 그대로 적용된다.
> 시간대가 겹치는 일정도 **막지 않고 저장한다.** 실제로 같은 시간에 두 곳을
> 잡았거나 장소를 옮긴 경우도 있는 그대로 기록될 수 있어야 한다.
> 겹침은 경고로만 알리고, 저장 여부는 사용자가 결정한다.

---

## 1. 기술 스택 (확정)

### 백엔드
- Java 21, Spring Boot 3.x
- Spring Security — JWT(access/refresh) + OAuth2(카카오)
- Spring Data JPA, PostgreSQL
- Redis — 리프레시 토큰, 레이트리밋
- Flyway — 스키마 마이그레이션
- springdoc-openapi — API 문서
- JUnit 5 + Testcontainers(PostgreSQL) — 통합 테스트

### 클라이언트 (별도 트랙, 백엔드 이후)
- Flutter — 웹 + iOS + Android 단일 코드베이스
- 지도: 네이버 지도 (`flutter_naver_map` + 웹은 JS SDK)
- HTTP: `dio`

### 인프라
- 단일 VM + Docker Compose (Spring Boot + PostgreSQL + Redis)
- Nginx 리버스 프록시 + Let's Encrypt
- Cloudflare (DNS/CDN) + Cloudflare R2 (미디어 저장)
- Firebase Cloud Messaging (푸시)
- GitHub Actions (CI/CD)

---

## 2. 절대 하지 말 것

작업 중 아래 판단이 필요해지면 **임의로 진행하지 말고 사용자에게 물어본다.**

1. **합주실 예약 시스템 연동 코드를 작성하지 않는다.** 외부 예약 API, 크롤링,
   가용시간 조회 등 일체 금지. 일정은 사용자가 입력하는 기록일 뿐이다.
2. **시간대 겹침을 차단하지 않는다.** DB 제약조건(`EXCLUDE` 등)이나 서비스 레이어
   검증으로 등록을 막는 코드를 작성하지 않는다. 겹침은 응답에 경고로만 포함한다.
3. **(오해 정정) "결제는 앱 밖에서 이루어진다"는 전제는 합주실 예약·합주 비용
   정산에 대한 것이지, 프리미엄 구독 결제를 금지하는 것이 아니다.** 프리미엄 구독
   결제는 오히려 **앱(스토어) 안에서** 열려 있어야 한다 — Google Play Billing /
   Apple In-App Purchase. 다만 **지금 단계(Phase 10)에서는 실제 스토어 결제 연동을
   구현하지 않는다.** 요금제 구조와 `PaymentGateway` 인터페이스, no-op 구현체까지만
   만들고, 실제 인앱결제 어댑터는 별도 Phase(§4 Phase 12)로 진행한다.
4. **마이크로서비스로 분리하지 않는다.** 도메인별 패키지로 나눈 모듈형 모놀리스를 유지한다.
5. **대용량 파일을 백엔드 서버를 경유해 업로드하는 코드를 작성하지 않는다.**
   반드시 presigned URL 방식으로 클라이언트 → R2 직접 업로드로 구현한다.
6. **스펙에 없는 라이브러리를 임의로 추가하지 않는다.** 필요하면 이유와 함께 먼저 제안한다.
7. **엔티티 필드를 임의로 추가/변경하지 않는다.** 3번 항목의 도메인 모델을 기준으로 하고,
   변경이 필요하면 먼저 제안한다.

---

## 3. 도메인 모델

```
User { id, email, passwordHash, name, socialProvider, socialId, emailVerified, createdAt, deletedAt }
  - passwordHash: 이메일 가입자만 값이 있다(소셜 가입자는 NULL). BCrypt 해시로만 저장한다.
  - email: 소셜 가입자는 제공 동의가 없으면 NULL일 수 있다.
  - emailVerified: 승인됨(2026-09-06) — 이메일 가입자는 가입 시 false로 시작해 인증 코드
    메일로 true가 된다. 소셜 가입자는 항상 true. 강제하지 않는다(미인증이어도 기능 제한 없음,
    클라이언트 배너 안내용).

Band { id, name, leaderId,
       reservationPermission(LEADER_ONLY | ANYONE | APPROVAL_REQUIRED),
       createdAt }
  - 기본값은 LEADER_ONLY
  - 밴드 설정에서 밴드장이 세 값 중 선택 가능

BandMember { id, bandId, userId, role(LEADER | MEMBER), joinedAt, leftAt }
  - 자발적 탈퇴 + 밴드장에 의한 추방 모두 지원
  - 밴드장 위임 시 기존 LEADER는 MEMBER로 강등

BandInvite { id, bandId, code, expiresAt, maxUses, usedCount, revoked, createdBy, createdAt }
  - code: 8자 영숫자, 혼동 문자(0/O, 1/l/I) 제외, unique
  - 기본 만료 7일, 재발급 시 기존 코드 revoked 처리
  - createdAt: Phase 2에서 추가(승인됨). 활성 코드 최신순 조회·감사용

Room { id, bandId, name, address, lat, lng, phone, memo, usageCount, createdBy, createdAt, deletedAt }
  - 밴드별 독립 등록 (여러 밴드가 같은 장소를 각자 등록해도 별개 레코드)
  - 주소 → 좌표 변환은 네이버 지도 지오코딩 API 사용 (실패 시 lat/lng NULL 허용)
  - deletedAt: Phase 3에서 추가(승인됨). 소프트 삭제 — 삭제 후에도 과거 일정이 합주실 정보를 참조할 수 있어야 한다

Reservation { id, bandId, roomId, requestedBy,
              status(PENDING | CONFIRMED | CANCELLED | REJECTED),
              startAt, endAt, cost, note,
              recurringRuleId, createdAt }
  - status는 "밴드 내부 일정으로서의 등록 상태"이며 실제 합주실 예약 상태가 아니다
  - note: 외부 예약 방법 자유 기재 (예: "카톡 예약 완료, 예약자 홍길동")
  - 생성 시 초기 status는 Band.reservationPermission에 따라 분기
      LEADER_ONLY / ANYONE      → CONFIRMED
      APPROVAL_REQUIRED         → PENDING (밴드장 승인 시 CONFIRMED)
  - 시간대 겹침을 막지 않는다 (2장 2번 참조)

RecurringRule { id, bandId, roomId, frequency(WEEKLY | BIWEEKLY | MONTHLY),
                dayOfWeek, startTime, endTime, startDate, endDate, createdBy }
  - 규칙에 따라 Reservation 레코드를 미리 생성(예: 향후 8주분)
  - 개별 회차 수정/취소는 해당 Reservation만 변경하고 규칙은 유지

ReservationAttendance { id, reservationId, userId,
                        status(ATTENDING | ABSENT | PENDING), respondedAt }
  - 일정 생성 시 밴드 멤버 전원에 대해 PENDING으로 생성

SetlistItem { id, reservationId, title, artist, referenceUrl, orderNo }

Settlement { id, reservationId, totalAmount,
             splitType(EQUAL | ATTENDEES_ONLY), createdAt }
SettlementShare { id, settlementId, userId, amount, paid, paidAt }
  - EQUAL: 밴드 멤버 전원 균등분배
  - ATTENDEES_ONLY: ReservationAttendance가 ATTENDING인 멤버만 균등분배
  - paid는 본인이 직접 체크 (셀프 리포트)

BoardPost { id, bandId, authorId, title, content, createdAt, deletedAt }

MediaAttachment { id, boardPostId, storageKey, type(IMAGE | VIDEO),
                  status(PENDING | READY | EXPIRED),
                  contentType, sizeBytes, uploadedAt, expiresAt }
  - expiresAt: 무료 플랜은 업로드일 + 30일, 프리미엄은 null

Report { id, targetType(POST | MEDIA | USER), targetId, reporterId,
         reason, status(OPEN | RESOLVED), createdAt }
UserBlock { id, blockerId, blockedUserId, createdAt }

NotificationSetting { userId, pushEnabled, reminderOffsets[] }
DeviceToken { id, userId, token, platform(IOS | ANDROID | WEB), updatedAt }

BandPlan { id, bandId, tier(FREE | PREMIUM), mediaRetentionDays,
           startedAt, expiresAt }
  - FREE: mediaRetentionDays = 30
  - PREMIUM: null(무제한) 또는 정책에 따른 값
```

---

## 4. 구현 Phase

각 Phase를 순서대로 진행한다. **한 Phase가 완료 기준을 충족한 뒤 다음으로 넘어간다.**
Phase 0~10은 백엔드이며, Flutter 클라이언트는 백엔드 완료 후 별도 트랙으로 진행한다.

### Phase 0 — 프로젝트 스캐폴딩

- Spring Boot 3.x + Java 21 프로젝트 생성 (Gradle)
- 패키지 구조: `com.yeka.bandapp` 하위에 도메인별 패키지
  (`user`, `band`, `room`, `reservation`, `settlement`, `board`, `notification`,
  `plan`, `common`)
- `docker-compose.yml` — app, postgres, redis
- Flyway 설정 및 초기 마이그레이션 디렉터리
- 공통 예외 처리(`@RestControllerAdvice`), 공통 응답 포맷
- springdoc-openapi 설정
- GitHub Actions 워크플로 (빌드 + 테스트)

**완료 기준**: `docker compose up`으로 앱이 기동되고, `/actuator/health`가 200을 반환하며,
CI에서 빌드·테스트가 통과한다.

### Phase 1 — 인증

- 이메일 회원가입/로그인
- 카카오 OAuth2 소셜 로그인
- JWT access/refresh 토큰 발급 및 갱신, refresh 토큰은 Redis 저장
- 회원 탈퇴(계정 삭제) — 소셜 계정 unlink 포함, `deletedAt` 소프트 삭제
- 비밀번호 재설정(이메일 인증번호) — 승인됨(2026-09-06)
- 이메일 인증(가입 시 자동 발송, 강제하지 않음) — 승인됨(2026-09-06)

**완료 기준**: 가입 → 로그인 → 토큰 갱신 → 탈퇴 전 과정의 통합 테스트가 통과한다.

### Phase 2 — 밴드 · 초대 · 멤버

- 밴드 생성 (생성자가 자동으로 LEADER)
- 초대코드 발급/재발급/무효화 (밴드장만)
- 초대코드로 밴드 참여
- **초대 딥링크** — 초대코드가 담긴 링크 발급 API,
  앱 미설치 시 스토어로 유도하는 웹 랜딩 페이지 (Universal Link / App Link 대응)
- 멤버 목록 조회, 자발적 탈퇴, 밴드장의 멤버 추방
- **밴드장 위임** — 기존 LEADER는 MEMBER로 강등, 대상은 LEADER로 승격 (원자적 처리)
- 밴드 설정 변경 API (`reservationPermission`)
- 초대코드 입력 시도에 Redis 기반 레이트리밋 (계정/IP 기준 분당 제한)

**완료 기준**: 만료·사용완료·revoked 코드가 각각 올바르게 거부되고, 권한 없는 사용자의
밴드 설정 변경이 403으로 차단되며, 위임 후 밴드에 LEADER가 정확히 한 명 남는 테스트가 통과한다.

### Phase 3 — 합주실 (Room)

- 합주실 등록/수정/삭제 (밴드 멤버)
- 주소 입력 시 네이버 지도 지오코딩으로 좌표 변환 후 저장
- 밴드별 합주실 목록 조회 (`usageCount` 내림차순 정렬)

**완료 기준**: 지오코딩 실패 시에도 주소만으로 등록이 가능하며(좌표 null 허용),
다른 밴드의 합주실이 조회되지 않는 테스트가 통과한다.

### Phase 4 — 일정 등록

- 일정 등록 — `Band.reservationPermission`에 따른 권한 분기 및 초기 status 결정
- 밴드장의 승인/거절 API (`APPROVAL_REQUIRED` 모드)
- 일정 수정/취소
- 기간별 일정 목록 조회 (캘린더용)
- 일정 등록 시 해당 Room의 `usageCount` 증가
- **겹침 경고** — 등록/수정 응답에 같은 밴드의 겹치는 일정 목록을 포함한다.
  저장은 정상적으로 수행되며, 이를 이유로 요청을 거부하지 않는다.

**완료 기준**: 세 가지 권한 모드가 각각 의도대로 동작하고, 시간대가 겹치는 일정을
등록해도 정상 저장되면서 응답에 겹침 정보가 담기는 테스트가 통과한다.

### Phase 5 — 정기 일정

- 반복 규칙 등록 (주간/격주/월간, 요일·시간 지정)
- 규칙에 따라 향후 N주분 Reservation 자동 생성
- 개별 회차 수정/취소 (규칙 자체는 유지)
- 규칙 삭제 시 미래 회차만 삭제하고 과거 기록은 보존
- 만료 임박한 규칙의 회차를 이어서 생성하는 배치잡

**완료 기준**: 규칙 삭제 후에도 과거 일정과 그에 연결된 정산 기록이 남아 있는 테스트가 통과한다.

### Phase 6 — 참석 체크(RSVP) · 셋리스트

- 일정 생성 시 밴드 멤버 전원의 `ReservationAttendance`를 PENDING으로 생성
- 본인 참석 상태 변경 API (본인 것만 수정 가능)
- 일정 상세 조회 시 멤버별 참석 현황 및 집계(참석 N / 전체 M) 포함
- 셋리스트 CRUD — 곡명, 아티스트, 참고 링크, 순서

**완료 기준**: 일정 생성 이후 밴드에 합류한 멤버도 참석 응답이 가능하며,
타인의 참석 상태 변경이 403으로 차단되는 테스트가 통과한다.

### Phase 7 — 정산 (N빵)

- 일정에 총 비용 입력 → `splitType`에 따라 `SettlementShare` 생성
  - `EQUAL`: 밴드 멤버 전원 균등분배
  - `ATTENDEES_ONLY`: 참석(ATTENDING) 멤버만 균등분배
- 나누어떨어지지 않는 금액의 처리 규칙을 명시적으로 구현 (예: 나머지는 밴드장 부담)
- 정산 생성 후 참석자가 바뀐 경우 재계산 API 제공 (자동 재계산은 하지 않음)
- 본인 납부 체크 API (본인 share만 수정 가능)
- 정산 현황 조회

**완료 기준**: 3명이 10,000원을 나누는 등 나머지가 발생하는 케이스에서 share 합계가
총액과 정확히 일치하고, `ATTENDEES_ONLY`인데 참석자가 0명인 경우가 명시적으로
처리되는 테스트가 통과한다.

### Phase 8 — 게시판 · 미디어 업로드 · 신고

- 게시글 CRUD (밴드 멤버만 조회 가능)
- R2 presigned PUT URL 발급 API (멤버십·contentType·sizeBytes 검증)
- `MediaAttachment`를 PENDING으로 선생성
- 업로드 완료 콜백 → R2 HEAD 요청으로 실제 업로드 및 크기 검증 → READY 전환
- 조회 시 짧은 만료의 presigned GET URL 발급 (버킷은 비공개 유지)
- 업로드 URL 발급에 레이트리밋 적용
- **신고 기능** — 게시글/미디어/사용자 신고 접수 API
- **사용자 차단** — 차단한 사용자의 게시글이 목록에서 제외됨

**제한값**: 영상 50MB, 이미지 10MB, presigned URL 만료 5~15분

**완료 기준**: 백엔드를 경유하는 파일 스트림이 코드상 존재하지 않으며,
신고한 크기와 실제 크기가 다를 때 거부·삭제되고, 차단한 사용자의 글이
목록 응답에서 빠지는 테스트가 통과한다.

### Phase 9 — 알림 · 배치잡

- FCM 연동, 디바이스 토큰 등록/해제
- 일정 리마인더 발송 (사용자별 설정된 시점)
- 알림 트리거: 일정 리마인더, 새 일정 등록, 승인 요청/결과, 정산 요청, 참석 미응답 독촉
- 사용자별 알림 설정 (on/off, 리마인더 시점 복수 지정)
- 배치잡 1 (일 1회): `expiresAt` 지난 READY 미디어 → R2 삭제 후 EXPIRED 전환
- 배치잡 2 (시간당): 1시간 이상 PENDING인 미디어 레코드 정리
- 배치잡 3: 정기 일정 회차 이어서 생성 (Phase 5)

**완료 기준**: 배치잡이 R2 삭제 실패 시에도 트랜잭션이 깨지지 않고 재시도 가능한
구조이며, 각 배치의 단위 테스트가 통과한다.

### Phase 10 — 요금제 (결제 어댑터 제외)

- `BandPlan` 관리 — 밴드별 FREE/PREMIUM 티어
- 미디어 `expiresAt` 계산이 밴드의 현재 플랜을 따르도록 연결
- 플랜 변경 시 기존 미디어의 `expiresAt` 재계산 정책 구현
  (업그레이드 시 연장, 다운그레이드 시 유예기간 부여)
- `PaymentGateway` 인터페이스 정의 + no-op 구현체
- 결제 도메인 로직(구독 시작/갱신/해지)은 인터페이스에만 의존하도록 작성

> **실제 스토어 인앱결제 연동은 이 Phase에 포함하지 않는다.** 요금 정책(가격·기간)과
> 스토어 상품 등록이 확정되면 Phase 12에서 어댑터를 붙인다. "결제를 앱 밖에 둔다"는
> 이 프로젝트의 전제는 합주실 예약·정산에 대한 것이며 이 Phase와는 무관하다 — §2-3 참조.

**완료 기준**: no-op 게이트웨이로 FREE → PREMIUM 전환 시 기존 미디어의 만료일이
연장되는 테스트가 통과한다.

### Phase 11 — 배포

- 프로덕션용 Docker Compose 및 Nginx 설정
- Let's Encrypt 인증서 자동 갱신
- GitHub Actions 배포 파이프라인 (이미지 빌드 → GHCR → SSH 배포)
- **DB 자동 백업 스크립트** — `pg_dump` 일 1회 크론 → R2 업로드, 최근 7일 보관
- **복구 절차 문서화 및 실제 복구 테스트 1회 수행**

**완료 기준**: 백업 파일로부터 빈 DB에 복구하는 절차가 실제로 성공한다.

### Phase 12 — 인앱결제 연동 (스토어 결제)

> 스토어 개발자 등록·요금 정책(구독 가격·기간)이 확정된 뒤 착수한다. 이 Phase 전까지
> 프리미엄 전환은 쿠폰으로만 가능하며, 이는 정상 출시를 막지 않는다(무료 앱으로 먼저
> 출시 가능).
>
> **확정 (2026-09-08)**: 개발자 등록 완료. 요금 = **밴드당 연 구독 ₩19,000 / 년**
> (`PREMIUM_YEARLY`, 365일). 가격은 스토어 상품 등록 시 입력하는 값이다.
>
> **진행 (2026-09-08)** — **Android 만.** 클라이언트에 iOS 빌드 타깃이 없어 StoreKit 은 뒤로 미룬다.
> - 슬라이스 1 (PR #69): `StoreBillingGateway` 재설계(`fetch`/`acknowledge` + 웹훅 핸들러),
>   `band_plans.store`·`purchase_token`(V17), `processed_store_events`(멱등), no-op 게이트웨이,
>   `POST /bands/{id}/plan/google/verify`, `POST /api/v1/webhooks/google-play`
> - 슬라이스 2 (PR #70): `GooglePlayBillingGateway`(`purchases.subscriptionsv2.get`/`acknowledge`),
>   `google-api-services-androidpublisher` 의존성. `app.plan.billing.gateway=google` 로 켠다
> - 슬라이스 3 (PR #71): 웹훅 Pub/Sub OIDC 검증, Flutter `in_app_purchase` 결제 UI
> - **남음**: 슬라이스 0(Play Console 상품·서비스계정·Pub/Sub·라이선스 테스터 — 사람),
>   슬라이스 4(샌드박스 결제 e2e). 절차는 `docs/progress/NEXT.md` "Phase 12" 절.

프리미엄 구독의 실제 결제는 카드사 PG가 아니라 **스토어 인앱결제**로 연다.
"이 앱은 결제를 앱 밖에서 처리한다"는 §0/§2 전제는 합주실 예약·합주 비용 정산에
대한 것이며, 앱 안에서 잠기는 디지털 기능(미디어 무제한 보관 등)을 파는 구독에는
적용되지 않는다 — 오히려 스토어 정책상 이런 구독은 자체 PG 직접 결제가 허용되지 않고
스토어 인앱결제를 써야 한다.

- **Android**: Play Billing Library 연동(클라이언트가 구매 흐름 시작). 서버는 Play
  Developer API로 purchase token을 검증하고, RTDN(Real-time Developer
  Notifications, Pub/Sub)으로 갱신·해지·환불 이벤트를 수신한다.
- **iOS**: StoreKit 2 연동(클라이언트). 서버는 App Store Server API로 트랜잭션을
  검증하고, App Store Server Notifications V2로 갱신·해지·환불 이벤트를 수신한다.
- **`PaymentGateway` 인터페이스 재설계.** 기존 인터페이스는 "서버가 `subscribe`/
  `renew`/`cancel`을 능동적으로 호출"하는 카드 PG 모델로 설계돼 있다. 스토어 결제는
  구매가 클라이언트에서 시작되고 서버는 영수증/구매 토큰 검증과 웹훅 이벤트 반영만
  하므로, 이 모델에 맞게 인터페이스와 도메인 흐름을 다시 설계해야 한다(예:
  `verifyPurchase(receipt)` + 웹훅 이벤트 핸들러).
- 환불/취소 이벤트 수신 시 즉시 FREE로 강등.
- 이중 처리 방지 — 같은 구매/이벤트가 중복 전달돼도 한 번만 반영(웹훅은 재전송될 수 있음).

**완료 기준**: 각 스토어의 샌드박스 결제로 FREE → PREMIUM 전환이 실제로 되고, 갱신·해지·
환불이 스토어 웹훅으로 반영되는 통합 테스트가 통과한다.

---

## 5. 코딩 컨벤션

- 도메인별 패키지 구조를 유지하고, 도메인 간 참조는 서비스 레이어를 통해서만 한다
- Controller는 DTO만 다루고, 엔티티를 직접 노출하지 않는다
- 모든 API는 인증된 사용자의 밴드 소속 여부를 검증한다 (다른 밴드 데이터 접근 차단)
- 비즈니스 예외는 커스텀 예외로 던지고 `@RestControllerAdvice`에서 응답으로 변환한다
- 스키마 변경은 반드시 Flyway 마이그레이션으로 관리한다 (`ddl-auto`는 `validate`)
- 각 Phase마다 통합 테스트를 작성한다 (Testcontainers 사용)
- 커밋은 Phase·기능 단위로 나눈다

---

## 6. 배포 전 체크리스트

`docs/BACKLOG.md`에 상세 내용이 있다. 요약하면:

- 회원 탈퇴(계정 삭제) 기능 — 스토어 심사 필수 (Phase 1에 포함됨)
- 신고/차단 기능 — UGC 앱 심사 대응 (Phase 8에 포함됨)
- 개인정보처리방침·이용약관 웹 페이지 및 URL
- DB 자동 백업 및 복구 테스트 (Phase 11)
- 카카오 로그인 비즈앱 검수
- Apple Developer 연 $99 / Google Play 최초 $25
