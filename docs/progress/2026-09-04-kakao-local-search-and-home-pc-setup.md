# 2026-09-04 — 합주실 주소 검색: 네이버 → 카카오 로컬 전환 + 집 PC 셋업 메모

> 집에서 이어받을 때 이 파일부터 본다. 코드 변경 요약 + 오늘 로컬에서 겪은 함정 정리.

---

## 1. 무엇을 바꿨나 (백엔드)

합주실 등록 폼의 "주소 검색해서 자동 입력" 기능의 **검색 제공자를 네이버 지역검색 →
카카오 로컬(키워드 장소 검색)** 으로 교체했다.

### 왜

- 네이버 지역검색(`developers.naver.com`의 "검색" API)은 **신규 앱 등록에서 검색 API 선택지가
  사라졌고**, 쓰려면 **제휴 심사**를 통과해야 한다. `.env`에 있던 기존 키(`4c1aev4g6v`)는 `401
  NID AUTH Result Invalid`로 죽어 있었다.
- 카카오 로컬 API는 **카카오 개발자 콘솔 앱의 REST API 키만 있으면** 별도 제휴·활성화 없이
  호출된다. 하루 10만 건 쿼터(개발엔 충분).

### 어떻게 (엔드포인트·응답 형태는 그대로)

클라이언트가 부르는 `GET /api/v1/bands/{bandId}/rooms/search?query=` 와 응답 JSON
(`{query, placeCount, places:[{name, roadAddress, address, category, phone, lat, lng}]}`)은
**변경 없음**. 내부 구현만 바뀌었다.

| 항목 | 내용 |
|---|---|
| 새 파일 | `KakaoLocalSearchClient`(카카오 로컬 `GET /v2/local/search/keyword.json`, 헤더 `Authorization: KakaoAK {키}`), `KakaoLocalProperties`(`app.kakao.local.*`), `KakaoLocalSearchParseTest`(순수 단위 6건) |
| 삭제 | `NaverLocalSearchClient`, `NaverSearchProperties`, `NaverLocalSearchParseTest` |
| 인터페이스 | `PlaceSearchClient`는 그대로 — "예외 안 던지고 빈 목록으로 통일" 방침 유지. 구현만 교체 |
| 설정 | `application.yml`: `app.naver.search.*` 블록 삭제, `app.kakao.local.*` 추가. `docker-compose.yml`/`.env`/`.env.example`: `NAVER_SEARCH_CLIENT_ID/SECRET` → `KAKAO_REST_API_KEY` |
| 좌표 | 카카오는 `x`(경도)/`y`(위도)를 WGS84 십진 그대로 준다(네이버의 ×1e7 정수 변환 불필요). KR 대략 경계 밖이면 좌표 null, 후보는 유지 |
| 지오코딩 | **안 건드림.** 주소→좌표 변환은 여전히 NCP 네이버(`app.naver.*`, `NaverGeocodingClient`). 합주실 저장 시 주소 문자열로 다시 지오코딩함 |

### 켜는 법 (집에서)

1. `developers.kakao.com` → 내 애플리케이션 → (카카오 로그인 쓰는 그 앱) → **앱 키** →
   **REST API 키** 복사. (네이티브 키 `210fa438...`와 다른 값, 같은 화면에 있음)
2. `.env`:
   ```
   KAKAO_REST_API_KEY=<REST API 키>
   ```
   (이미 `4fcda09cfaff1680213f1350788cb2bb` 넣어 둠 — 유효한지 아래로 확인)
3. 백엔드만 재생성 (`restart`는 `.env`를 다시 안 읽는다):
   ```bash
   cd E:\project\band
   docker compose up -d --force-recreate app
   ```
4. 클라이언트 재빌드 **불필요**.

### 확인하는 법

- 앱(에뮬레이터) 합주실 등록 폼 → 주소 칸에 **한글 상호명**(예: `그루브`, `홍대 합주실`)
  입력 → 350ms 뒤 후보 드롭다운.
- 안 뜨면 백엔드 로그:
  ```bash
  docker compose logs app -f
  ```
  - `카카오 로컬 검색 호출 실패 ... 401` → REST API 키가 틀림 → 콘솔에서 다시 복사
  - `카카오 로컬 검색 결과가 없다` → 키는 맞고 그 검색어에 결과가 없는 것(다른 상호로)
  - 아무 로그 없음 → 앱이 백엔드에 못 붙음(에뮬레이터는 `10.0.2.2:8080`, `docker compose ps`로 app healthy 확인)
- 숫자만 넣으면 결과 없음 — 반드시 한글 상호명으로.

### 알려진 이슈 / 안 한 것

- 통합 테스트(`RoomIntegrationTest`)는 **이 PC에서 Testcontainers 실행 불가** → CI에서 확인.
  로컬은 `./gradlew build -x test` + `KakaoLocalSearchParseTest` 단위 테스트만 돌렸고 통과.
- 패키지명은 `com.yeka.bandapp.room.naver` 그대로 뒀다(지오코딩이 아직 네이버라 반쯤만 맞는
  이름). 나중에 `...room.place` 등으로 옮기려면 지오코딩까지 같이 정리.
- 좌표 파싱 코드(약 15줄)는 API 응답에 공짜로 오는 값이라 남겨 뒀다. `PlaceSuggestion`의
  `lat`/`lng`는 nullable이고 등록 폼은 안 쓴다(`_pick()`이 이름·주소·연락처만 복사).

---

## 2. 클라이언트 자잘한 변경

| 파일 | 변경 | 이유 |
|---|---|---|
| `client/lib/features/auth/presentation/login_screen.dart` | `catch (e)` 블록에 `debugPrint('kakao login failed: $e')` 한 줄 | 카카오 로그인 실패가 UI에 "문제가 발생했습니다"로만 삼켜져 실제 원인(키해시 등)을 못 봤음 |
| `client/.gitignore` | `dart_defines.json` 추가 | `--dart-define-from-file`용 로컬 키 파일(카카오 네이티브 키·네이버 지도 ID). 커밋 금지 |

`client/android/` 는 통째로 gitignore라 `gradle.properties`의 `kotlin.incremental=false`
(아래 5-C) 는 커밋 안 됨 — 새 클론에서 `flutter create` 후 다시 넣어야 함.

---

## 3. dart_defines.json (클라이언트 실행용 로컬 키)

`client/dart_defines.json` (gitignore됨):
```json
{
  "KAKAO_NATIVE_APP_KEY": "210fa438c992deab8ffcbae21b7c68c1",
  "NAVER_MAP_CLIENT_ID": "3o8l749enu"
}
```
실행:
```bash
C:\flutter\bin\flutter.bat run -d emulator-5554 --dart-define-from-file=dart_defines.json
```

- **카카오 네이티브**: 위 키 + `client/android/local.properties`에 `kakao.appKey=210fa438...`
  한 줄. 그리고 **카카오 콘솔에 디버그 키해시 등록**해야 로그인 완료됨(§5-E).
- **네이버 지도**: `NAVER_MAP_CLIENT_ID`를 넣으면 지도 화면이 SDK를 초기화하는데,
  그 NCP 앱에 **Dynamic Map이 활성화 + 패키지명 `com.yeka.bandapp_client` 등록** 안 돼 있으면
  `NaverMapSdk onAuthFailed` → 플러그인 버그로 **앱이 통째로 크래시**한다(§5-F).
  현재는 지도 안 켤 거면 이 키를 **빼 두는 게 안전**.

---

## 4. 아직 남은 외부 키 작업 (앱 로직과 무관, 켤 때만)

| 기능 | 필요한 것 | 안 하면 |
|---|---|---|
| 주소 검색 | `KAKAO_REST_API_KEY` (`.env`) — REST API 키 | 후보 안 뜸, 직접 입력은 정상 |
| 카카오 로그인 | 네이티브 키 + `local.properties` + **콘솔에 디버그 키해시 등록** | "카카오 로그인 중 문제" (keyHash validation failed) |
| 네이버 지도 | NCP 앱에 Dynamic Map 활성화 + 패키지명 등록 + `NAVER_MAP_CLIENT_ID` dart-define | 지도 화면 크래시(키 넣었을 때) / 목록만(키 없을 때) |
| FCM 푸시 | Firebase 서비스계정 JSON (이미 `bandapp-dev-67c6f-...json` 있음) | 푸시 발송 no-op |

---

## 5. 집 PC(DESKTOP-ANFSPFK)에서 오늘 겪은 함정 — 재현 대비

### A. `claude` 명령이 IntelliJ Git Bash에서 안 먹음

`~/.local/bin`(네이티브 설치본)이 PATH에 없음. 영구 수정:
```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
grep -q bashrc ~/.bash_profile || echo '[ -r ~/.bashrc ] && . ~/.bashrc' >> ~/.bash_profile
```
IntelliJ 터미널 탭 새로 열기(안 되면 IDE 재시작).

### B. `flutter` / `adb` 도 PATH 없음 → 매번 풀 경로

한 번 등록(관리자 PowerShell):
```powershell
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";C:\flutter\bin;C:\Users\USER\AppData\Local\Android\sdk\platform-tools", "User")
```

### C. Kotlin 빌드 "different roots" 에러

pub 캐시(`C:`)와 프로젝트(`E:`)가 다른 드라이브 → `client/android/gradle.properties`에
`kotlin.incremental=false` 추가로 우회. (gitignore라 클론마다 다시 넣어야 함.
근본 해결은 `setx PUB_CACHE "E:\pub-cache"` 후 `flutter pub get`)

### D. `keytool` 없음 (디버그 키해시 뽑을 때)

Android Studio 내장 JBR 사용:
```bash
"/c/Program Files/Android/Android Studio/jbr/bin/keytool" -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android | openssl sha1 -binary | openssl base64
```

### E. 카카오 로그인 "keyHash validation failed"

에뮬레이터엔 카톡이 없어 웹(크롬)으로 로그인 폴백되는 건 **정상**. 로그인 후 콜백에서
`misconfigured / Android keyHash validation failed` → **디버그 키해시를 카카오 콘솔에
등록** 안 해서. 콘솔: 내 애플리케이션 → 앱 설정 → 플랫폼 → Android → 패키지명
`com.yeka.bandapp_client` + 위 §D로 뽑은 키해시 추가. PC마다 `debug.keystore`가 달라서 각자 등록.

### F. 네이버 지도 켜면 앱 크래시

`NaverMapSdk onAuthFailed` → `flutter_naver_map`이 실패 콜백에서 `reply.error()`를 두 번
불러 `IllegalStateException: Reply already submitted` → **FATAL**. NCP 앱에 Dynamic Map
활성화 + 패키지명 등록하면 해결. 그 전엔 `dart_defines.json`에서 `NAVER_MAP_CLIENT_ID` 빼기.

### G. 에뮬레이터에서 한글/영문 입력이 안 되고 숫자만

앱 문제 아님. 에뮬레이터 하드웨어 키보드 반쯤 꺼진 상태. Extended controls(`⋯`) →
Settings → **Enable keyboard input** ON. 또는 AVD `config.ini`에 `hw.keyboard=yes` 후 cold boot.

### H. Windows 데스크톱 빌드 실패

`flutter run -d all`이 Windows까지 잡음 → `firebase_cpp_sdk_windows`의 CMake가 `< 3.5`
요구, 설치된 CMake 4.x와 충돌. **Windows는 타깃 아님** → `-d emulator-5554`로 안드로이드만.

### I. 잡다

- `docker pompose` 오타 주의(→ `compose`).
- PowerShell에선 `&&` 안 됨(PS 5.1). Git Bash에서 `&&` 쓰거나 PS는 줄바꿈/`;`.
- 포트 5432: Windows 네이티브 PostgreSQL 18이 선점. 호스트에서 DB 직접 볼 땐
  `docker-compose.yml`을 `5433:5432`로 remap. 앱 컨테이너 내부통신은 영향 없음(§client-DEVLOG 3-B).
