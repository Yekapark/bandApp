# Phase 1 — 사람이 해야 할 잔여 작업

> 코드 구현은 끝났고 로컬(`docker compose`) 수동 검증도 통과했다.
> 아래는 **사람이** 해야 하는 일이다. 순서대로 하면 된다.

---

## A. 지금 바로 (보안)

### A-1. 카카오 API 키 재발급 ⚠️ 중요

계획 논의 중 채팅창에 붙여넣은 REST API 키와 Admin 키는 **폐기하고 새로 발급**한다.
특히 Admin 키는 카카오 API를 무제한 호출할 수 있는 마스터 키다.

1. https://developers.kakao.com → 내 애플리케이션 → 해당 앱
2. 앱 설정 → 앱 키 → **REST API 키 재발급**
3. 앱 설정 → 앱 키 → **Admin 키 재발급**
4. 새 값을 `.env`에만 적는다 (`.env`는 git에 올라가지 않음). 코드/문서/채팅에 붙이지 않는다.

> 참고: 이 백엔드는 REST API 키를 실제로 쓰지 않는다(사용자 정보 조회는 사용자 토큰,
> 연결 해제는 Admin 키). 필요한 건 **`KAKAO_APP_ID`** 와 **`KAKAO_ADMIN_KEY`** 두 개다.

### A-2. `KAKAO_APP_ID` 확인

카카오 개발자 콘솔 → 내 애플리케이션 → 앱 설정 → **요약 정보**의 "앱 ID"(숫자).
REST API 키와 다른 값이다. 이 값으로 "이 토큰이 우리 앱에서 발급된 게 맞는지"를 검증한다.

---

## B. 집 PC에서 이어서 (검증)

### B-1. 환경 준비

```bash
git checkout phase-1-auth      # 이 브랜치
cp .env.example .env           # 없으면
# .env 편집: JWT_SECRET 은 아무 32자 이상 문자열로. (운영은 openssl rand -base64 48)
#            KAKAO_APP_ID, KAKAO_ADMIN_KEY 는 A에서 받은 값
```

### B-2. 이메일 로그인 전 과정 (카카오 키 없이도 가능)

`docs/progress/phase-01-auth.md` 의 **5. 직접 확인하는 법 → 방법 A** 를 그대로 따라 한다.
기대 결과: `201 → 200 → 401 → 200 → 401 → 204 → 401`.

### B-3. 카카오 실연동 (키 채운 뒤)

1. `docker compose up --build -d`
2. 카카오 로그인 토큰 얻기 — 카카오 개발자 콘솔의 도구 → "토큰 정보 보기"/REST API 테스트,
   또는 카카오 로그인 문서의 토큰 발급 도구로 테스트용 access token 을 하나 만든다.
3. ```bash
   curl -s -X POST http://localhost:8080/api/v1/auth/kakao \
     -H 'Content-Type: application/json' \
     -d '{"accessToken":"<카카오 access token>"}'
   ```
   → 200, `newUser: true`, 우리 토큰이 나오면 성공.
4. 그 토큰(우리 accessToken)으로 탈퇴:
   ```bash
   curl -s -X POST http://localhost:8080/api/v1/users/me/withdraw \
     -H "Authorization: Bearer <우리 accessToken>" -H 'Content-Type: application/json' -d '{}'
   ```
   → 204. 이후 **카카오 계정 설정 → 연결된 서비스**에서 이 앱 연결이 사라졌는지 확인.

### B-4. CI 확인

`phase-1-auth` 브랜치를 push 하고 PR을 열면 GitHub Actions가 자동 테스트를 돌린다.
**자동 테스트 통과 여부는 CI 결과로 판정한다**(로컬 `./gradlew test`는 이 환경에서 실패함).
초록불이면 Phase 1 완료 기준 충족.

### B-5. 완료 처리

- `docs/progress/phase-01-auth.md` 의 **8. 커밋 · CI** 에 커밋 해시와 CI 링크 채우기
- `docs/progress/README.md` 문서 목록 표의 Phase 1 행을 `✅ 완료`로, 링크 걸기
  (이미 링크는 걸어뒀으니 상태만 확인)
- PR 머지

---

## C. 배포 전까지 (지금 안 해도 됨, 잊지 말 것)

- **카카오 비즈앱 검수** — 이메일 등 개인정보 항목을 받으려면 필요할 수 있다.
  검수 전에는 카카오가 이메일을 안 줄 수 있어서, 지금 코드는 **이메일 없이도 가입되게**
  만들어져 있다. (`BACKLOG.md` 1.6)
- **개인정보처리방침 / 이용약관 웹페이지** — 탈퇴 시 3개월 보관 후 파기 정책을 여기에 명시.
  (`BACKLOG.md` 1.3)
- **운영 `JWT_SECRET`** — 서버 환경변수로만. `.env.example` 의 값은 로컬 전용이다.
- 로그인 레이트리밋 — Phase 2에서 초대코드 레이트리밋과 함께 붙일 예정.
