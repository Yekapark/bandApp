# 직접 테스트 가이드 (Phase 0~3)

지금까지 만든 백엔드(인증 · 밴드 · 초대 · 멤버 · 합주실)를 손으로 확인하는 절차.
읽으면서 위에서 아래로 따라 하면 전체 흐름이 한 번 돈다.

---

## 0. 준비

```bash
cd band
cp .env.example .env          # 최초 1회. JWT_SECRET 은 아무 32자 이상 문자열로 채운다
docker compose up --build -d
curl -s http://localhost:8080/actuator/health     # {"status":"UP"} 나오면 준비 완료 (20~40초 걸림)
```

- 카카오(`KAKAO_APP_ID`)와 네이버(`NAVER_MAP_CLIENT_ID`)는 **비워 둬도 된다.**
  - 카카오 로그인만 `503` 으로 응답하고 나머지는 정상.
  - 합주실은 저장되지만 좌표(`lat`/`lng`)가 `null` 로 온다.
- 끝낼 때: `docker compose down -v` (`-v` 는 DB 데이터까지 삭제)

---

## 1. 제일 쉬운 길 — Swagger UI

브라우저에서 **http://localhost:8080/swagger-ui.html**

- 모든 API가 요청/응답 형식과 함께 나온다. 각 API의 **Try it out** 버튼으로 바로 호출.
- 로그인 후 받은 `accessToken` 을 오른쪽 위 **Authorize** 버튼에 붙여 넣으면,
  이후 모든 요청에 `Authorization: Bearer ...` 가 자동으로 붙는다.
- 순서: `auth/signup` 실행 → 응답의 `data.tokens.accessToken` 복사 → Authorize 에 붙여넣기 → 나머지 API 테스트.

> 아래 2~6 은 터미널(curl)로 하는 방법. Swagger 로 할 거면 경로/본문만 참고하면 된다.

---

## 2. curl 준비

```bash
B=http://localhost:8080
```

**한글 주의 (Windows Git Bash)**: `curl -d` 본문에 한글을 넣으면 인코딩이 깨져 `INVALID_INPUT` 이 난다.
아래 예시는 이름·밴드명을 영문으로 쓴다. 한글을 꼭 넣고 싶으면 JSON 을 파일로 저장해
`curl --data @body.json` 으로 보내거나 PowerShell `Invoke-RestMethod` 를 쓴다.

토큰은 응답 JSON 에서 직접 복사해 변수에 넣는다:

```bash
# 예: 가입 응답에서 accessToken 을 눈으로 찾아 복사한 뒤
LEAD='eyJhbGciOi...(복사한 토큰)...'
```

---

## 3. 인증 (Phase 1)

### 3.1 이메일 가입 → 로그인 → 내 정보

```bash
# 가입 → 201, 응답에 user + tokens(accessToken, refreshToken)
curl -s -XPOST $B/api/v1/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"leader@test.app","password":"pw12345678","name":"Leader"}'

# 로그인 → 200, 같은 형식
curl -s -XPOST $B/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"leader@test.app","password":"pw12345678"}'

# 내 정보 → 200. 위 응답의 accessToken 을 LEAD 에 넣고:
curl -s $B/api/v1/users/me -H "Authorization: Bearer $LEAD"
```

확인 포인트
- 비밀번호 8자 미만 → `400 INVALID_INPUT` (fieldErrors 에 password)
- 없는 이메일 / 틀린 비밀번호 → 둘 다 똑같이 `401 INVALID_CREDENTIALS` (계정 존재 여부를 숨김)
- **이메일 대소문자 무관**: `Leader@Test.App` 로 가입해도 `leader@test.app` 로 로그인된다. 대소문자만 바꿔 재가입하면 `409 EMAIL_ALREADY_REGISTERED`
- 헤더 없이 `/users/me` → `401 UNAUTHORIZED`

### 3.2 토큰 갱신 (refresh)

```bash
# 로그인 응답의 refreshToken 을 RT 에 넣고:
curl -s -XPOST $B/api/v1/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$RT\"}"        # → 200, 새 accessToken + refreshToken
```

확인 포인트
- **방금 쓴 refresh 를 60초 안에 한 번 더 보내면** → `200` 이고 **첫 번째와 똑같은 새 토큰**이 온다
  (네트워크 재시도·더블탭 대비. 예전엔 여기서 전 기기 로그아웃됐음)
- 60초가 지난 뒤 옛 refresh 를 다시 쓰면 → `401 REFRESH_TOKEN_INVALID` + 그 사용자 전 세션 무효화 (탈취 방어)

### 3.3 로그아웃

```bash
curl -s -o /dev/null -w '%{http_code}\n' -XPOST $B/api/v1/auth/logout \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$RT\"}"   # → 204 (이미 만료돼 있어도 204, 멱등)
```

### 3.4 카카오 로그인

```bash
curl -s -XPOST $B/api/v1/auth/kakao -H 'Content-Type: application/json' \
  -d '{"accessToken":"whatever"}'        # 로컬(KAKAO_APP_ID 미설정) → 503 KAKAO_NOT_CONFIGURED (정상)
```

실제로 테스트하려면 `.env` 에 `KAKAO_APP_ID` / `KAKAO_ADMIN_KEY` 를 넣고 재기동한 뒤,
카카오 SDK 로 받은 진짜 access token 을 넣어야 한다.

### 3.5 탈퇴

```bash
# 이메일 계정은 본문에 password 재확인 필요
curl -s -o /dev/null -w '%{http_code}\n' -XPOST $B/api/v1/users/me/withdraw \
  -H "Authorization: Bearer $LEAD" -H 'Content-Type: application/json' \
  -d '{"password":"pw12345678"}'          # → 204
```

확인 포인트
- 탈퇴 직후 그 `accessToken` 으로 `/users/me` → `401 ACCOUNT_WITHDRAWN` (즉시 차단)
- 같은 이메일로 다시 가입 → `201` (재가입 가능)
- 밴드에 속해 있었다면 → 4.7 참조 (자동 정리)

### 3.6 레이트리밋

`/api/v1/auth/**` 는 IP 당 분당 20회. `/login` 을 21번 연타하면 21번째부터 `429 TOO_MANY_REQUESTS`.
1분 기다리거나 `docker compose restart redis` 로 초기화.

---

## 4. 밴드 · 초대 · 멤버 (Phase 2)

사용자 3명(리더·멤버·낯선이)을 3.1 로 만들어 `LEAD` / `MEMBER` / `STRANGER` 토큰을 준비한다.

### 4.1 밴드 생성 / 조회 / 내 밴드 목록

```bash
# 생성 → 201. 응답 data.id 가 밴드 id. 만든 사람이 자동으로 LEADER
curl -s -XPOST $B/api/v1/bands -H "Authorization: Bearer $LEAD" \
  -H 'Content-Type: application/json' -d '{"name":"Rose Motel"}'
BID=<위 응답의 id>

curl -s $B/api/v1/bands/$BID -H "Authorization: Bearer $LEAD"       # 밴드 조회 (멤버만)
curl -s $B/api/v1/bands       -H "Authorization: Bearer $LEAD"       # 내가 속한 밴드 목록 (역할·멤버수 포함)
```

확인 포인트
- 낯선이가 `GET /api/v1/bands/$BID` → `403 NOT_BAND_MEMBER` (밴드가 없어도 같은 응답 — 존재 여부 숨김)

### 4.2 초대코드 발급 / 참여

```bash
# 발급/재발급 → 201. 본문 없이 호출하면 7일·무제한. data.code, data.link 확인
curl -s -XPOST $B/api/v1/bands/$BID/invites -H "Authorization: Bearer $LEAD"
CODE=<위 응답의 code>

# 옵션 지정 발급: {"maxUses":1,"ttlDays":3}
# 현재 활성 코드 조회 (밴드장만)
curl -s $B/api/v1/bands/$BID/invites/current -H "Authorization: Bearer $LEAD"

# 멤버가 코드로 참여 → 200, 응답은 밴드 정보
curl -s -XPOST $B/api/v1/bands/join -H "Authorization: Bearer $MEMBER" \
  -H 'Content-Type: application/json' -d "{\"code\":\"$CODE\"}"
```

확인 포인트 (참여 거부 사유가 각각 다름)
- 이미 그 밴드 멤버 → `409 ALREADY_BAND_MEMBER` (사용 횟수는 안 깎임)
- 없는 코드 → `404 INVITE_NOT_FOUND`
- 재발급 후 옛 코드로 참여 → `410 INVITE_REVOKED`
- `DELETE /api/v1/bands/$BID/invites/current` (밴드장) 로 무효화한 코드 → `410 INVITE_REVOKED`
- `maxUses:1` 코드를 두 번째 사람이 쓰면 → `409 INVITE_EXHAUSTED`
- 만료(`ttlDays` 경과) 코드 → `410 INVITE_EXPIRED` (짧게 테스트하려면 `ttlDays` 를 못 줄이므로 통합 테스트에서만 확인)
- 참여는 계정당 분당 10회 / IP 당 20회 제한

### 4.3 초대 링크 / 랜딩 (무인증)

```bash
curl -s $B/invite/$CODE                                    # HTML 랜딩 페이지 (앱 미설치 폴백)
curl -s $B/.well-known/apple-app-site-association          # iOS 검증 JSON
curl -s $B/.well-known/assetlinks.json                     # Android 검증 JSON
```

### 4.4 멤버 목록 / 탈퇴 / 추방

```bash
curl -s $B/api/v1/bands/$BID/members -H "Authorization: Bearer $LEAD"   # 가입순, 역할·이름 포함

# 멤버 자발적 탈퇴 → 204
curl -s -o /dev/null -w '%{http_code}\n' -XPOST $B/api/v1/bands/$BID/members/leave \
  -H "Authorization: Bearer $MEMBER"

# 밴드장이 멤버 추방 → 204  (targetUserId = 추방할 사람의 user id, /users/me 로 확인)
curl -s -o /dev/null -w '%{http_code}\n' -XDELETE $B/api/v1/bands/$BID/members/<targetUserId> \
  -H "Authorization: Bearer $LEAD"
```

확인 포인트
- **밴드장이 위임 없이 탈퇴 시도** → `409 LEADER_MUST_DELEGATE_BEFORE_LEAVING`
- 일반 멤버가 추방 시도 → `403 NOT_BAND_LEADER`
- 자기 자신 추방 → `400 CANNOT_KICK_SELF`
- 탈퇴/추방된 사람이 그 밴드 조회 → `403`

### 4.5 밴드 설정 (일정 등록 권한 모드)

```bash
curl -s -XPUT $B/api/v1/bands/$BID/settings -H "Authorization: Bearer $LEAD" \
  -H 'Content-Type: application/json' -d '{"reservationPermission":"ANYONE"}'
```

- 값: `LEADER_ONLY`(기본) / `ANYONE` / `APPROVAL_REQUIRED`. 이상한 값 → `400`
- 밴드장이 아닌 사람이 변경 → `403 NOT_BAND_LEADER`

### 4.6 밴드장 위임

```bash
# newLeaderUserId = 넘겨받을 멤버의 user id
curl -s -XPOST $B/api/v1/bands/$BID/leader -H "Authorization: Bearer $LEAD" \
  -H 'Content-Type: application/json' -d '{"newLeaderUserId":<memberUserId>}'
```

확인 포인트
- 위임 후 `GET .../members` 에서 LEADER 는 **정확히 한 명**(넘겨받은 사람), 원래 밴드장은 MEMBER
- 원래 밴드장이 설정 변경 시도 → `403`, 새 밴드장은 `200`
- 자기 자신에게 위임 → `400 CANNOT_DELEGATE_TO_SELF`

### 4.7 탈퇴 시 밴드 자동 정리 (점검 때 추가)

리더 + 멤버 2명(먼저 가입 M1, 나중 M2)인 밴드를 만든 뒤 **리더가 탈퇴**(3.5):

```bash
curl -s $B/api/v1/bands/$BID/members -H "Authorization: Bearer $M1"
```

- M1(가장 먼저 가입한 멤버)이 자동으로 LEADER 로 승격, M2 는 MEMBER 그대로
- M1 이 이제 초대코드 발급 가능(`POST .../invites` → 201)
- 리더가 유일 멤버였다면 그 밴드는 활성 멤버 0 → 아무도 접근 못 함(사실상 소멸)
- 밴드에 안 속한 사람이 탈퇴하면 그냥 204

---

## 5. 합주실 (Phase 3)

밴드(`BID`)와 그 밴드 멤버 토큰(`LEAD` 또는 `MEMBER`)이 필요하다. 등록·수정·삭제는 **밴드 멤버 누구나** 가능.

```bash
# 등록 → 201. name 만 필수. address 주면 지오코딩 시도(로컬은 키 없어 lat/lng = null)
curl -s -XPOST $B/api/v1/bands/$BID/rooms -H "Authorization: Bearer $LEAD" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Sound Box A","address":"Seoul Mapo-gu Wausan-ro 1","phone":"02-000-0000","memo":"parking ok"}'
RID=<응답 data.id>

curl -s $B/api/v1/bands/$BID/rooms          -H "Authorization: Bearer $LEAD"   # 목록 (usageCount 내림차순)
curl -s $B/api/v1/bands/$BID/rooms/$RID     -H "Authorization: Bearer $LEAD"   # 상세

# 수정 (PUT = 전체 교체). 보내지 않은 필드는 비워진다
curl -s -XPUT $B/api/v1/bands/$BID/rooms/$RID -H "Authorization: Bearer $LEAD" \
  -H 'Content-Type: application/json' -d '{"name":"Sound Box A (renamed)","memo":"updated"}'

# 삭제 (소프트) → 204
curl -s -o /dev/null -w '%{http_code}\n' -XDELETE $B/api/v1/bands/$BID/rooms/$RID \
  -H "Authorization: Bearer $LEAD"
```

확인 포인트
- 네이버 키 없이 등록 → `201`, `lat`/`lng` = `null`, `address` 는 저장됨
- 같은 밴드에 같은 이름 재등록 → `409 ROOM_NAME_DUPLICATED`
- 삭제 후 그 이름으로 다시 등록 → `201` (소프트 삭제라 이름 재사용 가능)
- 삭제한 방 조회·수정 → `404 ROOM_NOT_FOUND`
- **낯선이(다른 밴드 멤버·비멤버)가 목록 조회** → `403 NOT_BAND_MEMBER`
- **다른 밴드의 roomId 를 자기 밴드 경로로 조회** → `404 ROOM_NOT_FOUND`
- 수정 시 주소를 바꾸면 좌표를 다시 계산(키 없으면 `null` 로 비워짐), 주소 안 바꾸면 지오코딩 호출 안 함
- 네이버 키를 넣고 재기동하면 등록/수정 시 `lat`/`lng` 가 실제 좌표로 채워진다

---

## 6. 알아두면 좋은 것

| 항목 | 값 / 동작 |
|---|---|
| access 토큰 수명 | 30분 (지나면 `401 ACCESS_TOKEN_EXPIRED` → refresh 로 갱신) |
| refresh 토큰 수명 | 14일 (쓸 때마다 갱신되는 슬라이딩) |
| 레이트리밋 | 인증 20/분/IP · 초대참여 10/분/계정 + 20/분/IP · 지오코딩 20/분/계정 |
| 카카오 미설정 | `/api/v1/auth/kakao` → `503`, 나머지는 정상 |
| 네이버 미설정 | 합주실은 저장되나 `lat`/`lng` = `null` |
| 공통 응답 형식 | 성공 `{"success":true,"data":...}` / 실패 `{"success":false,"error":{"code":"...","message":"..."}}` |
| Swagger | http://localhost:8080/swagger-ui.html (로컬·docker 프로파일에서만, 운영 `prod` 프로파일은 비공개) |
| DB 직접 보기 | `docker compose exec postgres psql -U bandapp -d bandapp` → `\dt`, `select * from bands;` 등 |
| Redis 보기 | `docker compose exec redis redis-cli` → `keys *` (refresh 세션·레이트리밋 카운터) |
| 상태 초기화 | `docker compose down -v && docker compose up -d` (전체) / `docker compose restart redis` (레이트리밋·세션만) |

---

## 모르는 것 / 이상한 것

여기 안 적힌 동작이나 예상과 다른 응답이 나오면 그 요청·응답을 그대로 캡처해서 물어보면 된다.
