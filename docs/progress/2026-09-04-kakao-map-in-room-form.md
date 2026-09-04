# 2026-09-04 — 합주실 등록 폼에 지도 + 지도·지오코딩 카카오 통일

> 같은 날 앞선 작업([카카오 로컬 검색 전환](2026-09-04-kakao-local-search-and-home-pc-setup.md))의
> 후속. 그 문서의 "안 한 것"에 남아 있던 두 가지(등록 폼이 좌표를 안 씀, 지오코딩은 여전히 네이버)를
> 정리하면서, 지도까지 카카오로 옮겼다.

---

## 1. 한 줄 요약

합주실 등록 폼에서 **지도를 보면서** 검색 후보를 고르고, **화면에서 확인한 그 위치가 그대로
저장되게** 했다. 그 과정에서 지도·지오코딩 제공자를 네이버에서 카카오로 통일해, 관리할
클라우드 계정이 카카오 하나로 줄었다.

---

## 2. 왜 했나

### 사용자가 겪던 것

같은 이름의 합주실이 여러 개 검색될 때, 주소 문자열만 보고 골라야 했다. 고르고 나서도 그 위치가
맞는지 등록 전에 확인할 방법이 없었다.

### 그 뒤에 있던 진짜 문제

좌표를 얻는 경로가 **검색과 저장에서 서로 달랐다.**

| | 쓰던 것 |
|---|---|
| 검색 (후보 목록) | 카카오 로컬 — 후보마다 좌표가 **응답에 이미 실려 왔다** |
| 저장 (등록 버튼) | 네이버 NCP 지오코딩 — 주소 문자열로 **처음부터 다시** 변환 |

그런데 Flutter의 `PlaceSuggestion`이 카카오가 준 좌표를 **파싱조차 하지 않고 버리고** 있었다.
결과적으로:

- 제공자가 달라, 검색으로 고른 위치와 실제로 저장되는 좌표가 어긋날 수 있었다.
  지도를 붙여봤자 "확인하고 등록"이 성립하지 않는다.
- 이 PC의 `.env`에는 NCP 키가 비어 있어 지오코딩이 **항상 실패**했다. 등록한 합주실은 좌표가
  `null`이 되어 지도 화면에서 "위치 없음"으로 남았다.

### 왜 카카오로 통일했나

지도 렌더링은 네이버(`flutter_naver_map`)였는데, 켜려면 NCP 계정 + Maps Application 등록 +
Dynamic Map 활성화 + 패키지명 등록이 필요했다. 아직 아무도 만져본 적이 없었고, 설정이 덜 된 채
키만 넣으면 앱이 통째로 크래시하는 것으로 알려져 있었다.

반면 카카오맵은 **로그인에 이미 쓰고 있는 네이티브 앱 키를 그대로** 쓴다. 필요한 키 해시 등록은
카카오 로그인 때문에 어차피 해야 하는 작업이라, 실질적인 추가 설정이 0이다. 검색·지도가 같은
제공자가 되어 핀과 저장 좌표가 정확히 맞는 것은 덤이다.

---

## 3. 무엇을 바꿨나

### 백엔드

| 파일 | 변경 |
|---|---|
| `room/place/KakaoGeocodingClient.java` | **신규.** 카카오 로컬 주소검색(`/v2/local/search/address.json`)으로 주소→좌표 변환. 장소 검색과 **같은 REST API 키·설정**(`KakaoLocalProperties`)을 그대로 쓴다 |
| `room/naver/NaverGeocodingClient.java`, `NaverProperties.java` | **삭제** |
| `room/dto/CreateRoomRequest`, `UpdateRoomRequest` | 선택 필드 `lat`/`lng` 추가. 한국 범위 검증(위도 33.0~39.7, 경도 124.0~132.0) — 클라이언트가 보내는 값이라 신뢰 경계에서 막는다 |
| `room/service/RoomService.java` | 좌표가 오면 **지오코딩을 건너뛰고 그대로 저장**. 없을 때만(직접 입력한 주소) 지오코딩 |
| `application.yml`, `docker-compose.yml`, `.env.example` | `app.naver.*` / `NAVER_MAP_CLIENT_ID`·`SECRET` 제거 |
| 패키지 `room.naver` → `room.place` | 네이버가 하나도 안 남아서 이름을 맞췄다 (순수 rename) |

`GeocodingClient` 인터페이스("실패를 예외로 올리지 않고 좌표 없이 진행한다")는 그대로다.
Phase 3 완료 기준인 **"지오코딩 실패해도 등록은 성공한다"** 는 계속 유효하다.

수정(PUT)은 요청에 좌표가 오면 **주소 변경 여부와 무관하게** 덮어쓴다. 좌표 없이 저장됐던 옛
합주실을, 주소는 그대로 두고 검색으로 다시 골라 채울 수 있어야 하기 때문이다.

### 클라이언트

| 파일 | 변경 |
|---|---|
| `pubspec.yaml` | `flutter_naver_map` → `kakao_map_sdk` (네이티브 래퍼, Android/iOS) |
| `data/place_models.dart` | 버리던 `lat`/`lng`를 파싱 + `hasLocation` |
| `data/room_repository.dart` | `create`/`update`가 좌표를 함께 전송 |
| `presentation/room_form_screen.dart` | **주소 칸 아래 180px 지도.** 후보를 핀으로 표시, 핀 탭 = 선택 |
| `presentation/map_screen.dart` | 기존 합주실 지도를 카카오맵으로 교체 (마커·카메라 이동만) |
| `presentation/widgets/room_map_bits.dart` | **신규.** 두 화면이 공유하는 마커 스타일·폴백 안내 |
| `core/config/app_config.dart`, `main.dart` | `naverMapClientId` 제거, 카카오 SDK 초기화 + **인증 실패 가드** + 디버그 시 키 해시 로그 출력 |
| `core/config/native_abi.dart` (+ `_io`/`_web`) | **신규.** ARM ABI 여부 판별 — x86_64 에뮬레이터에서 지도 SDK 초기화를 막는다(§7-B) |
| `android/` (gitignore) | minSdk 23, 위치·인터넷 권한, ProGuard 규칙, **카카오 로그인 리다이렉트 스킴** |

---

## 4. 어떻게 동작하나

```
주소 칸에 "홍대 합주실" 입력
        ↓ 350ms 디바운스
GET /bands/{id}/rooms/search?query=홍대 합주실
        ↓ 카카오 로컬 (백엔드가 대신 호출, REST API 키는 서버에만)
후보 5건 (이름·주소·전화·좌표)
        ↓
목록에 표시  +  지도에 핀 5개   ← 이번에 추가된 부분
        ↓ 목록 항목 또는 핀을 탭
이름·주소·연락처 자동 입력 + 그 좌표를 폼이 기억 + 카메라가 그 자리로
        ↓ 저장
POST /bands/{id}/rooms  { name, address, phone, memo, lat, lng }
        ↓ 서버: 좌표가 왔으므로 지오코딩 생략
DB 저장 → 합주실 지도 화면에 같은 위치로 표시
```

주소를 손으로 고치면 기억해 둔 좌표를 **버린다**. 그래야 직전에 고른 핀의 좌표가 엉뚱한 새
주소에 붙지 않는다. 이 경우 저장 시 서버가 주소로 지오코딩한다.

---

## 5. 직접 확인하는 법

### 5-A. 사전 준비 — 카카오 콘솔 (앱을 새로 만들었다면 필수)

지도·검색·로그인이 **카카오 앱 하나**를 공유한다. NCP(네이버) 작업은 이제 없다.

**콘솔에서 복사할 값** (앱 설정 → 앱 키):

| 값 | 쓰이는 곳 |
|---|---|
| 앱 ID (숫자) | 백엔드 `KAKAO_APP_ID` |
| 네이티브 앱 키 | 클라이언트 로그인 **+ 지도** |
| REST API 키 | 백엔드 주소검색 **+ 지오코딩** |
| 어드민 키 | 백엔드 회원탈퇴(연결끊기) |

**콘솔에서 설정할 것:**

1. 앱 설정 → **플랫폼 → Android 등록** — 아래 두 값을 **복사해서** 넣는다(타이핑하면 오타가 난다)

   | 칸 | 값 |
   |---|---|
   | 패키지명 | `com.example.bandapp_client` |
   | 키 해시 | `ahCJ5a5dXyiPh3x9ksny6yMbjzk=` (이 PC의 디버그 키스토어 기준) |

   넣은 뒤 **하단 저장 버튼을 누른다** — 칸에 입력만 하면 반영되지 않는다.
   이게 없으면 로그인은 `keyHash validation failed`, 지도는 인증 실패다.

   키 해시가 계속 거부되면 순서대로 확인한다:
   - **지금 보고 있는 콘솔 앱이 맞는 앱인가.** 앱이 쓰는 네이티브 키는
     `client/dart_defines.json`에 있고, 실행 로그의 리다이렉트 스킴 `kakao<네이티브키>://oauth`
     에도 그대로 찍힌다. 앱을 새로 만들었다면 옛 앱 화면에서 등록하고 있을 수 있다.
   - **앱이 스스로 계산한 값을 쓴다.** 디버그 빌드는 시작할 때 로그에
     `kakao keyHash (콘솔에 등록할 값): ...` 을 찍는다(`main.dart`). 이게 SDK가 서버로
     실제 보내는 값이라 100% 정확하다. 릴리스 키스토어로 바꿀 때도 이 줄만 보면 된다.
2. 제품 설정 → **카카오 로그인 활성화 ON**
3. 카카오 로그인 → **동의항목**: `프로필 정보(닉네임)`, `카카오계정(이메일)`
4. **Redirect URI는 등록 불필요** — 네이티브 SDK 로그인(`loginWithKakaoTalk` /
   `loginWithKakaoAccount`)은 커스텀 스킴으로 돌아온다. 웹 빌드를 켤 때만 필요하다.
5. **지도는 추가 등록이 없다** — 같은 네이티브 키 + 같은 키 해시를 재사용한다.

키 해시는 `keytool ... | openssl sha1 -binary | openssl base64`로 뽑는다. Windows에 openssl이
없으면, 앱 실행 중 `KakaoMapSdk.instance.hashKey()`가 콘솔에 붙여넣을 값을 그대로 준다.

### 5-B. 사전 준비 — 키 넣기

`bandApp/.env` (백엔드):

```
KAKAO_APP_ID=<앱 ID>
KAKAO_ADMIN_KEY=<어드민 키>
KAKAO_REST_API_KEY=<REST API 키>
```

> `KAKAO_APP_ID`가 제일 중요하다. 백엔드는 카카오 토큰의 `app_id`를 이 값과 대조해서 **다른 앱
> 토큰을 거부**한다(`AuthService`). 앱을 새로 만들었는데 옛 ID가 남아 있으면 로그인이 전부
> `KAKAO_TOKEN_INVALID`로 죽는다.

`client/dart_defines.json` (gitignore, 없으면 새로 만든다):

```json
{ "KAKAO_NATIVE_APP_KEY": "<네이티브 앱 키>" }
```

`client/android/local.properties`에 한 줄 (로그인 리다이렉트 스킴에 꽂힌다):

```
kakao.appKey=<네이티브 앱 키>
```

반영 — **무엇을 고쳤느냐에 따라 명령이 다르다.** 이걸 헷갈리면 한참 헤맨다:

| 고친 것 | 명령 |
|---|---|
| `.env` 값만 | `docker compose up -d --force-recreate app` |
| **백엔드 코드** | `docker compose up -d --build app` |

```powershell
cd C:\band\bandApp; docker compose up -d --build app
```

`docker compose up -d`는 **이미지가 있으면 재빌드하지 않는다.** `--force-recreate`도 컨테이너만
다시 만들 뿐 이미지는 그대로다 — 즉 코드 변경이 반영되지 않는다. 실제로 이번에 이틀 전 이미지가
계속 돌면서 `rooms/search` 엔드포인트가 없는 상태였고, 그 탓에 `/rooms/search` 요청이
`/rooms/{roomId}` 로 흘러가 400(INVALID_INPUT)이 났다. 지금 도는 이미지가 언제 것인지는
`docker image inspect bandapp-app --format '{{.Created}}'` 로 확인한다.

### 5-C. 실행 — 지도를 보려면 **실기기여야 한다**

지도는 실기기에서만 나온다(이유는 §7-B). 폰에서 개발자 옵션 → USB 디버깅을 켜고 연결한다.

```powershell
flutter devices                     # 폰의 기기 ID 확인 (예: R3CX40J7QJE)
cd C:\band\bandApp\client; flutter run -d <기기ID> --dart-define-from-file=dart_defines.json
```

> Flutter SDK 경로는 이 PC 기준 `C:\src\flutter`다(앞선 문서의 `C:\flutter`는 다른 PC).
> PowerShell 5.1에는 `&&`가 없다 — 명령을 이을 때는 `;`를 쓴다.

**실기기는 백엔드 주소가 다르다.** `10.0.2.2`는 에뮬레이터 전용 별칭이라 폰에서는 아무 데도 가지
않는다. USB 터널을 뚫는 게 가장 확실하다(WiFi·테더링·방화벽과 무관):

```powershell
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" -s <기기ID> reverse tcp:8080 tcp:8080
```

그러면 기기의 `localhost:8080`이 PC의 8080으로 간다. `client/dart_defines.json`에 함께 넣어 둔다:

```json
{ "KAKAO_NATIVE_APP_KEY": "<네이티브 앱 키>", "API_BASE_URL": "http://localhost:8080" }
```

`adb reverse`는 **USB를 뽑거나 기기를 다시 연결하면 사라진다.** 붙일 때마다 다시 실행한다.
에뮬레이터에도 같은 명령이 그대로 통한다.

평문 HTTP는 Android 9+ 가 기본 차단하므로 `android/app/src/debug/AndroidManifest.xml`에
`android:usesCleartextTraffic="true"`를 넣어 뒀다(**디버그 빌드 전용** — 배포는 https).
이 파일은 `client/android/` 아래라 gitignore다.

### 5-D. 기대 결과

1. 합주실 등록 폼 → 주소 칸에 **한글 상호명**(예: `홍대 합주실`) 입력
2. 350ms 뒤 후보 목록 + **지도에 핀 여러 개**
3. 핀 또는 목록 항목 탭 → 이름·주소·연락처 자동 입력, 지도가 그 위치로 이동
4. 저장 → 합주실 지도 화면(하단 탭)에서 **같은 위치**에 마커. "위치 없음" 배지가 아니어야 한다
5. 주소를 직접 타이핑해서 저장 → 서버가 카카오 지오코딩으로 좌표를 채운다
6. `dart_defines.json` 없이 실행 → 지도 자리에 안내 문구만, **등록은 정상, 크래시 없음**

### 5-E. 문제 해결

| 증상 | 원인 | 조치 |
|---|---|---|
| 후보가 안 뜬다 | 백엔드에 `KAKAO_REST_API_KEY` 없음/틀림 | `docker compose logs app -f` → `카카오 로컬 검색 호출 실패 ... 401`이면 키를 다시 복사 |
| 후보는 뜨는데 지도가 안 보인다 | 네이티브 앱 키 미설정 | 지도 자리 안내 문구가 이유를 말해준다 |
| 지도 자리에 "인증에 실패했습니다" | 콘솔에 패키지명·키 해시 미등록 | 5-A의 1번 |
| 숫자만 넣으면 결과 없음 | 카카오 키워드 검색 특성 | 한글 상호명으로 검색 |
| 카카오 로그인이 브라우저에서 멈춤 | `local.properties`의 `kakao.appKey` 누락 → 리다이렉트 스킴이 비어버림 | 5-B |
| `keyHash validation failed` | 콘솔의 키 해시가 이 빌드 서명과 다름 / 다른 앱에 등록 / 저장 안 누름 | 5-A의 확인 순서 |
| 앱이 켜지자마자 죽음 | x86_64 에뮬레이터 + 카카오맵 ARM 전용 라이브러리 | 해결됨(§7-B). 실기기 사용 |
| "서버에 연결하지 못했습니다" | 실기기가 `10.0.2.2`(에뮬 전용 주소)로 붙으려 함 / 평문 HTTP 차단 | 5-C의 `adb reverse` + `API_BASE_URL` |
| 검색 요청이 400 `INVALID_INPUT` | **도는 이미지가 옛 코드** — `rooms/search` 가 없어 `/{roomId}` 로 매칭됨 | `docker compose up -d --build app` (5-B) |

---

## 6. 실제 검증 기록

| 항목 | 결과 |
|---|---|
| 백엔드 컴파일 (`compileJava`, `compileTestJava`) | ✅ |
| 백엔드 단위 테스트 (`*ParseTest`, 카카오 지오코딩 파싱 6건 포함) | ✅ |
| 백엔드 통합 테스트 (`RoomIntegrationTest`, 신규 4건 포함) | ✅ **20건 통과** (Testcontainers 로컬 실행 문제를 §7-A로 해결한 뒤) |
| 백엔드 전체 테스트 (`./gradlew test`) | ✅ **302건 통과**, 실패 0 |
| `flutter analyze` (변경 파일) | ✅ 에러 0 (남은 것은 저장소 전반의 기존 스타일 info) |
| `flutter test` | ✅ 27건 통과 (`place_models_test.dart` 신규 5건 포함) |
| `flutter build apk --debug` | ✅ 성공 — 카카오맵 SDK 링크, minSdk 23, 매니페스트 병합 확인 |
| `flutter build web` | ✅ 성공 (`dart:ffi` 조건부 import가 웹을 깨지 않는지 확인) |
| 실기기 앱 구동 (갤럭시 S24) | ✅ 앱 실행·카카오 로그인 리다이렉트 스킴 동작 확인 |
| 실기기 지도·등록 end-to-end | ⏸ **미실행** — 카카오 콘솔 키 해시 등록(5-A) 후 확인 필요 |

신규 백엔드 통합 테스트 4건:

- `request_coordinates_are_saved_without_geocoding` — 좌표를 실어 보내면 그대로 저장되고 지오코딩 호출 0
- `half_coordinates_fall_back_to_geocoding` — 위도만 온 반쪽 좌표는 무시하고 지오코딩
- `out_of_range_coordinates_are_rejected` — 위도 99 → 400
- `update_with_coordinates_backfills_room_without_changing_address` — 주소 그대로, 좌표만 채우기

---

## 7. 알려진 이슈 / 안 한 것

### 7-A. (해결) 이 PC에서 Testcontainers가 안 돌던 이유

앞선 문서에 "이 PC에서 Testcontainers 실행 불가"로 적혀 있던 문제의 원인을 찾았다.
**Docker Desktop을 켜도** 통합 테스트가 20건 전부 `Could not find a valid Docker environment`로
죽었는데, 진짜 원인은 두 겹이었다.

1. `~/.testcontainers.properties`에 `docker.client.strategy=NpipeSocketClientProviderStrategy`가
   캐시돼 있었다. 이 전략은 `npipe:////./pipe/docker_engine`만 보는데, 이 PC의 Docker Desktop은
   `dockerDesktopLinuxEngine` 파이프를 쓴다(`docker context ls`로 확인).
2. 파이프를 맞춰줘도 **HTTP 400**이 났다. Docker Engine 29는 최소 API 버전 1.40을 요구하는데
   (`docker version`의 MinAPIVersion), Testcontainers 1.20.4가 물고 있는 docker-java는 더 낮은
   버전으로 협상한다.

조치 — **저장소 변경 없이 이 PC의 홈 디렉터리 설정 파일 두 개만** 손봤다:

```
# %USERPROFILE%\.testcontainers.properties
docker.host=npipe\:////./pipe/dockerDesktopLinuxEngine     # 기존 docker.client.strategy 줄은 삭제

# %USERPROFILE%\.docker-java.properties   (신규)
api.version=1.44
```

원본은 `.testcontainers.properties.bak`으로 백업해 뒀다. 근본 해결은 Testcontainers를 1.21+로
올리는 것인데, CI에서는 지금 버전으로 잘 돌고 있어 저장소는 건드리지 않았다.

### 7-B. (해결) 에뮬레이터에서 앱이 켜지자마자 죽던 문제

`kakao_map_sdk`는 **ARM 네이티브 라이브러리만 배포한다.** 빌드된 APK를 열어 보면:

| ABI | `libK3fAndroid.so` (카카오맵 엔진) |
|---|---|
| `arm64-v8a` | 있음 |
| `armeabi-v7a` | 있음 |
| `x86_64` | **없음** |

그래서 흔한 x86_64 에뮬레이터에서 SDK를 초기화하면 이렇게 죽는다:

```
dlopen failed: "...libK3fAndroid.so" is for EM_AARCH64 (183) instead of EM_X86_64 (62)
  at com.kakao.vectormap.KakaoMapSdk.<init>
```

**Dart `try/catch`로는 못 막는다.** 네이티브 메인 스레드에서 나는 `UnsatisfiedLinkError`라
Dart 로 올라오지 않고 프로세스가 통째로 종료된다. 유일한 방어는 **지원되지 않는 ABI에서는
초기화를 아예 호출하지 않는 것**이라, `core/config/native_abi.dart`(+ io/web 조건부 분기)에서
`Abi.current()`로 ARM 여부를 보고 `AppConfig.mapEnabled`를 끈다. 웹은 `dart:ffi`를 못 쓰므로
조건부 import로 갈랐다(`flutter build web` 통과 확인).

결과: 에뮬레이터에서도 앱이 정상 실행되고, 지도 자리에만 "ARM 기기 전용" 안내가 뜬다.
로그인·합주실 등록·주소 검색·좌표 저장까지 전부 에뮬레이터에서 확인할 수 있다.

### 7-C. 남은 것

- **지도 화면 자체는 실기기에서만 검증할 수 있다**(에뮬레이터는 §7-B 때문에 안내 문구).
  크롬(웹)도 지도는 안 나오지만 나머지 기능은 확인 가능하다.
- **패키지명이 `com.example.bandapp_client`로 남아 있다** — `flutter create` 기본값이다.
  구글 플레이는 `com.example.`로 시작하는 패키지를 거부하므로 출시 전에 바꿔야 하고,
  바꾸면 카카오 콘솔의 플랫폼 등록(패키지명·키 해시)도 다시 해야 한다. 테스트가 끝난 뒤
  한 번에 처리하는 게 낫다.
- **릴리스 키스토어의 키 해시는 아직 등록 전이다.** 릴리스 빌드로 처음 돌릴 때
  §5-A의 로그 한 줄(`kakao keyHash ...`)을 보고 콘솔에 추가한다.
- **`kakao_map_sdk`는 신생 패키지다** (1.3.0, 좋아요 10 / 주간 1.3k). 네이티브 래퍼이고
  Android/iOS는 production-ready로 표기돼 있으나, 검증된 `flutter_naver_map`(주간 7.6k)보다
  이력이 짧다. 이 앱이 쓰는 기능은 마커 몇 개 + 카메라 이동뿐이라 SDK 표면을 거의 안 건드린다.
- **웹에서는 지도가 안 나온다.** `kakao_map_sdk`의 웹 지원은 experimental이라 기존 `kIsWeb`
  가드를 그대로 뒀다 — 웹에서는 목록만 보인다.
- **핀 드래그로 위치 미세조정은 없다.** 검색 결과 좌표로 충분하다는 전제. 실제로 위치가 틀린
  사례가 나오면 그때 붙인다.
- **기존 합주실 좌표 백필은 안 했다.** 이미 등록된 방은 수정 화면에서 검색으로 다시 고르면
  채워진다. 데이터가 거의 없어 마이그레이션 스크립트를 쓸 이유가 없었다.
- **ProGuard 규칙은 아직 잠자고 있다.** `buildTypes.release`에 `isMinifyEnabled`가 없어 난독화가
  돌지 않는다. 나중에 켜면 그때 규칙이 필요해지므로 미리 넣어 뒀다.
- **`client/android/`는 통째로 gitignore**다. minSdk·권한·ProGuard·카카오 로그인 스킴 설정은
  커밋되지 않으므로, 새 클론에서는 다시 넣어야 한다.

---

## 8. 커밋

| 커밋 | 내용 |
|---|---|
| `cd20a6d` | (백엔드) 지오코딩 카카오 교체 + 요청 좌표 우선 저장 |
| `02cea41` | (클라이언트) 등록 폼 지도 + 지도 제공자 카카오 통일 |
| `1cbbc17` | (리팩터) `room.naver` → `room.place` 패키지 rename |

브랜치: `feat/kakao-map-in-room-form`
