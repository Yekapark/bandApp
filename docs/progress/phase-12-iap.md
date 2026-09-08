# Phase 12 — 인앱결제 연동 (Google Play)

> **상태: 코드 완료(슬라이스 1~3), 스토어 설정·실기기 검증 미완(슬라이스 0·4).**
> 이 문서는 다른 사람/AI 가 검토할 수 있게 "무엇을 왜 어떻게" 를 자세히 적는다.
> 마지막 갱신: 2026-09-08.

---

## 1. 한 줄 요약

밴드 PREMIUM 을 **Google Play 인앱결제(자동갱신 구독)**로 판다. 결제는 클라이언트가 Play Billing 으로
시작하고, 서버는 (1) 클라이언트가 보낸 **구매 토큰을 Play Developer API 로 검증**해 PREMIUM 을 부여하고,
(2) **RTDN(Real-time Developer Notifications, Pub/Sub) 웹훅**으로 갱신·해지·환불을 반영한다.
가격은 **밴드당 연 ₩19,000** (Play Console 상품 설정값, 코드에 없음).

**iOS 는 이번 범위 밖.** 클라이언트에 iOS 빌드 타깃이 없어 StoreKit 을 빌드·테스트할 수 없다.
`Store` enum·`StoreBillingGateway` 는 나중에 `APP_STORE` 를 더할 수 있게 열어 뒀다.

---

## 2. 이 Phase 의 목표 (BUILD_PLAN 기준)

`docs/BUILD_PLAN.md` §4 Phase 12:

- Android: Play Billing(클라 구매 시작) + 서버가 Play Developer API 로 purchase token 검증 +
  RTDN 으로 갱신·해지·환불 수신.
- `PaymentGateway` 인터페이스 **재설계** — 기존은 "서버가 subscribe/renew/cancel 을 능동 호출"하는
  카드 PG 모델. 스토어 결제는 구매가 클라에서 시작되고 서버는 검증·웹훅 반영만 하므로 모델이 다르다.
- **환불/취소 이벤트 → 즉시 FREE 강등.**
- **이중 처리 방지** — 같은 구매/이벤트가 중복 전달돼도 한 번만 반영(웹훅은 재전송된다).

**완료 기준**: 샌드박스 결제로 FREE → PREMIUM 전환이 실제로 되고, 갱신·해지·환불이 웹훅으로
반영되는 통합 테스트가 통과한다. → **통합 테스트는 통과(아래 §6). 실기기 샌드박스는 슬라이스 4.**

---

## 3. 무엇을 만들었나 — 파일별

### 3-1. 도메인 (슬라이스 1, PR #69)

| 파일 | 역할 |
|---|---|
| `db/migration/V17__plan_store_iap.sql` | `band_plans` 에 `store`(GOOGLE_PLAY, 쿠폰이면 NULL)·`purchase_token` 컬럼 추가. 둘은 짝(`ck_band_plans_store_token` CHECK: 둘 다 NULL 이거나 둘 다 있어야). `processed_store_events` 테이블(웹훅 멱등) |
| `plan/entity/Store.java` | 결제 스토어 enum. 지금은 `GOOGLE_PLAY` 하나 |
| `plan/entity/BandPlan.java` | `store`·`purchaseToken` 필드 + `upgradeToPremium`/`downgradeToFree` 가 그 둘을 짝으로 관리. `renewFromStore(...)` — 쿠폰으로 PREMIUM 이던 밴드가 나중에 실결제하면 토큰을 붙여, 이후 웹훅이 밴드를 찾을 수 있게 |
| `plan/entity/ProcessedStoreEvent.java` + repo | 처리한 RTDN `messageId`(PK). insert 충돌 = 이미 처리함 |
| `plan/gateway/StoreBillingGateway.java` | **새 인터페이스.** `Optional<StoreSubscription> fetch(store, purchaseToken)` — 스토어에 구독 현재 상태를 물어봄. `void acknowledge(store, productId, purchaseToken)` — 3일 안에 안 하면 Play 가 자동환불. `StoreSubscription`(state·expiryTime·productId·orderId·acknowledged), `StoreSubscriptionState`(ACTIVE/CANCELED/IN_GRACE/ON_HOLD/PAUSED/EXPIRED/REVOKED; `grantsPremium()` = ACTIVE·CANCELED·IN_GRACE) |
| `plan/gateway/NoOpStoreBillingGateway.java` | `app.plan.billing.gateway` 가 없거나 `noop` 일 때 뜨는 기본 구현. 토큰 접두사로 상태를 흉내(`revoked-…`→REVOKED, `invalid-…`→empty, 그 외 ACTIVE). **로컬·CI 전용** — 실제 스토어를 안 부른다 |
| `plan/service/StoreSubscriptionService.java` | 오케스트레이션(`@Transactional` 없음 — 스토어 I/O 를 트랜잭션 밖에서). `verifyGooglePurchase(bandId,userId,token)`, `handleGoogleNotification(messageId,type,token)` |
| `plan/service/PlanMutationService.java` | DB 쓰기 단위(짧은 `@Transactional`). `applyUpgrade`(store·token 추가), `applyStoreRenew`, 그리고 웹훅용 조용한(no-op) 변형: `applyCancelAtPeriodEndIfPremium`·`applyDowngradeIfPremium`·`applyRevoke` |
| `plan/service/PlanService.java` | 서버 주도 `subscribe/cancel/renew` **제거**. `view`(조회)와 `expireOverdue`(만료 배치, 웹훅이 늦거나 빠졌을 때의 안전망)만 남김 |
| `plan/service/StoreWebhookRetryException.java` | "지금은 처리 못 함, Pub/Sub 재전송해 달라" 신호 → 컨트롤러가 503 |
| `plan/controller/PlanController.java` | `POST /api/v1/bands/{bandId}/plan/google/verify`(밴드장, `{purchaseToken}`) 추가. `subscribe/cancel/renew` 삭제. `view`·쿠폰 유지 |
| `plan/controller/GooglePlayWebhookController.java` | `POST /api/v1/webhooks/google-play` — Pub/Sub push 봉투 파싱(base64 `message.data` → `DeveloperNotification`) |
| `plan/config/StoreBillingProperties.java` | `app.plan.billing.*` |
| `common/exception/ErrorCode.java` | `PURCHASE_NOT_VERIFIED`(402), `PURCHASE_ALREADY_LINKED`(409) |
| `common/security/SecurityConfig.java` | `POST /api/v1/webhooks/google-play` 무인증 허용(컨트롤러가 자체 인증) |

### 3-2. 안전장치 (슬라이스 1 하드닝, PR #69)

돈 경로라 아래 4개를 추가로 메웠다. 근거는 §7 에.

1. **한 구매 토큰 = 한 밴드** — 같은 토큰을 다른 밴드에 `verify` 하면 `PURCHASE_ALREADY_LINKED`(409).
2. **acknowledge 실패로 응답을 깨지 않는다** — 등급은 이미 올랐고, 미확인이면 Play 자동환불 →
   REVOKED 웹훅 → 자기수정. 에러 로그만.
3. **웹훅 grant 이벤트(RENEWED 등)에서 스토어 조회 실패 시** 삼키지 않고 `StoreWebhookRetryException`
   → 503 → Pub/Sub 재전송. 멱등 기록은 **처리 성공 후에만** 남긴다.
4. **아직 밴드에 안 붙은 토큰의 grant 이벤트**도 재전송 대기(클라 verify 와의 경합). 종료성
   이벤트(REVOKED/EXPIRED)는 무시하고 200. 동시요청(verify + PURCHASED)이 `PLAN_ALREADY_PREMIUM`
   을 던지면 연장으로 이어감.

### 3-3. 실제 Google 어댑터 (슬라이스 2, PR #70)

| 파일 | 역할 |
|---|---|
| `build.gradle.kts` | `com.google.apis:google-api-services-androidpublisher:v3-rev20260528-2.0.0`. `google-api-client`·`google-auth-library` 는 `firebase-admin` 이 이미 가져옴 |
| `plan/gateway/google/GooglePlayBillingGateway.java` | `@ConditionalOnProperty(gateway=google)`. `fetch`→`purchases.subscriptionsv2.get`, `acknowledge`→`purchases.subscriptions.acknowledge`. 404/410·"no longer valid" 400 → `empty`(확정 무효). 5xx·네트워크 → `StoreBillingUnavailableException`. 서비스 계정 키 없이 켜면 **기동 실패**(FCM 키와 같은 fail-fast) |
| `plan/gateway/google/GooglePlaySubscriptionMapper.java` | `SubscriptionPurchaseV2` → `StoreSubscription` 순수 매핑. `SUBSCRIPTION_STATE_*` → enum(모르는/PENDING 은 PREMIUM 대상 아님), 여러 라인아이템이면 **가장 늦은 만료** 채택, RFC3339(Zulu·오프셋 둘 다) 파싱 |
| `plan/gateway/StoreBillingUnavailableException.java` | `StoreWebhookRetryException` 상속 — 일시 실패는 웹훅 503 / verify 402(잠시 후 재시도) |

### 3-4. 웹훅 OIDC + Flutter 결제 (슬라이스 3, PR #71)

| 파일 | 역할 |
|---|---|
| `plan/gateway/google/PubSubOidcVerifier.java` + `GoogleTokenVerifier.java` | Pub/Sub push 가 붙이는 `Authorization: Bearer <OIDC JWT>` 를 Google 공개키로 검증(google-auth `TokenVerifier`). `google-pubsub-audience` 미설정 시 비활성 |
| `plan/controller/WebhookAuthenticator.java` | OIDC Bearer 통과 **또는** `?token=` 공유 시크릿 일치 → 허용. 둘 다 설정 없으면 전부 거부(fail-closed). `google-pubsub-service-account` 설정 시 `email` 클레임까지 대조 |
| `client/pubspec.yaml` | `in_app_purchase` 추가 |
| `client/lib/features/plan/data/iap_service.dart` | Play Billing 래퍼 — 상품 조회, `buyNonConsumable`, `purchaseStream` 구독, `completePurchase`. Android 구매 토큰은 `verificationData.serverVerificationData`(구매 JSON)에서 꺼냄 |
| `client/lib/features/plan/data/plan_repository.dart` | `subscribe/cancel/renew` → `verifyGooglePurchase(bandId, token)` |
| `client/lib/features/plan/presentation/plan_screen.dart` | `initState` 에서 스트림 구독(지난 미검증 구매 재유입) + 상품 로드. 결제 성공 → `/plan/google/verify` → **성공 시에만** `completePurchase`. "PREMIUM 시작 · `<가격>` / 년" 버튼. 정상 PREMIUM 은 "Play 스토어에서 관리" 안내, 해지/연장 버튼 없음 |

---

## 4. 어떻게 동작하나 — 흐름

### 4-1. 구매 (클라 → 서버)

```
[앱] 요금제 화면 "PREMIUM 시작" 탭
  → InAppPurchase.buyNonConsumable(premium_yearly)      // Play 결제 시트
  → (Play 결제 완료) purchaseStream 으로 PurchaseDetails(status=purchased) 수신
  → 구매 JSON 에서 purchaseToken 추출
  → POST /api/v1/bands/{bandId}/plan/google/verify  { purchaseToken }   // 밴드장 JWT
       [서버] StoreSubscriptionService.verifyGooglePurchase
         1. accessGuard.requireLeader
         2. billingGateway.fetch(GOOGLE_PLAY, token)      // Play Developer API, 트랜잭션 밖
              - 상태가 grantsPremium() 아니면 → 402 PURCHASE_NOT_VERIFIED
              - 일시 실패(StoreBillingUnavailableException) → 402 (클라 재시도 유도)
         3. grantPremium(bandId, sub)
              - 토큰이 다른 밴드에 연결됨 → 409 PURCHASE_ALREADY_LINKED
              - 밴드 FREE → applyUpgrade(expiry, orderId, GOOGLE_PLAY, token)  // 짧은 트랜잭션
                            + 밴드의 READY 미디어 만료일을 NULL(무제한)로
              - 밴드 PREMIUM(쿠폰 등) → applyStoreRenew(...)  // 만료일 연장 + 토큰 기록
         4. sub.acknowledged 아니면 billingGateway.acknowledge(...)  // 실패해도 응답 안 깸
  → 200 { tier: PREMIUM, ... }
  → [앱] 성공 시에만 completePurchase(p)   // 실패면 미완료로 두어 다음 실행에 재유입
```

### 4-2. 갱신·해지·환불 (RTDN 웹훅)

Play 가 구독 상태가 바뀔 때 Pub/Sub 토픽에 메시지를 넣고, **push 구독**이
`POST https://api.bandule.com/api/v1/webhooks/google-play?token=<시크릿>` 로 전달.

```
[GooglePlayWebhookController.receive]
  1. WebhookAuthenticator.isAuthorized(Authorization 헤더, ?token=)
       - OIDC Bearer 검증 통과  또는  공유 시크릿 일치  →  OK
       - 아니면 403
  2. base64(message.data) → DeveloperNotification → subscriptionNotification{notificationType, purchaseToken}
  3. StoreSubscriptionService.handleGoogleNotification(messageId, type, token)
       - processed_store_events 에 messageId 있으면 → return (멱등)
       - findBandIdByPurchaseToken(token)
           · 없음 + grant 타입(RENEWED 등) → StoreWebhookRetryException → 503 재전송
           · 없음 + 종료성 타입 → markProcessed, return 200
       - type 별:
           PURCHASED/RENEWED/RECOVERED/RESTARTED/IN_GRACE_PERIOD
               → billingGateway.fetch 재조회, grantsPremium 이면 grantPremium
               → 조회 실패면 StoreWebhookRetryException(503)
           CANCELED (자동갱신 끔)  → applyCancelAtPeriodEndIfPremium (티어 유지, canceled=true)
           REVOKED  (환불·강제취소) → applyRevoke  (즉시 FREE + 미디어 만료 시각 = now, 유예 0)
           EXPIRED / ON_HOLD       → applyDowngradeIfPremium (FREE + 미디어 30일 유예)
       - markProcessed(messageId)   // 처리 성공 후에만
  4. 항상 200 (인증 실패·재시도 예외 제외)
```

핵심 성질:
- **멱등** — `messageId` 기록 + 모든 반영이 멱등이라 재전송/순서 뒤바뀜에 안전.
- **자기수정** — 이벤트가 빠져도 다음 이벤트가 스토어에서 현재 상태를 다시 읽어 맞춘다.
  게다가 `PlanExpirationJob`(야간 배치)이 `expires_at` 지난 PREMIUM 을 FREE 로 내리는 안전망.

### 4-3. 돈 흐름 (참고)

```
유저 카드 → Google 이 수납 → 서비스 수수료 15% 차감(축소 수수료 프로그램 등록됨) → 월 1회 정산(다음 달 15일 전후) → 등록 은행계좌
```
- 환불 시 잔액에서 차감. 최소 지급액 문턱 있음(못 넘으면 이월).
- 한국 소비자 부가세(10%)는 Google 이 대신 징수·납부. 사업소득세는 별도로 사업자가 낸다.

---

## 5. 직접 확인하는 법

### 5-1. 코드 검증 (지금 가능)

```bash
cd C:\band\bandApp
./gradlew build              # 백엔드 전체 + 통합 테스트 (CI 와 동일). Docker 필요
cd client
flutter analyze              # 에러 0
flutter test                 # 56개 통과
```
> 이 저장소를 여는 개발 PC 에 Docker 데몬이 없으면 Testcontainers 통합 테스트는 로컬에서 못 돈다.
> **CI(우분투 러너)가 대신 돌린다** — 아래 §6 링크.

로컬에서 순수 단위 테스트만:
```bash
./gradlew test --tests 'com.yeka.bandapp.plan.BandPlanTest' \
  --tests 'com.yeka.bandapp.plan.NoOpStoreBillingGatewayTest' \
  --tests 'com.yeka.bandapp.plan.GooglePlaySubscriptionMapperTest' \
  --tests 'com.yeka.bandapp.plan.WebhookAuthenticatorTest'
```

### 5-2. 스토어 없이 흐름 보기 (no-op 게이트웨이)

`app.plan.billing.gateway` 를 안 건드리면 `NoOpStoreBillingGateway` 가 뜬다. 통합 테스트가
이 경로로 verify·웹훅 전 시나리오를 검증한다:

- `PlanSubscriptionIntegrationTest` — verify → PREMIUM(미디어 무제한), 재검증 → 연장,
  같은 토큰 다른 밴드 → 409, 동시 verify → 500 없음
- `GooglePlayWebhookIntegrationTest` — RENEWED 연장 / REVOKED 즉시 강등+미디어 즉시 만료 /
  중복 messageId → 1회 / 시크릿 틀림 → 403 / 모르는 토큰(종료성) → 200, (grant) → 503
- `PlanGatewayContractIntegrationTest` — 검증 실패 → 402, 상태·미디어 불변

### 5-3. 실제 Google 으로 켜기 (슬라이스 0 완료 후)

**Play Console 준비 (사람):**
1. 구독 상품 `premium_yearly`, 기본 요금제 = ₩19,000 / 1년
2. Google Cloud 서비스 계정 생성 → Play Console > 사용자 및 권한에서 "재무 데이터 보기"
   권한 부여 → JSON 키 발급 → 서버 `secrets/play-developer-sa.json` 로 마운트(`chown 999:999`)
3. RTDN: Google Cloud Pub/Sub 토픽 생성 → Play Console > 수익 창출 설정 > 실시간 개발자 알림에
   토픽 등록 → **push 구독** 만들어 엔드포인트를
   `https://api.bandule.com/api/v1/webhooks/google-play?token=<랜덤시크릿>` 로.
   (인증 서비스 계정을 지정하면 OIDC 도 자동으로 동작 — `PLAN_BILLING_PUBSUB_*` 설정 시)
4. 라이선스 테스터에 본인·테스터 계정 등록
5. 서명된 AAB 를 **내부 테스트 트랙**에 올림 (검증 API 는 트랙에 올라간 앱에만 동작)

**서버 `.env.prod` 추가 후 앱만 재기동:**
```
PLAN_BILLING_GATEWAY=google
PLAN_BILLING_GOOGLE_PACKAGE=com.yeka.bandule
PLAN_BILLING_GOOGLE_CREDENTIALS_PATH=/run/secrets/play-developer-sa.json
PLAN_BILLING_WEBHOOK_SECRET=<위 ?token= 과 같은 값>
# (선택, OIDC) PLAN_BILLING_PUBSUB_AUDIENCE=<push 구독 audience>
# (선택, OIDC) PLAN_BILLING_PUBSUB_SA=<Pub/Sub 인증 서비스 계정 이메일>
```
```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 \
  'cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d app'
# 로그에 "Google Play 결제 검증 활성화 package=com.yeka.bandule" 가 떠야 한다
```

### 5-4. 실기기 e2e (슬라이스 4)

라이선스 테스터 계정으로 로그인한 기기에서:
1. 요금제 화면 → "PREMIUM 시작 · ₩19,000 / 년" → Play 결제 시트 → 완료
   → 카드가 PREMIUM, 미디어 보관 "무제한"
2. Play 스토어 > 메뉴 > 구독 > 밴듈 > 해지 → 잠시 뒤 요금제 화면에 "해지 예약됨"(canceled)
3. Play Console 에서 그 구독을 환불 → 몇 분 내 REVOKED 웹훅 → 요금제 화면이 FREE,
   미디어에 유예 없이 만료 시각이 붙음
4. 서버 로그에 `RTDN` 처리 흔적, `processed_store_events` 에 messageId 행

---

## 6. 실제 검증 기록

| PR | 내용 | CI | 결과 |
|---|---|---|---|
| [#69](https://github.com/Yekapark/bandApp/pull/69) | 슬라이스 1 — 도메인 + 하드닝 | build 4m4s | ✅ 전체 통합 테스트 통과 |
| [#70](https://github.com/Yekapark/bandApp/pull/70) | 슬라이스 2 — Google 어댑터 | build 4m3s | ✅ + `GooglePlaySubscriptionMapperTest` |
| [#71](https://github.com/Yekapark/bandApp/pull/71) | 슬라이스 3 — OIDC + Flutter | build 3m56s · analyze-test 1m11s | ✅ 백엔드 통합 + `flutter analyze` 0 에러 + `flutter test` 56개 |
| [#72](https://github.com/Yekapark/bandApp/pull/72) | 문서 | build 4m8s | ✅ |

merge 커밋(main): `49e949d`(#69) · `ddccca3`(#70) · `d211cae`(#71) · `84f6016`(#72).

로컬에서 확인한 것: 순수 단위 테스트 4종(위 §5-1) 통과, `./gradlew compileJava compileTestJava`
성공, `flutter analyze`(에러 0) · `flutter test`(56개) 통과. **Docker 없는 PC 라 통합 테스트는
CI 로만 검증.**

아직 안 한 것: **실제 Play 결제·웹훅 e2e**(슬라이스 0·4), 앱 스토어 심사 제출.

---

## 7. 알려진 이슈 / 제약

- **iOS 없음.** 클라 iOS 타깃 부재. `Store.APP_STORE`·StoreKit 어댑터는 iOS 빌드가 생길 때.
- **`obfuscatedAccountId` 대조 없음.** 지금은 "한 토큰 = 한 밴드" 만으로 재사용을 막는다. Play Billing
  구매 시 `obfuscatedAccountId` 에 밴드 id 를 실어 보내고 서버가 대조하면 더 강하다 — 슬라이스 후속.
- **웹훅 OIDC 는 실 토큰으로 통합 테스트 불가.** `WebhookAuthenticatorTest` 가 가짜 verifier 로
  판정 로직만 검증. 공유 시크릿 경로는 통합 테스트로 커버.
- **acknowledge 실패 재시도 잡 없음.** 실패 시 로그만. Play 자동환불 → REVOKED 웹훅으로
  자기수정되지만, 사용자는 3일간 "PREMIUM 인데 곧 환불" 상태일 수 있다.
- **쿠폰 → 실결제 전환 시 만료일이 스토어 값으로 덮인다.** 쿠폰이 준 잔여기간과 결제 기간을
  합산하지 않는다(엣지 케이스, BUILD_PLAN 요구사항 아님).
- **grant 이벤트 무한 재시도 가능성.** 아직 밴드에 안 붙은 토큰의 RENEWED 는 계속 503 을 준다.
  Pub/Sub 가 (기본 7일) 재시도하다 포기하므로 영구 루프는 아니지만, 클라 verify 가 영영 안 오면
  로그 노이즈가 생긴다.
- **`NoOpStoreBillingGateway` 는 운영에서 절대 쓰면 안 된다.** 아무 토큰이나 ACTIVE 로 통과시킨다.
  `PLAN_BILLING_GATEWAY` 를 명시적으로 `google` 로 두는 것이 유일한 방어 — 운영 `.env.prod` 확인 필수.

---

## 8. 커밋 · CI 링크

- 브랜치·PR: `phase-12-iap`(#69) → `phase-12-iap-slice2`(#70) → `phase-12-iap-slice3`(#71) →
  `docs/phase-12-status`(#72). 전부 `main` 머지.
- CI run: #69 `34228030717` · #70 `34229701724` · #71 `34231692313`(build)/`34231692265`(analyze-test) ·
  #72 `34232908180`.

---

## 9. 다음 — NEXT.md "Phase 12" 절

1. **슬라이스 0** (사람): Play Console 결제 프로필 계좌 확인(진행 중) + 축소 수수료 15% 프로그램
   등록(완료) + 구독상품·서비스계정·Pub/Sub·라이선스 테스터·내부테스트 AAB.
2. `.env.prod` 에 `PLAN_BILLING_*` 넣고 앱 재기동 → 로그로 활성화 확인.
3. **슬라이스 4**: 라이선스 테스터 실기기로 구매→PREMIUM→해지→만료, 환불→즉시강등 확인.
4. 그 뒤 스토어 심사 제출.
