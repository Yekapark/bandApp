# 문제 기록

> **겪은 문제와 해결을 전부 남기는 곳.** 같은 함정을 두 번 밟지 않기 위한 파일이다.
> 새 세션에서 작업을 이어받는 사람(또는 Claude)이 먼저 훑어야 한다.

## 이 파일에 적는 규칙

**버그·장애·"왜 안 되지" 를 하나 해결할 때마다 여기에 항목을 추가한다.** 코드만 고치고
넘어가지 않는다 — 원인이 코드에 안 보이는 종류(플랫폼 기본값, 외부 서비스 설정, 빌드 옵션)가
대부분이라 기록이 없으면 다음에 똑같이 헤맨다.

- **최신 항목이 위로.** 날짜별로 묶는다.
- 항목마다 **증상 / 원인 / 해결 / 확인법** 네 가지를 적는다. 넷 다 없으면 미완성이다.
- 원인은 "무엇이 잘못됐다" 가 아니라 **"왜 그렇게 됐다"** 까지 적는다.
  (예: "플래그를 일찍 세워서" ❌ → "실패할 수 있는 작업 앞에 세워서 실패 후 재시도가 막혀서" ✅)
- 코드를 안 쓰는 사람도 읽을 수 있게 쓴다. 파일·줄 번호는 괄호로 덧붙이는 정도로.
- 해결하지 못하고 우회한 것도 적는다. **"아직 안 고침" 이라고 쓰는 게 안 적는 것보다 낫다.**

---

## 2026-09-10 — 계정 삭제 URL이 홈페이지를 보여 줘 Play 등록에 쓸 수 없었다

**증상** — `https://bandule.com/account-deletion/` 이 `200`을 반환하지만 계정 삭제 안내가 아니라
홈페이지를 보여 줬다. Play Console의 계정 삭제 URL에 넣어도 사용자가 웹에서 삭제를 요청할 수
없었다.

**원인** — Cloudflare Pages가 게시하는 `site/`에 `account-deletion/index.html`이 없었다. 없는
경로가 홈페이지로 대체되어 상태코드만 보면 정상처럼 보였다.

**해결** — 법률 문서의 원본인 `docs/legal/account-deletion-ko.md`를 만들고 `site/build.py`가
`site/account-deletion/index.html`을 생성하도록 추가했다. 앱 안 탈퇴 경로, 이메일 요청 링크,
90일 보관 범위, 공동 밴드 기록, Google Play 구독 별도 해지를 한 페이지에 명시했다.

**확인법** — `python site/build.py` 실행 후 `site/account-deletion/index.html`에
`밴듈 계정 삭제 안내`와 `mailto:notice@bandule.com`이 있는지 확인한다. 배포 후에는
`https://bandule.com/account-deletion/`의 제목과 본문을 직접 열어 홈페이지가 아닌지 확인한다.

---

## 2026-09-09 — 밴드에 안 붙은 구매 알림이 7일 동안 초당 한 번씩 서버를 때렸다

**증상** — Nginx 접근 로그에 RTDN 푸시가 **10분에 600건**, 전부 `503`.
앱 로그는 `Google Play 웹훅: 재시도 요청 — type=4 인데 아직 밴드에 안 붙은 토큰 — 재전송 대기`
한 줄로 도배됐다.

**원인** — 구매·갱신 알림인데 그 구매 토큰이 아직 어느 밴드에도 안 붙어 있으면, 서버가
"클라이언트 `verify` 가 곧 올 테니 다시 보내 달라" 는 뜻으로 `503` 을 준다(경합 대비). 그런데
**끝내 안 오는 경우를 안 봤다.** 실제로 그런 일이 있었다 — 클라이언트 토큰 추출 버그(`0.1.0+26`)로
`verify` 가 아예 호출되지 않은 구매가 있었고, 그 토큰은 영영 밴드에 안 붙는다. 그러면 조건이
바뀔 리 없는데도 Pub/Sub 는 메시지 보존기간(기본 7일) 내내 재전송한다. 기다림에 **끝을 안 정한**
재시도는 조건이 영영 안 바뀌는 순간 그대로 무한 루프가 된다.

**해결** — 재전송 요청에 시간 제한을 뒀다
(`plan/service/StoreSubscriptionService.java`, `GRANT_RETRY_WINDOW = 1시간`).
Pub/Sub 봉투의 `publishTime` 은 **최초 발행 시각이라 재전송돼도 그대로**여서 메시지의 나이가 된다.
한 시간이 지난 grant 이벤트는 포기하고 `200` + 처리완료 기록으로 끝낸다. 포기해도 잃는 게 없다 —
PREMIUM 부여는 클라이언트 `verify` 가 하고, 늦게라도 `verify` 가 오면 그쪽이 스토어에 직접 물어
등급을 올린다. 시각을 못 읽으면 예전처럼 재전송을 요청한다(모른다고 정상 경합을 버리지 않는다).

**확인법** — 통합 테스트 `a_stale_grant_event_is_given_up_on_instead_of_retried_forever`
(두 시간 전 `publishTime` → `200`). 운영에서는:

```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 "docker logs --since 10m bandapp-nginx-1 2>&1 | grep -c 'webhooks/google-play'"
```

배포 뒤 밀린 메시지들이 한 번씩 `200` 을 받고 사라지면서 건수가 0 에 수렴해야 한다.

**실측 (2026-09-09)** — 배포 전 분당 47~101건이 전부 `503`. 15:10 에 재기동(502), **15:11:07 에
단 한 건이 `200`** 을 받고 그대로 멎었다. 그 순간 앱 로그는
`RTDN: 모르는 구매 토큰 (밴드 없음) type=4 — 무시`(재전송 창을 벗어나 포기한 자리다.
이 분기는 `RTDN 처리 완료` 를 찍지 않으니 그 문구로 찾으면 안 나온다).
이후 2분 간격 3회 측정 전부 **0건**.

> 여기서 하나 배운다 — **밀린 메시지가 수백 건이 아니라 한 건이었다.** 한 메시지가 초당
> 한 번꼴로 재전송되고 있었을 뿐이라, 그 하나를 ack 하자 폭풍이 통째로 끝났다. 로그 건수만
> 보고 "메시지가 많다" 고 읽으면 원인을 잘못 짚는다.

---

## 2026-09-09 — 환불만 하고 "사용 권한 취소" 를 안 하면 REVOKED 알림이 오지 않는다

**증상** — Phase 12 마지막 검증(환불 → 즉시 FREE)에서, Play Console 로 테스트 구독을
환불했는데 서버에 `type=12`(REVOKED) 웹훅이 몇 분이 지나도 오지 않았다. 앱은 계속 PREMIUM.

**원인** — Play Console 의 환불 대화상자에서 **"사용 권한 취소"** 를 따로 체크해야 한다.
체크를 안 하면 Google 은 **돈만 돌려주고 구독은 그대로 살려 둔다.** 사용자는 여전히 구독자라
Google 이 보낼 알림 자체가 없다. 환불과 권한 취소가 한 동작이라고 생각하기 쉬운데 별개다.

**해결** — Play Developer API 로 사용 권한 취소를 직접 쏜다. `deploy/play-revoke.sh` 를 만들었다.
`band_plans` 에서 그 밴드의 구매 토큰을 읽어 `subscriptionsv2 …:revoke` 를 호출한다.

```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'bash -s 4' < deploy/play-revoke.sh
# 이미 환불된 주문이라 fullRefund 가 거부되면:
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'REFUND_TYPE=proratedRefund bash -s 4' < deploy/play-revoke.sh
```

**확인법** — 취소 후 몇 분 안에 서버 로그에 `RTDN 처리 완료 type=12 bandId=…` 가 뜬다.
2026-09-09 실측: 14:34:07 에 `type=12`, 같은 순간(`14:34:07.958262`)에 `band_plans` 가
`tier=FREE`·`store`/`purchase_token` NULL 로 바뀌고 그 밴드 READY 미디어 4건의 `expires_at`
도 같은 값으로 붙었다(유예 0). 2초 뒤 온 `type=13` 은 **"모르는 구매 토큰 (밴드 없음)"** 으로
무시됐는데, 이게 오히려 강등이 토큰을 실제로 떼어냈다는 증거다.

> **DB 를 손으로 고쳐 대신하지 말 것.** 확인하려는 건 "웹훅이 강등을 만드는가" 이므로,
> `band_plans` 를 직접 UPDATE 하면 결과만 흉내내고 그 경로를 한 번도 안 탄다.

---

## 2026-09-09 — RTDN 을 정상 처리해도 서버 로그에 아무것도 안 남았다

**증상** — 운영에서 웹훅이 실제로 반영됐는지 로그로 확인할 방법이 없었다.
`phase-12-iap.md` §5-4 의 확인 항목 "서버 로그에 RTDN 처리 흔적" 이 애초에 불가능했다.

**원인** — `StoreSubscriptionService.handleGoogleNotification` 이 **실패 경로만** 로그를 찍고
있었다(인증 실패·재전송 요청·예외). 성공은 조용히 지나가서 **"조용하면 정상"** 과
**"조용하면 아무 일도 안 일어남"** 이 구분되지 않았다. 개발 중엔 통합 테스트가 결과를
직접 보니까 로그가 아쉽지 않았고, 그대로 운영에 나갔다.

**해결** — 처리 완료 지점에 INFO 한 줄
(`src/main/java/com/yeka/bandapp/plan/service/StoreSubscriptionService.java`):

```
RTDN 처리 완료 type={} bandId={} messageId={}
```

**확인법**

```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 "docker logs --since 30m bandapp-app-1 2>&1 | grep 'RTDN 처리 완료'"
```

구매하면 `type=4`, 환불·권한취소하면 `type=12`, 만료면 `type=13` 이 밴드 id 와 함께 찍힌다.

---

## 2026-09-09 — 서버 스크립트에서 `.env.prod` 를 `source` 하면 깨진다

**증상** — `deploy/play-revoke.sh` 첫 실행이
`/opt/bandapp/.env.prod: line 68: syntax error near unexpected token 'newline'` 로 죽었다.

**원인** — `.env.prod` 는 **docker compose 가 읽는 형식이지 셸 스크립트가 아니다.**
compose 는 `KEY=값` 을 문자 그대로 읽지만, 셸의 `.`(source)은 그 줄을 **셸 코드로 실행**한다.
그래서 값에 `#`·`<`·`>`·따옴표 같은 게 들어 있으면 문법 에러가 난다. 비밀번호·키를
`openssl rand -base64` 로 만들면 특수문자가 섞이므로 사실상 언제든 터질 수 있다.

**해결** — 파일 전체를 읽지 말고 **필요한 값만 뽑는다.**

```bash
envget() { grep -m1 "^$1=" "$COMPOSE_DIR/.env.prod" | cut -d= -f2- | sed "s/[[:space:]]*#.*$//; s/[[:space:]]*$//"; }
DB_USERNAME=$(envget DB_USERNAME); DB_NAME=$(envget DB_NAME)
```

**확인법** — `bash -n deploy/play-revoke.sh` 로 문법을 보고, 실제로 돌려서
`band=4 token=…(…자)` 가 찍히면 값이 제대로 읽힌 것이다.

---

## 2026-09-09 — 운영 웹훅이 공유 시크릿으로만 인증되고 있었다

**증상** — 앱 기동 로그에
`Pub/Sub OIDC 검증 비활성 (google-pubsub-audience 미설정) — 웹훅은 공유 시크릿으로만 인증`.
웹훅 URL 의 `?token=` 하나가 유일한 방어선이었고, 그 값은 **Nginx 접근 로그에 쿼리스트링으로
평문으로 남는다.**

**원인** — 슬라이스 0 문서에서 `PLAN_BILLING_PUBSUB_AUDIENCE`·`_SA` 가 "(권장)" 으로 적혀 있어
필수처럼 보이지 않았다. 시크릿만으로도 웹훅이 동작하니 넘어갔고, 그대로 운영에 나갔다.

**해결** — 코드는 이미 다 있었다(`docker-compose.prod.yml` 92·93줄, `application.yml` 140·141줄).
설정만 하면 된다. `WebhookAuthenticator` 는 OIDC 와 시크릿을 **OR** 로 받으므로 순서가 중요하다 —
**OIDC 를 켜는 것만으로는 아무것도 안 단단해진다.** `?token=` 이 계속 통하기 때문이다.

1. GCP Pub/Sub 구독 `play-rtdn-push` → 인증 사용 설정, 서비스 계정
   `bandule-play-api@bandule.iam.gserviceaccount.com`. **audience 를 비워두지 말 것** —
   비우면 GCP 가 푸시 URL 을 그대로 쓰는데 거기 `?token=` 이 붙어 서버 설정값과 어긋난다.
   `https://api.bandule.com/api/v1/webhooks/google-play` 로 명시한다.
2. 서버 `.env.prod` 에 두 줄 **추가**(덮어쓰지 않는다 — `IMAGE_TAG` 가 밀리면 옛 이미지로 롤백된다):
   `PLAN_BILLING_PUBSUB_AUDIENCE`, `PLAN_BILLING_PUBSUB_SA`. 재기동.
3. **OIDC 가 진짜 인증하는지 증명한다** — 푸시 URL 에서 `?token=` 을 뗀다. 시크릿이 안 실린
   요청이 `403` 이 아니면 통과시킨 건 OIDC 뿐이다.
4. 증명된 뒤에야 `.env.prod` 의 `PLAN_BILLING_WEBHOOK_SECRET` 을 비워 OIDC 전용으로 닫는다.

**확인법** — 기동 로그에 `Pub/Sub OIDC 검증 활성 audience=…`, 컨테이너 안에서
`echo ${#PLAN_BILLING_WEBHOOK_SECRET}` 가 `0`, 그리고 Nginx 로그에서 `?token=` 없는 푸시의
상태코드가 `403` 이 아닐 것. 2026-09-09 실측: 토큰 없는 요청 `503` 만, `403` 0건.

> 옛 시크릿은 Nginx 로그에 평문으로 남아 있다. 되돌릴 일이 생겨도 **재사용하지 말고 새로 만든다.**

---

## 2026-09-09 — 카카오 로그인이 옛 앱(bandapp)으로 붙고, 고쳐도 브라우저만 맴돌았다

**증상** — 두 단계로 나타났다.

1. 카카오 동의 화면에 **`bandapp`** 이 떴다. 써야 할 앱은 `Bandule` 이다.
2. 앱 키를 바꾼 뒤에는 동의 화면이 `Bandule` 로 바뀌었지만, **계속하기를 누르면**
   상단이 `웹페이지 불러오는 중` ↔ `카카오계정으로 로그인` 만 반복하고 앱으로 안 돌아왔다.

**원인** — **같은 카카오 앱 키가 두 파일에 나뉘어 있었다.**

| 파일 | 쓰이는 곳 |
|---|---|
| `client/dart_defines.json` 의 `KAKAO_NATIVE_APP_KEY` | 다트 `KakaoSdk.init` — **로그인을 어느 앱으로 요청할지** |
| `client/android/local.properties` 의 `kakao.appKey` | `build.gradle.kts` → 매니페스트의 `kakao{키}://oauth` 스킴 — **로그인 끝나고 앱으로 돌아올 주소** |

둘 다 `.gitignore` 대상이라 PC 마다 손으로 넣는 파일이고, 이름도 위치도 달라서
**한쪽만 고치면 아무 경고 없이 어긋난다.**

- 처음엔 둘 다 `bandapp` 이었다 → 동의 화면에 `bandapp`.
- `dart_defines.json` 만 `Bandule` 로 바꿨다 → 카카오는 `Bandule` 로 로그인시키고 인가 코드를
  `kakao{Bandule키}://oauth` 로 돌려보내는데, 앱은 `kakao{bandapp키}://oauth` 만 받게
  선언돼 있으니 **받을 액티비티가 없다.** 브라우저가 돌아갈 곳을 못 찾아 그 자리에서 맴돈다.

증상이 "실패" 가 아니라 "무한 로딩" 이라 원인을 짐작하기 어려웠다. 서버 로그에도 안 남는다 —
인가 코드가 앱까지 못 와서 서버 호출 자체가 일어나지 않기 때문이다.

**해결** — **출처를 `dart_defines.json` 하나로 합쳤다.**
`build.gradle.kts` 가 `local.properties` 대신 `../dart_defines.json` 을 직접 읽는다
(`groovy.json.JsonSlurper`, 의존성 추가 없음). 이제 그 파일 한 줄만 고치면 다트와 매니페스트가
같이 따라간다. `local.properties` 에 옛 `kakao.appKey` 가 남아 있으면 빌드 로그에
"더 이상 쓰이지 않는다" 경고를 찍는다 — 그걸 고치고 왜 안 바뀌냐고 또 헤매지 않게.

**확인법** — 앱에서 카카오 로그인 → 동의 화면에 **Bandule** 이 뜨고, 계속하기 후 **앱으로
돌아오면** 정상이다. 빌드된 매니페스트의 스킴이 실제로 바뀌었는지는:

```bash
grep -rho 'android:scheme="kakao[^"]*"' client/build/app/intermediates/merged_manifests/prodRelease/
```

> 같은 뿌리의 함정이 하나 더 있다. **키 해시는 서명 키마다 다르다.** Play 앱 서명을 쓰면
> 사용자가 받는 앱은 구글 키로 재서명되므로 **Play Console → 앱 서명 → 앱 서명 키 인증서**의
> SHA-1 을 base64 로 바꿔 등록해야 한다(업로드 키 인증서가 아니다). USB 로 디버그 빌드를
> 깔아 테스트하려면 디버그 키의 해시를 **추가로** 등록해야 한다 — 여러 개 등록된다.
> ```bash
> echo "<SHA-1>" | tr -d ':' | xxd -r -p | openssl base64
> ```
## 2026-09-09 — 결제 검증이 "상태만" 보고 통과시키던 것 셋

**증상** — 없다. 배포 전 결제 경로 점검에서 나온 것들이라 아직 아무도 안 당했다.
Play 결제를 실제로 켜기 전에 막아 둔다.

**원인과 해결**

**(1) 우리 상품인지 안 봤다.** `purchases.subscriptionsv2.get` 은 **패키지 단위**라
이 앱의 어떤 구독 토큰이든 조회된다. 그런데 응답의 `productId` 를 읽기만 하고
(acknowledge 에 넘기려고) 대조하지는 않았다. 상품이 `premium_yearly` 하나뿐인 지금은
무해하지만, **더 싼 상품을 하나라도 추가하는 순간 그 토큰으로 PREMIUM 을 받는 길이 열린다.**
상품이 늘기 전에 막는 게 맞다 — 늘어난 뒤에는 "왜 이 사람만 싸게 샀지"를 정산에서 발견하게 된다.
→ `app.plan.billing.google-product-id`(기본 `premium_yearly`)와 대조한다.

**(2) 만료일이 없어도 통과시켰다.** 매퍼가 `lineItems` 에서 만료 시각을 못 찾으면
`null` 을 돌려주고, 그게 그대로 `band_plans.expires_at` 에 NULL 로 저장된다. 만료 배치의
조건이 `expires_at < now` 라 **NULL 은 영원히 걸리지 않는다** = 공짜 무기한 PREMIUM.
정상 구독이면 항상 값이 오지만, 안 왔을 때 조용히 무기한을 주는 쪽으로 실패하고 있었다.
→ `expiryTime == null` 이면 거부(402).

(1)(2)는 `StoreSubscriptionService.grantable()` 한 곳에 모았다. 사용자 검증과 RTDN 웹훅이
둘 다 이 판정을 지나므로, 한쪽만 고치고 다른 쪽이 남는 일이 없다.

**(3) 웹훅 OIDC 가 "누가 보냈나"를 안 봤다.** `google-pubsub-service-account` 대조가
**선택**이었다. audience 는 우리 웹훅 URL 이고, **그 값을 audience 로 하는 진짜 구글 OIDC
토큰은 아무 GCP 계정이나 자기 서비스 계정으로 발급할 수 있다.** 서명·발급자·만료가 전부
정상이라 검증기도 통과시킨다. 즉 audience 만 설정하면 **아무나 웹훅을 부를 수 있었고**,
환불(REVOKED)·만료 이벤트를 임의로 밀어 넣어 남의 밴드를 FREE 로 떨어뜨릴 수 있었다.
→ audience 가 있는데 서비스 계정이 없으면 OIDC 경로를 **아예 열지 않는다**(기동 로그에 에러).

**확인법** — `PlanPurchaseValidationIntegrationTest`(다른 상품 402 · 만료일 없음 402 ·
정상 구매는 200 대조군), `WebhookAuthenticatorTest.oidc_path_is_closed_when_the_service_account_is_not_configured`.
운영에서는 `.env.prod` 에 `PLAN_BILLING_PUBSUB_AUDIENCE` 를 넣었다면
`PLAN_BILLING_PUBSUB_SA` 도 반드시 함께 넣는다 — 안 넣으면 웹훅이 공유 시크릿으로만 인증된다.
## 2026-09-09 — 회색 구름인데 Cloudflare realip 을 켜 둬서 IP 레이트리밋이 뚫려 있었다

**증상** — 눈에 보이는 증상이 없다. 배포 전 점검에서 nginx 접근 로그를 읽다 발견했다.
로그인 무차별 대입·초대코드 대입·가입 스팸을 막는 **IP 기준 상한이 전부 우회 가능한 상태**였다.

**원인** — 설정과 DNS 상태가 어긋나 있었다.

`app.conf.template` 이 `cloudflare-realip.conf` 를 include 하고 있었고, 그 파일은
"Cloudflare 대역에서 온 요청이면 `CF-Connecting-IP` 헤더를 진짜 클라이언트 IP 로 믿어라"
라고 말한다. **주황 구름(Proxied)일 때는 맞는 설정이다** — 오리진에 Cloudflare 만 닿으니까.

그런데 `api.<도메인>` 이 **회색 구름(DNS only)** 으로 되돌아가 있었다. 회색이면 오리진이
인터넷에 직접 열려 있고, "Cloudflare 대역에서 왔다"는 전제가 깨진다. Cloudflare 대역에서
요청을 보내는 건 누구나 공짜로 할 수 있다 — 무료 Worker 의 아웃바운드가 CF 대역에서 나가고,
자기 도메인을 주황 구름으로 걸어 이 오리진 IP 로 향하게 해도 된다. 그러면:

```
공격자 → (CF 대역에서) POST https://<오리진 IP>/api/v1/auth/login
          Host: api.<도메인>
          CF-Connecting-IP: 1.2.3.4      ← 매 요청 아무 값
  → nginx: 피어가 set_real_ip_from 안 → 헤더 신뢰 → $remote_addr = 1.2.3.4
  → proxy-headers.conf: X-Forwarded-For $remote_addr (위조값)
  → 톰캣 RemoteIpValve → getRemoteAddr() → ClientIp.of() = 1.2.3.4
```

헤더만 바꿔 가며 무제한이다. 역설적이게도 **직접 XFF 를 위조하는 경로는 잘 막혀 있다**
(`ClientIp` 가 헤더를 안 읽고, nginx 가 XFF 를 이어붙이지 않고 덮어쓴다). 앞문만 열려 있었다.

왜 이렇게 됐나 — 2026-09-06 에 주황 구름을 켰고(LAUNCH_CHECKLIST 5단계 "완료"),
그 뒤 회색으로 되돌렸는데 **되돌린 기록이 없다.** 그리고 `DEPLOY.md` 에
"회색 구름으로 되돌려도 같은 설정이 그대로 맞다" 고 **틀린 설명**이 적혀 있어서, 되돌릴 때
nginx 설정을 함께 볼 이유가 없었다. 설정 하나가 두 상태에 다 맞다고 믿은 것이 원인이다.

**해결** — 세 가지.

1. `app.conf.template` 의 realip include 를 **주석 처리**했다(회색 구름 기준). 켜고 끄는
   조건과 "주황으로 바꾸면 반드시 되살릴 것"을 그 자리에 크게 적었다 — 안 되살리면 반대로
   깨진다(전 사용자가 CF IP 몇 개로 뭉쳐 서로의 레이트리밋을 소진).
2. `DEPLOY.md` 의 틀린 문장을 고치고, 어느 쪽으로 바꾸든 확인 절차를 넣었다.
3. `test-realip.sh` 가 **템플릿의 include 상태를 읽어 ①의 기대값을 자동으로 맞추게** 했다.
   예전에는 ①이 "CF 대역의 CF-Connecting-IP 인정"을 무조건 기대해서, 회색 구름에서도
   초록불이 떴다 — 취약한 상태를 정상이라고 확인해 주고 있었다. 이제 설정과 기대가
   한 곳(템플릿)에서 나온다.

**확인법**

```bash
nslookup api.<도메인>     # 오리진 IP 그대로 = 회색, Cloudflare 대역 = 주황
MSYS_NO_PATHCONV=1 sh deploy/nginx/test-realip.sh   # 다섯 케이스 전부 통과해야 한다
```

`test-realip.sh` 는 도커로 Cloudflare 대역·바깥 두 네트워크를 만들어 위조가 통하는지 본다.
윈도우에서는 **리포가 `C:\...\Temp` 같은 경로 아래 있으면 안 된다** — Git Bash 의 `/tmp` 를
Docker Desktop 이 마운트하지 못해 인증서 생성 단계에서 조용히 죽는다.

**아직 안 한 것** — 왜 주황에서 회색으로 되돌렸는지 모른다. Cloudflare 대시보드 →
계정 관리 → **감사 로그**에서 DNS 레코드 변경 이력을 확인해야 한다. 이유를 모른 채
주황으로 다시 켜면 그때 겪었던 문제가 그대로 재발한다(무료 플랜 Bot Fight Mode 가 앱의
`Dart/3.13 (dart:io)` 요청을 봇으로 보고 막는 유형이 흔하다).
## 2026-09-09 — 모르는 주소로 가입이 들어와 인증 메일이 반송됐다

**증상** — 10:08(KST) 운영 발신 계정으로 반송 메일이 왔다.

```
주소를 찾을 수 없음 — example.com 도메인을 찾지 못하여
testuser12345@example.com 주소로 메일을 전송하지 못했습니다.
```

DB 를 보니 실제로 계정이 있었다. `users.id = 9`, `testuser12345@example.com`,
이름 `Test User`, `social_provider` 는 NULL(= 이메일 가입), 생성 `2026-09-09 01:08:29+00`.
**운영자도 테스터도 만든 적이 없는 계정이다.**

**원인** — 두 가지가 겹쳤다.

1. `POST /api/v1/auth/signup` 은 무인증 공개 엔드포인트이고, 도메인이 실재하는지 보지 않고
   **형식만 맞으면 계정을 만들고 곧바로 인증 메일을 쏜다.** `example.com` 은 RFC 2606 이
   문서·예제용으로 못 박은 예약 도메인이라 MX 레코드가 존재할 수 없다 — 보내면 100% 반송이다.
2. 도메인은 Let's Encrypt 인증서를 받는 순간 **인증서 투명성(CT) 로그에 공개된다.** 새 도메인은
   몇 시간 안에 자동 스캐너가 훑고, 흔한 API 경로에 `testuser12345@example.com` / `Test User`
   같은 전형적인 값을 넣어 본다. 계정 생성 시각이 정확히 반송 시각과 같은 것도 그 그림에 맞는다.
   (다만 **어느 IP 에서 왔는지 확인하기 전까지는 단정하지 않는다** — 확인법은 아래.)

이게 왜 위험한가: 발송이 Gmail SMTP 한 계정에 얹혀 있다. 반송이 쌓이면 발신 평판이 깎이고
Google 이 발송을 정지시킨다. 그러면 인증 메일만 죽는 게 아니라 **비밀번호 재설정과 신고 접수
알림까지 같이 죽는다.** 게다가 비밀번호 재설정 요청은 IP 당 분당 20회 제한뿐이라, 남의 주소로
**분당 20통**을 대신 쏘는 중계기로도 쓸 수 있었다(계정이 있는 주소에 한해).

**해결** — 두 겹으로 막았다.

1. **예약 도메인 가입 거부** — `EmailPolicy.requireDeliverable` 이 `example.com/.net/.org` 와
   `.test`·`.example`·`.invalid`·`.localhost`·`.local` 로 끝나는 도메인을 400
   `EMAIL_DOMAIN_NOT_ALLOWED` 로 돌려보낸다. 계정 자체가 안 만들어진다. 일회용 메일 도메인
   차단은 하지 않는다 — 목록을 계속 따라다녀야 하고 오탐이 곧 가입 거부라 값에 비해 비싸다.
2. **받는 주소당 분당 상한**(기본 3통, `app.ratelimit.email-per-address-per-min`) — 메일을
   보내는 경로가 넷(가입 인증·재발송·비밀번호 재설정·신고 알림)인데 전부 `EmailSender.send()`
   를 지나므로 거기 한 곳에 걸었다. 초과분은 **예외를 던지지 않고 조용히 버린다** — 429 를
   돌려주면 `PasswordResetService.request` 가 "이 주소는 가입돼 있다"를 알려 주는 꼴이 되고,
   그 메서드가 계정 존재 여부를 숨기려고 일부러 조용히 끝나는 설계가 무너진다.

**확인법** — 단위 테스트 `EmailPolicyTest`(예약 도메인 거부, 대소문자·공백 우회 불가,
`example.com.co.kr` 같은 진짜 주소는 통과). 운영에서는 가입 API 에 `a@example.com` 을 넣어
400 `EMAIL_DOMAIN_NOT_ALLOWED` 가 나오면 된다.

**아직 안 한 것** — 그 계정(`users.id = 9`)이 어디서 왔는지 확정하지 못했다. 아래로 확인한다.

```bash
# 그 시각 signup 요청의 출처 IP·User-Agent
ssh root@64.176.231.126   'cd /opt/bandapp && docker compose -f docker-compose.prod.yml logs nginx | grep "auth/signup"'
# 다른 정크 가입이 더 있는지
ssh root@64.176.231.126 'docker exec $(docker ps -qf name=postgres) psql -U bandapp -d bandapp   -c "SELECT id, email, created_at FROM users ORDER BY created_at DESC LIMIT 20;"'
```

한 번뿐이면 스캐너 한 방으로 보고 그 계정만 지우면 된다. 계속 들어오면 가입에 별도
레이트리밋(지금은 `/api/v1/auth/**` 공통 IP 당 20/분)을 더 좁혀야 한다.

---

## 2026-09-09 — 쿠폰 한 장을 한 사람이 통째로 태울 수 있었다

**증상** — 배포 전 점검 중 "쿠폰 ABC 를 팀장이 한 번, 팀원이 한 번 넣으면?" 을 따라가다 발견.
팀원은 애초에 못 넣고(밴드장만 가능, 403), 같은 밴드에서 두 번도 막힌다(409). 그런데
**밴드장이 밴드를 새로 만들어 같은 코드를 다시 넣으면 그냥 된다.** `max_uses` 가 100 이면
한 사람이 밴드 100개를 만들어 100장을 혼자 다 쓸 수 있었다.

**원인** — `plan_coupon_redemptions` 의 유니크가 `(coupon_id, band_id)` 하나뿐이었다(V12).
"같은 밴드에서 두 번" 만 생각하고 "같은 사람이 밴드를 갈아 가며" 를 안 봤다. 밴드 생성은
개수 제한도 레이트리밋도 없어서(`BandService.create`, 레이트리밋은 `/api/v1/auth/**` 에만
걸려 있다) 계정 하나로 밴드를 얼마든지 만들 수 있다. 횟수 상한 자체는 지켜지므로 손해가
무한하진 않지만, "여러 밴드에 맛보기를 뿌린다"는 쿠폰의 목적이 무너진다 — 코드가 커뮤니티에
한 번 새면 먼저 본 한 명이 전부 가져간다.

**해결** — `(coupon_id, redeemed_by)` 유니크를 하나 더 걸었다(V18). 한 계정은 한 쿠폰을
한 번만 쓴다. 애플리케이션 코드는 안 고쳐도 됐다 — `PlanCouponService` 의
`DataIntegrityViolationException` catch 가 이 위반도 그대로 `COUPON_ALREADY_USED`(409) 로
옮긴다. 사용 기록 INSERT 가 횟수 차감(`consume()`)보다 **먼저** 일어나는 순서라, 거부된
시도가 남의 횟수를 깎지도 않는다.

> 배포 전에 기존 데이터에 중복이 없는지 확인한다. 있으면 마이그레이션이 실패해 앱이 안 뜬다.
> ```sql
> SELECT coupon_id, redeemed_by, count(*) FROM plan_coupon_redemptions
>  GROUP BY coupon_id, redeemed_by HAVING count(*) > 1;
> ```

**확인법** — `PlanCouponIntegrationTest.one_account_cannot_spend_the_same_coupon_on_a_second_band`
(한 계정이 밴드 둘에 같은 코드 → 두 번째 409, 둘째 밴드는 FREE, `used_count` 는 1). 운영에서는
```sql
\d plan_coupon_redemptions   -- ux_plan_coupon_redemptions_user 가 보여야 한다
```

---

## 2026-09-09 — 운영에서 아무 문자열이나 넣으면 PREMIUM 1년이 공짜로 붙었다

**증상** — 배포 전 점검에서 발견. 운영에 올라간 서버에 밴드장 계정으로

```
POST /api/v1/bands/{밴드}/plan/google/verify   {"purchaseToken":"x"}
```

를 보내면 결제 없이 PREMIUM 1년이 붙는다. 반대로 Google Play 의 갱신·해지·환불 알림(RTDN)은
서버가 **전부 403 으로 거부**해서 하나도 반영되지 않는다.

**원인** — `docker-compose.prod.yml` 의 `app.environment` 에 `PLAN_BILLING_*` 가 한 줄도 없었다.
서버 `.env.prod` 에 값을 채워도 compose 가 컨테이너에 안 실어서, 앱 안에서는
`app.plan.billing.gateway` 가 **기본값 `noop`** 이 된다. 그러면 `NoOpStoreBillingGateway` 가
뜨는데(`matchIfMissing = true`), 이 구현은 개발·CI 편의용이라 토큰 접두사가
`invalid-`/`revoked-`/`expired-`/`hold-` 가 아니면 **무조건 "정상 구독 · 만료 1년 뒤"** 를
돌려준다. 결제 검증이 사실상 없는 상태로 돈 받는 기능이 열려 있었던 것이다.
웹훅 쪽은 같은 이유로 `PLAN_BILLING_WEBHOOK_SECRET` 도 안 실려 인증 수단이 하나도 없었고,
`WebhookAuthenticator` 가 fail-closed 라 전부 거부했다.

**2026-09-08 에 메일이 전부 no-op 이던 것과 똑같은 원인이다** — `.env.prod` 에 값을 넣는 것과
그 값이 컨테이너에 실리는 것은 별개인데, compose 의 `environment:` 목록을 같이 고치는 걸
잊었다. 그때는 메일이 안 나가는 정도였지만 이번엔 돈이 샜다.
`docs/progress/phase-12-iap.md` 의 배포 절차도 "`.env.prod` 에 넣고 재기동" 이라고만 적혀 있어
그대로 따라 해도 안 되는 상태였다.

**해결** — 세 가지를 함께 했다.

1. `docker-compose.prod.yml` 에 `PLAN_BILLING_GATEWAY`·`_WEBHOOK_SECRET`·`_GOOGLE_PACKAGE`·
   `_GOOGLE_CREDENTIALS_PATH`·`_PUBSUB_AUDIENCE`·`_PUBSUB_SA` 를 넘기게 추가하고,
   Play 서비스 계정 키를 `${PLAY_SA_HOST_PATH}` → `/run/secrets/play-developer-sa.json` 로
   읽기전용 마운트한다(FCM 키와 같은 방식).
2. `.env.prod.example` 에 이 변수들을 설명과 함께 넣었다. 운영은 `PLAN_BILLING_GATEWAY=google`.
3. **최후 방어선** — `NoOpStoreBillingGateway` 가 `prod` 프로파일에서는 어떤 토큰도 통과시키지
   않는다(`fetch` 가 항상 빈 값 → 402 `PURCHASE_NOT_VERIFIED`). 설정이 또 빠져도 공짜
   PREMIUM 은 안 나간다. 기동 로그에 에러도 남긴다. 앱 기동 자체를 막지는 않는다 — 결제와
   무관한 기능까지 멈추면 손해가 더 크고, 쿠폰 PREMIUM 은 이 게이트웨이를 안 탄다.

**확인법** — 배포 후 앱 로그에

```
Google Play 결제 검증 활성화 package=com.yeka.bandule
```

가 떠야 한다. `[no-op billing]` 이나 `운영인데 결제 게이트웨이가 noop 이다` 가 보이면 아직
설정이 안 실린 것이다. 컨테이너에 실제로 들어갔는지는

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod exec app env | grep PLAN_BILLING
```

로 확인한다(`.env.prod` 를 보는 게 아니라 **컨테이너 안**을 봐야 한다 — 이번 사고의 핵심).
가짜 토큰으로 `/plan/google/verify` 를 때렸을 때 402 가 나오면 정상.
단위 테스트는 `NoOpStoreBillingGatewayTest.prod_profile_refuses_every_token`.

---

## 2026-09-09 — `flutter build appbundle` 이 Kotlin "different roots" 로 죽는다

**증상** — 릴리스 AAB 빌드 시 여러 플러그인(`video_compress`, `image_picker_android`,
`kakao_flutter_sdk_common`, `kakao_map_sdk`, `shared_preferences_android` …)의
`compileReleaseKotlin` 이 줄줄이 실패한다:
```
java.lang.IllegalArgumentException: this and base files have different roots:
  C:\Users\USER\AppData\Local\Pub\Cache\hosted\pub.dev\<plugin>\...\X.kt  and  E:\project\band\client\android
```

**원인** — **프로젝트는 `E:` 에, pub 캐시는 `C:\Users\USER\AppData\Local\Pub\Cache` 에**
있다. Kotlin 증분 컴파일러의 `RelocatableFileToPathConverter` 가 소스 파일 경로를 프로젝트
기준 **상대경로**로 저장하려 하는데, `C:` 와 `E:` 사이에는 상대경로가 존재하지 않아
`File.relativeTo` 가 예외를 던진다. `android/gradle.properties` 에 `kotlin.incremental=false`
를 넣어도 **새 Kotlin Build Tools API(BTAPI) 경로는 이 플래그를 무시**하고 증분 캐시
디렉터리를 만들다 같은 지점에서 터진다.

**해결** — pub 캐시를 프로젝트와 **같은 드라이브**로 옮긴다.
```bash
setx PUB_CACHE "E:\pub-cache"          # 영구(새 셸부터)
export PUB_CACHE=/e/pub-cache          # 현재 셸에도
cd /e/project/band/client && flutter clean && (cd android && ./gradlew --stop)
flutter pub get                        # 패키지를 E:\pub-cache 로 새로 받음
flutter build appbundle --release --flavor prod \
  --dart-define-from-file=dart_defines.json --dart-define=API_BASE_URL=https://api.bandule.com
```
`kotlin.incremental=false` 는 그대로 둔다(무해, 다른 상황 대비).

**확인법** — `flutter build appbundle` 이 `app-prod-release.aab` 를 만들면 끝.
`echo $PUB_CACHE` 가 `E:` 경로를 가리키고, `ls "$PUB_CACHE/hosted/pub.dev"` 에 패키지가
들어와 있어야 한다. 빌드 로그에 더 이상 `different roots` 가 없다.

---

## 2026-09-08 — 신고·인증 메일이 한 통도 안 나갔다 (`.env.prod` 는 채웠는데)

**증상** — 신고 접수를 메일로도 보내게 만들고(`f57d304`) 서버 `.env.prod` 에
`MAIL_SMTP_USERNAME`·`MAIL_SMTP_PASSWORD`·`MAIL_FROM` 세 줄을 채운 뒤에도 메일이
오지 않았다. 신고뿐 아니라 비밀번호 재설정·이메일 인증 메일도 그동안 안 나갔다.

**원인** — `docker-compose.prod.yml` 의 `app` 서비스 `environment:` 블록에 `MAIL_*`·
`REPORT_NOTIFY_*` 항목이 **아예 없었다.** compose 는 `.env.prod` 의 값을 파일 안에서
`${VAR}` 로 **참조된 자리에만** 넣는다 — 참조가 없으면 `.env.prod` 에 무슨 값을 적어도
컨테이너 환경에는 안 들어간다. 앱은 `MAIL_FROM` 이 비었다고 보고
`EmailSender.isConfigured()` 가 false 라 발송을 통째로 건너뛴다.
컨테이너 안에서 `printenv | grep '^MAIL_'` 이 0줄이면 이 경우다(`.env.prod` 는 3줄인데).

**해결** — 그 5개(`MAIL_SMTP_USERNAME`, `MAIL_SMTP_PASSWORD`, `MAIL_FROM`,
`REPORT_NOTIFY_USER_IDS`, `REPORT_NOTIFY_EMAILS`)를 `app` `environment:` 에 추가.
`main` 머지 → 자동 배포로 서버 반영된다.
**앱 비밀번호(`MAIL_SMTP_PASSWORD`)는 2026-09-07 대화 중 노출됐으니 폐기·재발급이 먼저다**
(NEXT.md §1-Y). 재발급 값을 `.env.prod`(서버·로컬 둘 다)에 넣고 배포해야 실제로 나간다.

**확인법**
```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 \
  "cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T app printenv | grep -c '^MAIL_'"
# 3 이 나와야 한다. 그다음 다른 계정 글을 신고해 보고 로그:
#   docker compose ... logs --tail 200 app | grep -i mail
#   [email] 발송 실패  → SMTP 인증·MAIL_FROM 꺾쇠 문제
#   (아무것도 없음)   → 신고 자체가 접수 안 됨
```

---

## 2026-09-08 — 공개 저장소에 서버 비밀값을 커밋했다 🔴

**증상** — 서버 `.env.prod` 사본(`env.prod.server`)이 커밋에 딸려 들어가 **공개 저장소에
푸시됐다.** DB·Redis 비밀번호, JWT 서명키, R2 액세스 키, 카카오 어드민 키가 담긴 파일이다.

**원인** — 세 가지가 겹쳤다.

1. 서버와 로컬 설정을 비교하려고 `env.prod.server` 를 **새로 만들었다.** 이름이 새것이라
   `.gitignore` 에 없었다.
2. 커밋할 때 `git add -A` 를 썼다. **목록에 없는 파일을 전부 담는 명령**이다.
3. 담긴 것을 눈으로 확인하지 않고 바로 커밋·푸시했다.

`.gitignore` 만 믿은 것이 핵심 실수다. 방금 만든 파일은 그 목록에 있을 수가 없다.

**해결**

- 커밋에서 제거하고 `--force-with-lease` 로 강제 푸시 → 현재 `main` 에는 없다.
  **다만 GitHub 은 지워진 객체를 한동안 보관하므로 값 자체는 노출된 것으로 본다.**
  교체 절차는 `docs/progress/NEXT.md` §1-A 에 순서대로 적었다.
- `.gitignore` 에 `env.prod.server`, `.env.prod.*` 추가.
- **`.githooks/pre-commit` 신설** — `.env*`·`*.jks`·`*.pem`·`key.properties`·
  `google-services.json`·`dart_defines.json`·`secrets/`·개인키 블록이 담기면 커밋을 멈춘다.
  `*.example` 은 통과시킨다. 새 PC 에서 한 번 켠다: `git config core.hooksPath .githooks`.
- `CLAUDE.md` 에 규칙 추가 — **`git add -A`/`git add .` 금지, 파일을 하나씩 적는다.
  커밋 전 `git status --short` 로 눈으로 확인한다.**

**확인법**

```bash
git config core.hooksPath          # .githooks 가 나와야 한다
git add -f .env.prod && git commit -m x   # 막혀야 한다
git restore --staged .env.prod
```

> **교훈 두 가지.**
> 1. **문서만으로는 못 막는다.** 같은 규칙을 사람이 매번 지킬 것으로 기대하지 말고
>    기계가 거절하게 만든다. 훅이 없었으면 이 규칙도 다음에 또 잊혔을 것이다.
> 2. **비밀 파일은 저장소 밖에 만든다.** 비교용 사본이 필요하면 작업 폴더가 아니라
>    임시 폴더에 내려받는다. 저장소 안에 두는 순간 실수 한 번이면 끝이다.

---

## 2026-09-08 — 신고 접수 메일이 안 온다 ⚠️ **아직 안 고침**

**증상** — 신고 접수를 푸시 + 메일 두 경로로 보내게 만들고 서버까지 배포했는데(`f57d304`),
실제로 신고해 보니 메일이 오지 않는다.

**여기까지 밝혀진 것**

- 서버 `/opt/bandapp/.env.prod` 에 **`MAIL_*` 세 줄이 아예 없었다.** 로컬 `.env.prod` 에는
  있어서 "설정은 정상" 이라고 판단했는데, **서버와 로컬은 별개 파일**이다.
  `MAIL_FROM` 이 비면 `EmailSender.isConfigured()` 가 false 라 발송을 통째로 건너뛴다
  → 신고 메일뿐 아니라 **비밀번호 재설정·이메일 인증 메일도 그동안 안 나갔다.**
- `REPORT_NOTIFY_EMAILS` 는 서버에 들어갔지만 같은 줄이 **두 번** 있다(`echo >>` 를 두 번 실행).
- 설정을 손본 뒤에도 안 온다고 해서 거기서 멈췄다.

**다음에 확인할 순서** — `docs/progress/NEXT.md` §1-Z 에 명령까지 적어 뒀다. 요지는

1. `.env.prod` 에 `MAIL_*` 이 3줄인지, 그리고 **앱 컨테이너의 `printenv` 에도 3줄인지**.
   파일에만 있고 컨테이너에 없으면 재시작을 안 한 것이다(환경변수는 시작할 때만 읽는다).
2. 앱 로그의 `[email]` 줄 — `발신 계정 미설정`(설정 미도달) / `발송 실패`(SMTP·주소 형식) /
   아무것도 없음(신고 자체가 접수 안 됨).
3. `reports` 테이블에 행이 들어왔는지.

**알아 둘 것**

- `MAIL_FROM` 은 `밴듈 <주소@gmail.com>` 처럼 **꺾쇠가 필요하다.** 없으면
  `AddressException: Local address contains control or whitespace` 로 실패한다(실제로 파싱해 확인).
- **자기 글·자기 사진은 신고할 수 없다.** 앱에서 메뉴 자체가 안 뜬다 —
  테스트하려면 다른 계정의 글을 신고해야 한다.
- 발송 실패는 `EmailSender` 가 삼키고 로그만 남긴다(부가 기능이 본 작업을 막지 않게).
  그래서 **화면에는 아무 티가 안 난다** — 로그를 봐야 안다.

> **교훈** — 로컬 설정 파일을 보고 서버 상태를 단정했다. 이 저장소는 `.env.prod` 가
> git 에 없고 서버 것이 따로 산다. **서버 이야기를 할 때는 서버를 조회한다.**
> (`NEXT.md` 에 "덮어쓰지 말고 diff 하라"는 같은 뿌리의 사고가 이미 적혀 있었다.)

---

## 2026-09-07 (4차) — PREMIUM 게이팅 감사와 해지 동작

### 게이팅 감사 결과 — 누락은 정기 일정 하나뿐이었다

서버 전체에서 PREMIUM 을 강제하는 지점(`requirePremium` 호출)은 **정기 일정 등록 하나뿐**이었고,
3차에서 이미 클라이언트 게이팅을 붙였다. 다른 누락은 없다.

나머지 PREMIUM 차이는 기능 잠금이 아니라 **미디어 보관기한**(FREE 30일 / PREMIUM 무제한)
하나이고, 요금제 화면·홈 만료 배너·만료된 첨부 자리에는 안내가 있었다. **글 작성 화면에만
없어서** 무료 밴드 사용자가 30일 뒤 사라진다는 걸 모르고 올렸다 — 사진 손실은 되돌릴 수
없으므로 첨부 안내에 한 줄을 넣었다.

### 해지하면 결제한 기간이 증발했다

**증상** — PREMIUM 해지 버튼을 누르면 그 자리에서 FREE 가 되고 `expires_at` 이 null 로 지워졌다.
1년치를 결제하고 하루 뒤 해지하면 364일이 사라진다.

**원인** — `PlanService.cancel` 이 곧바로 `applyDowngrade` 를 불렀다. 즉시 강등이 "해지" 라고
본 것인데, 스토어 인앱결제(BUILD_PLAN 이 계획한 결제 수단)에서 구독 취소는 **자동 갱신
중지**일 뿐 결제한 기간 끝까지 혜택이 유지된다. 앱 안에서는 FREE 인데 스토어에서는 아직
구독 중인 상태가 됐을 것이다.

**해결** — 해지를 "기간 만료 시 강등 예약" 으로 바꿨다.

- `BandPlan.cancelAtPeriodEnd(now)` — **티어와 `expires_at` 은 건드리지 않고** `subscription_ref`
  만 비운다. 만료일 밤에 `PlanExpirationJob` 이 강등하고 미디어 30일 유예도 그때 시작된다.
- **컬럼을 늘리지 않았다.** "해지했는지" 는 `subscription_ref == null` 로 구분한다
  (`BandPlan.isCanceled`). 구독·쿠폰 두 경로 모두 ref 를 채우므로 PREMIUM 이면 항상 non-null 이고,
  해지하면 게이트웨이의 구독이 실제로 사라지니 비우는 것이 의미상으로도 맞다.
- 응답에 `canceled` 를 실어 화면이 "해지 예약됨 — 날짜까지 이용" 을 보여준다.
  중복 해지는 409 `PLAN_ALREADY_CANCELED`.

> **남은 빚** — 해지 뒤에는 `subscription_ref` 가 없어 게이트웨이 갱신을 부를 수 없다.
> 지금은 화면에서 연장 버튼을 감춰 우회한다. 실제 PG 를 붙이면 이 자리는 "다시 구독"(새 구독
> 생성)이어야 하고, 해지 뒤에도 식별자가 필요해지면 그때 `canceled_at` 컬럼을 따로 둔다.

### FREE 는 영상 업로드 금지

**왜** — 사진은 클라이언트가 긴 변 2048px 로 줄여 400~700KB 로 올라오는데 영상은 상한이
200MB 다. **300배 차이**라 무료 밴드의 저장 비용은 사실상 전부 영상에서 나온다.

`MediaAttachmentService.issueUploadUrl` 에서 `MediaType.VIDEO` 면 `requirePremium`.
크기 검사가 앞이라 200MB 초과 영상은 FREE 여도 400(403 아님)이 온다 — 테스트로 못박았다.
클라이언트는 첨부 선택 시트에서 영상 항목을 잠그고 요금제 화면으로 보낸다(압축까지 시킨 뒤
거절하는 것이 최악이라 고르는 자리에서 막는다).

**총 용량 상한은 넣지 않았다.** FREE 는 이미 보관기한 30일이라 쌓을 수 있는 총량이 그 안에
묶여 있고, 밴드별 누적 용량을 세려면 업로드·삭제·만료 배치 **세 곳**에서 정합성을 맞춰야 해서
값에 비해 비싸다. 필요해지면 실제 사용량을 보고 그때 넣는다.

### 옛 동작을 전제로 쓰인 테스트 넷이 깨졌다

**증상** — 해지 동작을 바꾸고 나니 통합 테스트 359개 중 2개가 실패했고(로컬 Docker 로 확인),
그 전에 영상 게이트를 넣었을 때도 2개가 깨질 상황이었다.

**원인** — 테스트가 "해지 = 즉시 FREE", "FREE 도 영상 업로드 가능" 을 **전제로** 쓰여 있었다.
버그가 아니라 계약이 바뀐 것이므로 테스트를 새 계약에 맞춰야 한다.

**해결**

| 테스트 | 어떻게 |
|---|---|
| `PlanSubscriptionIntegrationTest` 해지 관련 | 해지 직후 `tier=PREMIUM`·`canceled=true`·미디어 만료일 없음을 보게 바꾸고, 중복 해지 409 테스트를 새로 넣었다 |
| `PlanCrossBandIsolationIntegrationTest` | 유예 재계산이 만료 배치의 몫이 되었으므로, 밴드A 의 구독기간만 과거로 옮겨 배치를 돌린다. **격리를 본다는 취지는 그대로** |
| `RecurringPlanGateIntegrationTest` | 해지 직후에는 아직 규칙을 만들 수 있고(201) 만료 뒤에야 403 이 되도록. "해지 직후에도 만들 수 있다" 단언을 새로 넣어 바뀐 계약을 못박았다 |
| `MediaUploadIntegrationTest` 영상 둘 | 형식이 요점이 아닌 쪽(PENDING→재시도)은 이미지로 바꾸고, 영상 200MB 상한 테스트는 밴드를 구독시켰다. FREE 영상 403 / FREE 사진 201 테스트를 새로 넣었다 |

`PlanExpirationIntegrationTest` 의 "수동 해지와 같은 동작" 주석도 사실이 아니게 되어 고쳤다.

> **교훈** — 동작을 바꿀 때 깨지는 테스트는 "고쳐서 통과시킬 대상" 이 아니라 **바뀐 계약을
> 다시 적을 자리**다. 단언을 지우지 말고 새 동작을 단언하게 바꾼다. 여기서는 지우는 대신
> "해지 직후에도 된다" 를 추가해 오히려 커버리지가 늘었다.
>
> 그리고 **통합 테스트는 Docker 가 떠 있어야 로컬에서 돈다.** 컴파일만 통과시키고 넘기면
> 이런 것을 CI 에서야 알게 된다.

### 곁다리 — 이 파일을 고칠 때 쓰는 스크립트 주의

문자열 치환 스크립트를 heredoc 으로 넘길 때 `\\n` 이스케이프가 도구를 거치며 실제 개행으로
바뀌어 매칭이 조용히 실패한다(세 번 겪었다). Dart/Java 소스의 리터럴 `\\n` 을 매칭할 때는
`chr(92) + "n"` 으로 백슬래시를 직접 만든다.

---

## 2026-09-07 (3차) — 테스터 점검에서 나온 것 셋

### (1) 남이 바꾼 데이터가 pull-to-refresh 전까지 안 보임

**증상** — 합주 일정 같은 것이 갱신되지 않는다. 앱을 껐다 켜면 되는지도 확실치 않다.

**원인** — 프로바이더가 `autoDispose` 가 아니라(코드베이스 전체에 사용 0건) 캐시가 프로세스
수명 내내 남는다. 내가 바꾼 것은 각 화면이 `ref.invalidate` 로 처리하지만(23개 화면),
**남이 바꾼 것**은 알 방법이 없었다. 앱 전체에 라이프사이클 훅이 하나뿐이었고
(`PushService`) 그것도 알림 목록만 갱신했다.

"앱 껐다 켜면 되나" 가 불확실했던 이유 — 안드로이드가 프로세스를 살려 두면 갱신이 안 되고,
메모리가 부족해 죽였다 다시 뜨면 갱신된다. **사용자가 통제할 수 없는 조건**이라 규칙을
배울 수가 없었다.

**해결** — `client/lib/app_refresh.dart` 신설. 포그라운드 복귀 시 마지막 갱신 후
**60초**(`_minRefreshGap`) 이상 지났으면 밴드 데이터를 무효화한다.

간격을 둔 이유: 매번 갱신하면 알림 눌러 들락날락하는 것만으로 복귀 1회당 요청 9개가
계속 나가고 화면이 로딩으로 깜빡인다. 요청량 자체는 문제가 아니지만(읽기 조회에는
레이트리밋이 없고, 사용자 100명 기준 초당 0.16회) 깜빡임이 사용자에게 보인다.

> 탭 5개가 `IndexedStack` 으로 전부 살아 있어서 **보이지 않는 탭의 프로바이더도 함께**
> 다시 불린다. 그래서 복귀 1회에 요청이 9개다. 탭에 새 서버 데이터를 붙이면
> `app_refresh.dart` 의 `_bandData` 에도 넣어야 한다.

### (2) 시스템 내비게이션 바에 화면 아래가 가림

**증상** — 알림 설정 화면의 `저장` 버튼이 제스처 바/3버튼 내비에 깔려 안 보인다.

**원인** — Scaffold 29개 중 SafeArea 를 쓰는 화면이 12개뿐이었다. 알림 설정은
`EdgeInsets.fromLTRB(20, 8, 20, 24)` 고정값이라 기기의 내비 바 높이를 모른다.
탭바(`TabShell`)는 `viewPadding` 을 직접 더해 처리하고 있었지만, **탭 밖으로 밀어 올린
화면들**은 각자 챙겨야 했고 절반이 빠져 있었다.

**해결** — 화면마다 챙기면 반드시 빠뜨리므로 **한 곳에서** 한다.
`app.dart` 의 `MaterialApp.router` builder 에 전역 `SafeArea(top: false)`.

- 자체 SafeArea 가 있는 12개 화면도 이중 적용되지 않는다 — SafeArea 는 자식의
  `MediaQuery.padding` 을 지우므로 안쪽 SafeArea 는 0 을 더한다.
- `top: false` 인 이유 — 위쪽은 AppBar 가 이미 처리한다.
- `TabShell` 의 수동 `viewPadding` 계산은 **지웠다.** `viewPadding` 은 SafeArea 가 지우지
  않으므로 남겨 두면 3버튼 내비 폰에서 탭바가 두 번 밀린다.

**확인법** — 제스처 내비와 3버튼 내비 **양쪽** 기기에서 알림 설정의 저장 버튼과 탭바 위치를
눈으로 본다. 코드로는 못 잡는다.

### (3) FREE 밴드인데 정기 일정이 열려 있음

**증상** — FREE 요금제인데 캘린더에서 정기 일정에 들어가 폼을 다 채울 수 있고,
**저장을 눌러야** 403 이 난다.

**원인** — 서버는 제대로 막고 있다(`RecurringRuleService.create` 의
`planDirectory.requirePremium`). 클라이언트에 게이팅이 **하나도 없었다** —
`client/lib/features/recurring/` 전체에 요금제를 보는 코드가 0건이었다.

**해결** — `recurring_list_screen.dart` 에서 `bandPlanProvider` 를 보고 FREE 면
추가 버튼(FAB)을 숨기고 목록 위에 "프리미엄 전용" 안내 + 요금제 화면 버튼을 얹는다.

- **목록 자체는 FREE 에서도 보여준다.** PREMIUM 을 쓰다 내려온 밴드의 기존 규칙은 계속
  회차를 만들기 때문에(서버가 배치를 멈추지 않는다), 감추면 그 일정이 어디서 생겼는지
  알 길이 없다.
- 요금제를 **아직 못 불러왔거나 조회에 실패했으면 잠그지 않는다.** 서버가 최종 방어선이라
  최악이라도 예전과 같은 동작이고, 반대로 잠그면 통신이 잠깐 불안한 것만으로 PREMIUM
  사용자가 기능을 못 쓴다.

> **다른 PREMIUM 기능도 같은 상태일 수 있다.** 서버가 막는 기능을 클라이언트가 안내 없이
> 열어 두면 전부 같은 증상이 난다. 아직 전수 점검하지 않았다.

---

## 2026-09-07 (2차) — 재설치 후 홈 화면만 회색 사각형

**증상** — 0.1.0+20 을 재설치로 깔았더니 홈 탭만 아무것도 없는 연회색 사각형이고,
캘린더·지도·게시판·정산은 정상. 앱이 죽지는 않는다.

**원인** — 바로 위 항목 (1) 과 **같은 뿌리인데 다른 장소**다. 두 가지가 겹쳤다.

1. 백업 복원으로 안전 저장소의 옛 항목이 복호화 불가 상태다. 알림 배지가 쓰는
   `notifications.lastSeenAt.{bandId}` 를 읽을 때마다
   `PlatformException(... BadPaddingException: BAD_DECRYPT)` 가 난다.
   앞 항목에서 `bootstrap()` 만 막았고 나머지 저장소 두 곳은 그대로였다.
   (로그인 토큰은 재로그인하며 **새 열쇠로** 다시 써서 멀쩡했다 — 그래서 로그인은 됐다.)
2. 홈 헤더가 그 값을 `ref.watch(...).value` 로 읽었다. **`AsyncValue.value` 는 실패 상태일 때
   그 예외를 다시 던진다** (`valueOrNull` 은 null 을 준다). 배지 숫자 하나를 못 구했다고
   위젯 build 가 예외로 끝났고, 릴리스 빌드에서 build 예외는 `RenderErrorBox` — 글자 없는
   **연회색 사각형**으로 그려진다(디버그였다면 빨간 화면이었을 것이다).

logcat 에 남은 결정적 두 줄:

```
PlatformException(Exception encountered, read, javax.crypto.BadPaddingException: BAD_DECRYPT)
#2  _Header.build (package:bandapp_client/features/home/presentation/home_screen.dart:103)
```

**해결** — 뿌리 하나와 증상 하나를 같이 고쳤다.

- `lib/core/storage/secure_read.dart` 신설. 읽기가 실패하면 **그 항목을 지우고 null 을 돌려준다.**
  되살릴 방법이 없는 값이고, 안 지우면 앱을 다시 깔기 전까지 매번 같은 예외가 난다.
  안전 저장소를 쓰는 **세 곳 모두**(`TokenStorage`, `NotificationSeenStorage`,
  `SocialTermsStorage`) 이걸 통한다. 쓰기는 감싸지 않는다 — 새 값은 현재 열쇠로 암호화된다.
- `home_screen.dart:103` 의 `.value` → `.valueOrNull`. 곁다리 데이터 하나가 화면 전체를
  죽이지 않게. 코드베이스 전체를 훑어 `.value` 로 읽던 곳은 여기 하나뿐이었다.
- 회귀 테스트 `client/test/secure_read_test.dart`.

**확인법**

```bash
cd client && flutter test
```

실기기에서는 재설치 → 로그인 → 홈 탭에 내용이 뜨면 된다. 로그를 볼 수 있으면
`secureRead: ... 를 읽지 못해 버린다` 가 **한 번만** 찍히고 그 뒤로 안 찍혀야 한다(지웠으니까).

> **교훈 두 가지.**
> 1. 저장소가 깨지는 상황을 한 군데(`bootstrap`)만 막은 것이 실수였다. 같은 저장소를 읽는
>    곳을 전부 찾아 공통 함수로 막았어야 했다.
> 2. **릴리스 빌드의 글자 없는 연회색 사각형 = 위젯 build 중 예외**다. 다음에 이 화면을 보면
>    바로 `adb logcat | grep -i flutter` 로 스택을 본다.

---

## 2026-09-07 — 푸시 알림이 아예 안 오고, 재설치하면 스플래시에서 멈춤

테스터 제보 "푸시 알림을 껐다 켜도 `device_tokens` 값이 안 바뀐다" 에서 시작해
서로 다른 문제 셋이 나왔다. 셋 다 고쳤다.

### (1) 앱 재설치 후 스플래시에서 영영 멈춤 ★ 가장 심각

**증상** — 앱을 지우고 다시 설치해서 실행하면 로딩바만 계속 돌고 로그인 화면으로
넘어가지 않는다. 테스터는 이 증상을 보고하지 않았고, 직접 해보다가 발견했다.

**원인** — 두 가지가 겹쳤다.

1. 안드로이드의 **Auto Backup 이 켜져 있었다.** `AndroidManifest.xml` 에 `android:allowBackup`
   을 안 적으면 기본값이 `true` 다. 로그인 토큰을 보관하는 `flutter_secure_storage` 는 값을
   `SharedPreferences` 파일에 넣는데, 그게 백업 대상이다. 그래서 Play 스토어로 재설치하면
   **예전 토큰이 되살아난다.**
2. 그런데 그 값을 푸는 **암호화 키는 Android KeyStore 에 있고, KeyStore 는 백업되지 않는다.**
   즉 "열쇠 없는 금고" 만 복원된다. 읽으려 하면 복호화 실패로 예외가 난다.

여기서 `bootstrap()` 이 저장소 읽기를 `try` 밖에서 하고 있어서 예외가 그대로 위로 튀었고,
로그인 상태가 `unknown` 에 멈췄다. 라우터는 `unknown` 일 때 아무 데도 보내지 않으므로
스플래시에 갇힌다.

**해결**

- `AndroidManifest.xml` 의 `<application>` 에 `android:allowBackup="false"` 추가.
  복원해도 못 여는 데이터라 백업할 이유가 없다.
- `bootstrap()` 전체를 `try` 로 감쌌다. 뒷정리용 `clear()` 도 던질 수 있어서 한 번 더 삼킨다.
  **무슨 일이 있어도 로그아웃 상태로는 반드시 떨어진다** (`auth_controller.dart`).
- 회귀 테스트 `client/test/auth_bootstrap_test.dart` — 읽기·삭제가 둘 다 실패하는 저장소를
  물려도 `unauthenticated` 로 끝나는지 본다.

**확인법**

```bash
cd client && flutter test test/auth_bootstrap_test.dart
```

실기기에서는 앱 삭제 → 재설치 → 실행 시 로그인 화면까지 넘어가면 된다.

> ⚠️ **이미 백업이 올라간 기기는 이 빌드를 깔아도 한 번은 복원된 쓰레기 데이터를 만난다.**
> 다만 이제 멈추지 않고 로그인 화면으로 떨어진다. 그 다음 설치부터는 백업 자체가 안 생긴다.

### (2) 알림 권한을 거부하면 그 뒤로 영영 토큰이 등록되지 않음

**증상** — `device_tokens` 테이블에 행이 안 생기고, 그래서 푸시가 한 번도 안 온다.
OS 설정에서 알림을 다시 켜도 그대로다.

**원인** — `PushService.start()` 가 **실패할 수 있는 작업들보다 먼저** `_started = true` 를
세웠다. 알림 권한 거부·Firebase 초기화 실패·등록 API 오류 중 하나만 나도 그 자리에서
빠져나오는데, 플래그는 이미 서 있어서 **다시 호출해도 가드에 막힌다.** 앱을 완전히
재시작하거나 재로그인하기 전까지 복구되지 않는다.

안드로이드는 알림 권한을 한 번 거부하면 시스템 팝업을 다시 띄우지 않으므로,
"거부 → 설정에서 켬 → 앱 복귀" 라는 가장 흔한 경로가 통째로 막혀 있었다.

**해결** — `push_service.dart` 의 플래그를 목적별로 쪼갰다.

| | 뜻 |
|---|---|
| `_registered` | 서버 등록에 **성공**했을 때만 `true`. `_register()` 가 성공 여부를 반환하도록 바꿨다 |
| `_listening` | FCM 스트림 중복 구독 방지 (재시도해도 한 번만 구독) |
| `_busy` | 재진입 방지 |

`AppLifecycleListener` 를 초기화 성공 여부와 무관하게 먼저 걸고, 앱이 포그라운드로
돌아올 때마다 **아직 등록 못 했으면 다시 시도**한다. 덤으로 로그아웃 뒤 토큰 갱신이
남의 계정에 붙지 않도록 `onTokenRefresh` 에 가드를 넣었다.

**확인법** — 알림 권한을 거부하고 로그인 → 휴대폰 설정에서 알림 켜기 → 앱으로 복귀.
그 뒤 DB 에 행이 생기면 된다.

```bash
psql -c "SELECT user_id, platform, updated_at FROM device_tokens ORDER BY updated_at DESC LIMIT 10;"
```

### (3) "알림을 받으시겠습니까" 팝업이 설치 직후에 안 뜬다 — 이건 정상

**증상** — 재설치 후 실행했는데 권한 팝업이 안 뜬다고 제보가 왔다.

**원인** — 버그가 아니다. 권한 요청은 **로그인 성공 후**에만 일어난다
(`app.dart` 가 로그인 상태 변화를 보고 `PushService.start()` 를 부른다).
설치 직후에는 로그인 화면이므로 팝업이 뜰 수 없다.

Android 12 이하라면 애초에 런타임 알림 권한이 없어서 팝업이 안 뜨는 게 정상이고,
알림은 그대로 온다. (`POST_NOTIFICATIONS` 는 Android 13 / API 33 부터다.)

**해결** — 코드는 그대로 뒀다. 로그인 직후에 물어보는 편이 맥락이 있어 승낙률이 높다.
**테스터에게 "로그인을 마쳐야 팝업이 뜬다" 고 안내하는 것으로 대신한다.**

### (4) 곁다리 — 앱 내 푸시 토글은 `device_tokens` 를 건드리지 않는다

혼동이 잦아서 남긴다. 두 개는 다른 것이다.

| | 역할 | 없거나 off 면 |
|---|---|---|
| `device_tokens` 행 | 푸시를 보낼 **주소** | 주소가 없어 발송 자체가 안 됨 |
| `notification_settings.push_enabled` | 앱 안의 **on/off 스위치** | 주소는 있지만 수신자 필터에서 걸러짐 |

설정 화면의 토글은 아래쪽만 바꾼다. 토큰은 로그인·로그아웃·FCM 자동 갱신 때만 움직인다.
로그인할 때마다 토큰을 지웠다 넣었다 하지 않으려는 의도된 설계다.

### (5) 곁다리 — 테스터 배포 스크립트가 앱 ID 를 못 찾아 멈춤

**증상** — `release_tester.py` 가 업로드 직전 "google-services.json 이 없다" 로 죽는다.

**원인** — Firebase 설정을 개발용·운영용으로 나누면서 파일이
`android/app/google-services.json` 에서 `android/app/src/{dev,prod}/` 로 옮겨졌는데,
스크립트가 옛 경로를 그대로 보고 있었다.

**해결** — 배포용 앱 ID 를 스크립트 상수로 못박고 `--app` 으로 덮어쓸 수 있게 했다.
**App Distribution 프로젝트와 앱 안의 FCM 프로젝트는 서로 다르고, 그래도 된다:**

| | 프로젝트 | 왜 |
|---|---|---|
| 테스터 배포 | `bandapp-dev-67c6f` | 그룹 `밴듈테스트`와 지난 릴리스 이력이 전부 여기 쌓여 있다. 옮기면 테스터가 초대를 다시 받아야 한다 |
| 앱의 FCM (푸시) | `bandule-b94d2` | prod flavor 의 `google-services.json`. 서버 `.env.prod` 의 `FCM_PROJECT_ID` 와 같아야 하고, 실제로 같다 |

패키지명이 같으므로 prod flavor APK 를 dev 프로젝트 앱 ID 로 올리는 데 문제가 없다.
배포 채널과 푸시 발송 경로는 서로 무관하다.
