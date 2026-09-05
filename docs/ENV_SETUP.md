# 환경변수 · 외부 서비스 발급 가이드

`.env` (로컬 docker compose) 와 배포 환경에 넣는 값들이 각각 무엇이고, 어디서 어떻게
발급하는지 정리한다. 실제 값의 뼈대는 `.env.example` 에 있다 — 여기서는 "왜 필요한지 /
없으면 뭐가 죽는지 / 발급 절차" 를 설명한다.

> 이 앱의 외부 연동은 **전부 선택**이다(JWT·DB·Redis 제외). 연동 값을 비워도 앱은 뜨고,
> 해당 기능만 `503` 또는 no-op 이 된다. 나중에 값을 채우고 재기동하면 그때부터 동작한다.

---

## 0. 한눈에

| 그룹 | 필수? | 비우면 |
|---|---|---|
| `DB_*` (PostgreSQL) | **필수** | 부팅 실패 |
| `REDIS_*` | **필수** | 부팅 실패 (rate limit·토큰·캐시) |
| `JWT_SECRET` | **필수** | 부팅 실패 (기본값 없음 — 의도) |
| `KAKAO_*` | 선택 | 카카오 로그인만 `503`, 이메일 로그인 등 정상 |
| `NAVER_MAP_*` | 선택 | 합주실 등록 시 좌표(lat/lng)만 `null`, 등록은 정상 |
| `R2_*` (Cloudflare R2) | 선택 | 게시판 첨부 업로드/조회만 `503`, 게시글·신고·차단 정상 |
| `FCM_*` (Firebase) | 선택 | 푸시 발송만 no-op, 토큰 등록·알림 설정 API 정상 |
| 배포 전용 (`CORS_*`, `DEEPLINK_*`, `IOS_APP_ID` …) | 배포 시 | 로컬 기본값이라 상용에서 반드시 교체 |

**최소 구성**: `DB_*` + `REDIS_*` + `JWT_SECRET` 만 있으면 로그인·밴드·일정·정산 등 핵심 기능은 전부 동작.

**테스트/상용 분리 원칙**: 외부 서비스는 앱/프로젝트/버킷/키를 **환경별로 따로** 만든다.
계정은 하나여도 되지만, 테스트 트래픽이 상용 데이터·사용자에 닿지 않도록 자원은 분리한다.

---

## 1. 필수 인프라

### DB_* — PostgreSQL 접속

| 변수 | 로컬(docker compose) | 상용 |
|---|---|---|
| `DB_HOST` `DB_PORT` | compose 가 `postgres:5432` 로 주입 | 관리형 DB 엔드포인트 |
| `DB_NAME` `DB_USERNAME` | 임의 (`bandapp`) | 프로비저닝한 값 |
| `DB_PASSWORD` | 로컬 전용 아무 값 | **`openssl rand -base64 24`** 등 강한 값, 시크릿 매니저 보관 |

- 로컬은 `docker compose up` 이 Postgres 16 컨테이너를 같이 띄운다. 추가 발급 없음.
- 상용은 RDS / Cloud SQL / Supabase 등 관리형 인스턴스를 만들고 접속정보를 넣는다.
- 스키마는 Flyway 가 기동 시 마이그레이션한다 (`ddl-auto: validate`).

### REDIS_* — Redis 접속

| 변수 | 로컬 | 상용 |
|---|---|---|
| `REDIS_HOST` `REDIS_PORT` | compose 가 `redis:6379` 로 주입 | 관리형 Redis 엔드포인트 |

- rate limit, 리프레시 토큰, 캐시에 쓴다. 로컬은 compose 가 Redis 7 을 같이 띄운다.
- 상용은 ElastiCache / Upstash / Memorystore 등.

### JWT_SECRET — 토큰 서명 키

- 액세스/리프레시 JWT 서명에 쓰는 대칭 키. **32자 이상**, 비면 부팅 실패(알려진 키로 서명하는 사고 방지 — 기본값을 일부러 안 둠).
- **직접 생성**:
  ```bash
  openssl rand -base64 48
  ```
- **테스트용과 상용용을 반드시 다른 값으로.** 유출 시 전 사용자 토큰 위조가 가능하므로 시크릿 매니저에 보관하고, 노출되면 즉시 교체(교체 시 기존 토큰 전부 무효화됨).

---

## 2. 카카오 로그인 — `KAKAO_APP_ID`, `KAKAO_ADMIN_KEY`

**없으면**: 카카오 로그인 엔드포인트만 `503`. 이메일 로그인 등 나머지는 정상.

**동작**: 클라이언트가 받은 카카오 액세스 토큰을 백엔드에 넘기면, 백엔드가
`GET /v1/user/access_token_info` 로 검증하고 토큰의 `app_id` 가 `KAKAO_APP_ID` 와
일치하는지 대조한다(다른 앱 토큰 도용 차단). `KAKAO_ADMIN_KEY` 는 연결 끊기 등 서버 호출용.

**발급 절차**:
1. <https://developers.kakao.com> → 내 애플리케이션 → **애플리케이션 추가**
   (테스트용·상용용 앱을 **따로** 만든다)
2. **앱 설정 > 요약 정보** 의 숫자 **앱 ID** → `KAKAO_APP_ID`
3. **앱 설정 > 앱 키 > 어드민 키** → `KAKAO_ADMIN_KEY`
   → 카카오 API 무제한 호출이 가능한 마스터 키. **클라이언트/저장소에 절대 노출 금지**
4. **제품 설정 > 카카오 로그인** ON, **Redirect URI** 등록
5. **제품 설정 > 카카오 로그인 > 동의항목** 에서 필요한 항목(닉네임 등) 설정

**테스트 vs 상용**:
- 비즈앱 전환 전에는 **등록한 팀 멤버만** 로그인 가능
- 이메일 등 민감 동의항목은 **비즈앱 심사**가 필요
- 이미 노출된 적 있는 키는 콘솔에서 **재발급**

---

## 3. 네이버 지도 지오코딩 — `NAVER_MAP_CLIENT_ID`, `NAVER_MAP_CLIENT_SECRET`

**없으면**: 합주실 등록 시 주소만 저장되고 좌표(lat/lng)는 `null`. 등록 자체는 정상.
나중에 값을 채우고 재기동하면 그때부터 좌표가 붙는다.

**동작**: 백엔드가 `GET /map-geocode/v2/geocode?query=주소` 를 서버-투-서버로 호출
(헤더 `x-ncp-apigw-api-key-id` / `x-ncp-apigw-api-key`). 이 앱이 쓰는 유일한 네이버 API.

**발급 절차**:
1. <https://console.ncloud.com> 가입 + **결제수단 등록** (Geocoding 은 일 3천 건까지 무료)
2. **Services > Maps** → **Application 등록**
3. 이용 API 는 **Geocoding 만** 체크
   (Reverse Geocoding / Directions / Dynamic Map / Static Map / Map Style Editor 는 이 앱 백엔드에서 안 씀)
4. **서비스 환경 등록** — 폼의 3칸 중 **Web 서비스 URL 만** 채운다:
   - 백엔드 공개 도메인 (없으면 지금은 `http://localhost:8080` 만 등록해도 실호출까지 정상 — Geocoding 은 헤더 인증이라 referer 를 엄격히 막지 않음)
   - **앱 패키지 이름 / iOS Bundle ID** 칸은 비운다 → 이건 앱에 박히는 클라이언트용 지도 SDK 키 검증값이라 백엔드와 무관. 나중에 앱 화면에 지도를 그릴 때 추가
5. 발급된 **Client ID / Client Secret** → 두 변수에 매핑

**참고**: `app.naver.api-base-url` 기본값은 `https://maps.apigw.ntruss.com`. NCP 콘솔이 다른
주소를 안내하면 `NAVER_API_BASE_URL` 로 교체 가능(코드 변경 불필요).

---

## 4. Cloudflare R2 (게시판 미디어) — `R2_ACCOUNT_ID`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`

**없으면**: 첨부 업로드/조회 API 만 `503`(`MEDIA_STORAGE_NOT_CONFIGURED`).
게시글·신고·차단은 정상. **넷 다 채워야** 활성화되고, 하나라도 비면 비활성.

**동작**: 백엔드는 파일 바이트를 받지 않는다. presigned URL(S3 SDK 오프라인 서명)만 발급하고,
클라이언트가 R2 에 직접 `PUT`(업로드) / `GET`(다운로드)한다. 버킷은 **비공개**이고
공개 도메인·CDN 설정이 필요 없다. 백엔드는 업로드 검증(`headObject`)과 정리(`deleteObject`)에만
R2 에 직접 접근한다. presigned URL 유효기간은 코드에서 5~15분으로 clamp.

```
클라이언트 → 백엔드   : "이 파일 올릴 URL 줘"
백엔드   → 클라이언트 : presigned PUT URL (15분)
클라이언트 → R2       : 그 URL 로 직접 PUT
백엔드   → R2         : headObject 로 검증 / 만료 시 deleteObject
조회 시              : 백엔드가 presigned GET URL 발급 → 클라이언트가 직접 다운로드
```

**발급 절차**:
1. <https://dash.cloudflare.com> → 왼쪽 **R2** → 첫 진입 시 **결제수단 등록**
   (무료 티어: 저장 10GB, 쓰기 100만/월, 읽기 1000만/월, **전송량 무료**)
2. **Create bucket** → 이름 입력 → 그게 `R2_BUCKET`
   - 테스트: `bandapp-media-dev`, 상용: `bandapp-media-prod` (버킷 분리, 계정 공용)
   - **Public access 는 끈 채로 둔다**
3. **Account ID** → `R2_ACCOUNT_ID`
   - 버킷 상세의 **S3 API** 주소 `https://<32자리 16진수>.r2.cloudflarestorage.com/<버킷>` 에서 `.r2.cloudflarestorage.com` **앞부분**
   - 또는 대시보드 우측 사이드바 "Account ID", 또는 브라우저 주소창 `dash.cloudflare.com/<여기>/r2/...`
4. **Manage R2 API Tokens** → **Create API Token**
   - Permissions: **Object Read & Write**
   - Specify bucket(s): 위에서 만든 버킷만 (권한 최소화)
   - TTL: **Forever** (또는 길게 + 주기적 회전)
   - 생성 결과에서 **Access Key ID** → `R2_ACCESS_KEY_ID`, **Secret Access Key** → `R2_SECRET_ACCESS_KEY`
     (Secret 은 이 화면 벗어나면 다시 못 봄 — 지금 저장)
   - 같이 보이는 "Token value", "jurisdiction-specific endpoint" 는 안 씀
5. `R2_ENDPOINT` 는 **비워둔다** — 코드가 `https://{account-id}.r2.cloudflarestorage.com` 로 자동 조립

**버킷 CORS 정책** — 브라우저/Flutter Web 에서 올릴 때만 필요(네이티브 앱은 CORS 개념 없음).
필요하면 **버킷 > Settings > CORS Policy**:
```json
[
  {
    "AllowedOrigins": ["http://localhost:8080", "https://앱도메인"],
    "AllowedMethods": ["PUT", "GET"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

---

## 5. Firebase Cloud Messaging (푸시 알림)

**없으면**: 푸시 발송만 no-op. 디바이스 토큰 등록·알림 설정 API 는 정상.
**활성 조건**: `FCM_PROJECT_ID` + (`FCM_CREDENTIALS_PATH` 또는 `FCM_CREDENTIALS_JSON`).

**중요**: 백엔드 자격증명만으로는 푸시가 안 온다. **앱 자체도 FCM 클라이언트로 등록**돼야 한다(§5.3).

```
① 앱이 FCM 에 등록 → 기기 토큰 발급 → 백엔드 /device-tokens API 로 전송
② 백엔드가 토큰 저장. 리마인더·독촉 이벤트 시 서비스 계정 키로 FCM 호출
③ FCM 이 배달 (Android: FCM 직접 / iOS: FCM→APNs→기기)
```
`.env` 값(§5.1~5.2)은 **②번**만 담당한다.

### 5.1 백엔드 — 프로젝트 & `FCM_PROJECT_ID`

1. <https://console.firebase.google.com> → **프로젝트 추가**
   - 이름: `bandapp-dev`, `bandapp-prod` 로 **분리** (테스트 푸시가 상용 사용자에 안 가게)
   - Google Analytics 는 건너뛰어도 됨
2. 톱니바퀴 → **프로젝트 설정 > 일반** → **프로젝트 ID** → `FCM_PROJECT_ID`
   - 표시 이름이 아니라 소문자 ID. 랜덤 접미사가 붙기도 함 (예: `bandapp-dev-67c6f`)

### 5.2 백엔드 — 서비스 계정 키 & 주입

1. **프로젝트 설정 > 서비스 계정** → **새 비공개 키 생성** → `*-firebase-adminsdk-*.json` 다운로드
   - JSON 안에 `private_key`, `client_email` 등이 들어있다.
   - **이 파일 하나면 누구나 네 앱 이름으로 푸시를 보낼 수 있다** → 커밋 금지(이미 `.gitignore` 에 `*firebase-adminsdk*.json`), 시크릿 매니저 보관
2. 주입 방식 — **파일 경로 권장**(환경변수는 프로세스 목록·크래시 덤프로 새기 쉬움):

   **로컬 docker compose** (현재 이 방식으로 설정됨):
   | 변수 | 값 | 설명 |
   |---|---|---|
   | `FCM_CREDENTIALS_HOST_PATH` | `./bandapp-...adminsdk-....json` | 내 PC 의 JSON 실제 경로(compose 파일 기준 상대) |
   | `FCM_CREDENTIALS_PATH` | `/run/secrets/fcm-credentials.json` | 컨테이너 안 경로. 보통 그대로 |

   `docker-compose.yml` 의 app 서비스가 아래로 HOST_PATH 파일을 컨테이너에 read-only 마운트한다:
   ```yaml
   volumes:
     - ${FCM_CREDENTIALS_HOST_PATH:-/dev/null}:/run/secrets/fcm-credentials.json:ro
   ```
   FCM 을 안 쓰면 `HOST_PATH` 를 비워 두면 되고, 그러면 `/dev/null` 이 마운트되고
   `FCM_CREDENTIALS_PATH` 도 비어 있으면 앱은 파일을 읽지 않는다.

   **파일을 못 두는 PaaS**: `FCM_CREDENTIALS_JSON` 에 JSON 문자열 전체를 한 줄로.
   `FCM_CREDENTIALS_PATH` 와 둘 다 있으면 **PATH 우선**.

   **비 docker 로컬 실행**(`./gradlew bootRun`, local 프로파일): `FCM_CREDENTIALS_PATH` 에
   호스트 파일 경로를 직접 주면 됨 (마운트 개념 없음).

### 5.3 클라이언트 (Flutter) — `.env` 아님, 앱 프로젝트에 들어감

`google-services.json` / `GoogleService-Info.plist` 는 앱에게 "어느 Firebase 프로젝트의 어느
발신자와 통신하는지" 알려주는 설정 파일이다. **비밀이 아니다**(앱 바이너리에 그대로 포함).
§5.2 의 서비스 계정 키(서버 전용 비밀)와 혼동 주의.

1. 같은 Firebase 프로젝트 → **프로젝트 설정 > 일반 > 내 앱 > 앱 추가 → Android**
   - Android 패키지 이름: `com.yeka.bandule` (`ANDROID_PACKAGE`, 클라이언트 `applicationId` 와 동일하게)
   - `google-services.json` 다운로드 → `android/app/` 에 배치
2. **앱 추가 → iOS**
   - iOS 번들 ID: `com.yeka.bandule`
   - `GoogleService-Info.plist` 다운로드 → Xcode 로 `ios/Runner/` 에 추가
3. **iOS 는 APNs 키 추가 필요** (안 하면 Android 는 되는데 iOS 만 조용히 실패):
   - Apple Developer → **APNs Authentication Key (.p8)** 생성
   - Firebase **프로젝트 설정 > Cloud Messaging** → "APNs 인증 키" 에 .p8 업로드
4. Flutter 패키지: `firebase_core`, `firebase_messaging`
   - FlutterFire CLI (`flutterfire configure`) 로 config 파일 다운로드 + `firebase_options.dart` 생성 자동화
5. 앱 코드 흐름: 알림 권한 요청 → `FirebaseMessaging.instance.getToken()` → 그 토큰을
   백엔드 디바이스 토큰 등록 API 로 전송(분당 30회 제한) → 이후 백엔드가 그 토큰으로 발송

---

## 6. 배포(상용) 전용 값

`.env.example` 에는 없지만 `application.yml` 에 있고, 로컬 기본값이라 상용에서 반드시 교체:

| 변수 | 이유 |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Swagger/API 문서 비공개, `/actuator/health` 상세 숨김 |
| `CORS_ALLOWED_ORIGINS` | 기본값 `http://localhost:*` → 실제 프론트/앱 도메인 |
| `DEEPLINK_BASE_URL` | 초대 링크·AASA/assetlinks 제공용 백엔드 공개 도메인 (HTTPS) |
| `IOS_APP_ID` | `TEAMID.com.yeka.bandule`. Apple Developer($99/년)의 Team ID + 번들 ID. iOS 유니버설 링크 검증 |
| `ANDROID_PACKAGE` / `ANDROID_SHA256_CERT_FINGERPRINTS` | 앱 서명 키 SHA-256 지문. Play Console > 앱 무결성 > 앱 서명 에서 복사. Android App Links 검증 |
| `IOS_APP_STORE_URL` / `ANDROID_PLAY_STORE_URL` | 미설치 사용자 스토어 유도 |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | 기본 30분 / 14일. 필요 시만 |

`*_CRON`, `RL_*`(rate limit), `RECURRING_*`, `NOTI_*`, `MEDIA_*` 등은 합리적 기본값이 있어 건드릴 필요 없음.

---

## 7. 테스트 → 상용 자원 분리 요약

| 항목 | 테스트 | 상용 |
|---|---|---|
| DB / Redis | 로컬 docker compose | 관리형 인스턴스 |
| `JWT_SECRET` | 로컬 전용 값 | 새로 생성, 시크릿 매니저 |
| 카카오 앱 | 테스트 앱 (팀 멤버만) | 상용 앱 (비즈앱 심사) |
| 네이버 Maps Application | localhost URL 등록 | 상용 도메인 등록 (같은 App 에 추가 가능) |
| R2 버킷 / API 토큰 | `bandapp-media-dev` + 전용 토큰 | `bandapp-media-prod` + 전용 토큰 |
| Firebase 프로젝트 | `bandapp-dev` | `bandapp-prod` |
| Firebase config 파일 | dev 프로젝트에서 받은 것 | prod 프로젝트에서 받은 것 |
| 배포 프로파일 | `docker` (로컬) | `prod` |

---

## 8. 현재 로컬(dev) 설정 상태

`.env` 에 이미 채워진 값 (테스트/개발용):

- `DB_*`, `REDIS_*` — docker compose 가 주입 (로컬 컨테이너)
- `JWT_SECRET` — 로컬 전용 placeholder
- `KAKAO_APP_ID` / `KAKAO_ADMIN_KEY` — 테스트 앱 값
- `NAVER_MAP_CLIENT_ID` / `NAVER_MAP_CLIENT_SECRET` — 발급 완료
- `R2_*` — `bandapp-media-dev` 버킷 + 전용 토큰
- `FCM_PROJECT_ID=bandapp-dev-67c6f`, `FCM_CREDENTIALS_HOST_PATH` → 프로젝트 루트의
  `bandapp-dev-67c6f-firebase-adminsdk-fbsvc-*.json` (gitignore 됨), 컨테이너에
  `/run/secrets/fcm-credentials.json` 로 마운트

배포 시에는 §6, §7 에 따라 상용 자원으로 새로 발급해 교체한다.
