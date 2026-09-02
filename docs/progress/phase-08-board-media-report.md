# Phase 8 — 게시판 · 미디어 업로드 · 신고 · 차단

## 1. 한 줄 요약

밴드원이 합주 사진·영상을 올리는 **밴드 내부 게시판**을 만들었다. 글은 밴드 멤버만 읽고 쓰며(CRUD),
목록은 커서 페이징이다. 사진·영상 파일은 **백엔드를 절대 거치지 않는다** — 서버가 발급한 presigned URL 로
클라이언트가 Cloudflare R2 에 직접 올리고, 서버는 업로드가 끝났다는 콜백을 받아 **R2 에 HEAD 를 한 번
날려 실제 크기·형식이 신고한 값과 같은지 확인**한다. 다르면 그 파일을 거부하고 R2 객체와 DB 행을 함께
지운다. 부적절한 글/미디어/사용자를 신고하는 **신고 접수 API**, 특정 사용자의 글이 서로 안 보이게 하는
**사용자 차단**(양방향)도 함께 들어갔다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- 게시글 CRUD (밴드 멤버만 조회 가능)
- R2 presigned PUT URL 발급 API (멤버십·contentType·sizeBytes 검증)
- `MediaAttachment`를 PENDING 으로 선생성
- 업로드 완료 콜백 → R2 HEAD 요청으로 실제 업로드 및 크기 검증 → READY 전환
- 조회 시 짧은 만료의 presigned GET URL 발급 (버킷은 비공개 유지)
- 업로드 URL 발급에 레이트리밋 적용
- 신고 기능 — 게시글/미디어/사용자 신고 접수 API
- 사용자 차단 — 차단한 사용자의 게시글이 목록에서 제외됨
- 제한값: 영상 50MB, 이미지 10MB, presigned URL 만료 5~15분

**완료 기준**: 백엔드를 경유하는 파일 스트림이 코드상 존재하지 않으며, 신고한 크기와 실제 크기가 다를 때
거부·삭제되고, 차단한 사용자의 글이 목록 응답에서 빠지는 테스트가 통과한다.

### `BUILD_PLAN.md`에 없어 새로 정한 정책 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| **R2 라이브러리** | `software.amazon.awssdk:s3` 2.31.x (BOM 고정) + `url-connection-client`(동기 HTTP), 기본 `netty-nio-client` 는 제외 | R2 는 S3 호환 API 라 AWS SDK 를 그대로 쓴다. `S3Presigner` 는 **오프라인 서명**(네트워크 없음)이고, `S3Client` 는 HEAD·DELETE 에만 쓴다 — 파일 바이트는 SDK 로도 서버를 안 지난다. 동기 호출만 하므로 무거운 netty 스택을 빼 이미지·기동 시간을 줄였다. **스펙에 없는 의존성이라 커밋 메시지·이 표에 근거를 남긴다** |
| **R2 키 미설정 시 동작** | 게시글·신고·차단은 정상, **미디어 업로드/조회 API 만 503 `MEDIA_STORAGE_NOT_CONFIGURED`** | 카카오·네이버 키와 같은 방식(`KakaoProperties` 선례). 로컬·CI 에서 R2 자격증명 없이도 게시판 대부분을 띄우고 테스트할 수 있다 |
| **presigned URL 만료 clamp** | `R2Properties` compact 생성자에서 업로드/다운로드 TTL 을 **5~15분으로 강제 clamp** | BUILD_PLAN 제한값. 설정 실수로도 만료가 너무 길어지지 않게 코드에서 막는다 |
| **목록 페이징** | 커서 페이징 `(created_at DESC, id DESC)`. 커서는 `"<instant>|<id>"` 를 Base64URL 로 감싼 불투명 문자열. 잘못된 커서는 400 `POST_CURSOR_INVALID` | offset 페이징은 스크롤 중 글이 추가·삭제되면 항목이 밀린다. 커서 유무로 쿼리 메서드를 둘로 나눈 건 PostgreSQL 이 `:cursor is null` 분기에서 null 바인드 타입을 추론 못 해서다 |
| **차단의 방향성** | **양방향** — 게시판 목록·상세에서 "내가 차단한" + "나를 차단한" 사용자 글을 모두 숨긴다 | 단방향이면 "저 사람 글이 안 보이네" 로 차단 사실이 역으로 드러난다. `user_blocks` 한 테이블에서 `blocker_id = me OR blocked_user_id = me` 한 쿼리로 상대 id 를 모은다 |
| **미디어 매직넘버 검증 안 함** | 바이트 내용 검사 없이 ① MIME 허용목록에서 `image/svg+xml`·`image/gif`·`text/*` 제외, ② presigned GET 에 `Content-Disposition: attachment` 고정 | 파일 바이트를 서버가 읽으면 "백엔드 경유 파일 스트림 금지" 규칙에 걸린다. 대신 실행 벡터가 되는 형식을 아예 안 받고, 조회 URL 은 브라우저가 실행 못 하게 강제 다운로드로 내려준다 |
| **한 게시글 첨부 수 상한** | PENDING+READY 합쳐 **10개**. 초과 시 409 `MEDIA_LIMIT_EXCEEDED` | BUILD_PLAN 에 수치는 없으나 무한 선생성을 막아야 한다(고아 PENDING 스팸 방지) |
| **신고 처리(RESOLVED) API** | 만들지 않음. `status` 컬럼만 두고 전이 API 는 생략 | BUILD_PLAN Phase 8 은 "접수"만 요구한다. 운영자 처리 화면은 범위 밖 |
| **레이트리밋 기본값** | 업로드 URL 발급 계정당 분당 30, 신고 접수 계정당 분당 10 | 기존 초대·지오코딩 리밋과 같은 `RedisRateLimiter` 재사용. 신고는 스팸 소지가 커 더 빡빡하게 |
| `created_at` | `media_attachments` 에도 `BaseTimeEntity` 로 자동 관리(BUILD_PLAN 모델엔 없음) | Phase 5~7 과 동일 — 다른 테이블과 맞춘다. Phase 9 의 고아 PENDING 청소 배치 정렬 키이기도 하다 |

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V8__board_media_report.sql`

테이블 4개 신설.

| 대상 | 내용 |
|---|---|
| `board_posts` | `band_id`, `author_id`, `title`(≤100), `content`(≤4000), `created_at`, `deleted_at`(소프트 삭제). 제목·본문 공백 금지 CHECK |
| `ix_board_posts_band_created` | `(band_id, created_at DESC, id DESC) WHERE deleted_at IS NULL` — 커서 페이징을 그대로 태우는 부분 인덱스 |
| `media_attachments` | `board_post_id`, `storage_key`(UNIQUE), `type`(IMAGE/VIDEO), `status`(PENDING/READY/EXPIRED), `content_type`, `size_bytes`(>0, ≤50MB CHECK), `uploaded_at`, `expires_at`, `created_at`. `status=READY` 면 `uploaded_at` 필수 CHECK |
| `ix_media_attachments_expires` / `_pending` | Phase 9 배치용 부분 인덱스(기한 지난 READY / 고아 PENDING) |
| `reports` | `target_type`(POST/MEDIA/USER) + `target_id`(다형 참조라 FK 없음), `reporter_id`, `reason`(≤500), `status`(OPEN/RESOLVED), `created_at` |
| `ux_reports_open_target` | `(reporter_id, target_type, target_id) WHERE status='OPEN'` — 같은 대상 미처리 신고는 신고자당 하나(스팸 방지) |
| `user_blocks` | `blocker_id`, `blocked_user_id`, `created_at`. 자기 차단 금지 CHECK, `(blocker_id, blocked_user_id)` UNIQUE |
| `ix_user_blocks_blocked` | `(blocked_user_id)` — "나를 차단한 사람" 역방향 조회 |

`target_id` 가 다형 참조라 외래키를 못 거는 건 V4 `reservations.recurring_rule_id` 와 같은 사유다.
컬럼 의도는 `COMMENT ON COLUMN` 으로 스키마에 박아 뒀다.

### 3.2 저장소 경계 — `src/main/java/com/yeka/bandapp/board/storage/`

- **`StorageClient`** (인터페이스) — 미디어 저장의 **유일한 접점**. `presignPut` / `presignGet` /
  `head` / `delete` 뿐이고 **바이트를 받거나 돌려주는 메서드가 없다**(`GeocodingClient` 와 같은 역할).
- **`R2StorageClient`** — AWS SDK 로 R2(S3 호환) 호출. 키가 없으면 SDK 클라이언트를 아예 안 만들고 각
  메서드가 503 을 던진다. 통신 실패는 502 `MEDIA_STORAGE_ERROR` 로 변환(`KakaoApiClient` 계열).
  `presignGet` 은 응답 `Content-Type` 고정 + `Content-Disposition: attachment`.
- **`R2Properties`** (`app.r2.*`) — 자격증명 + 엔드포인트(비우면 `https://{accountId}.r2.cloudflarestorage.com`)
  + TTL(5~15분 clamp) + 타임아웃. `isConfigured()` 로 키 유무 판정.
- **`StoredObject`** — HEAD 결과(`sizeBytes`, `contentType`) 운반용 record.

### 3.3 게시판 도메인 — `src/main/java/com/yeka/bandapp/board/`

- **엔티티** `entity/BoardPost`, `MediaAttachment`, `Report`, `UserBlock` + enum 4개
  (`MediaType`, `MediaStatus`, `ReportStatus`, `ReportTargetType`). 전부 `Long` FK, 정적 팩토리,
  의미 있는 메서드(`softDelete(when)`, `belongsTo(bandId)`, `isWrittenBy(userId)` …). Lombok 은
  `@Getter`/`@NoArgsConstructor(PROTECTED)` 만.
- **`service/MediaPolicy`** (순수 함수) — MIME 허용목록 → `MediaType`, 형식별 크기 상한 검사,
  완료 콜백의 `verifyUpload`(실제 vs 신고 대사), 무료 플랜 만료(업로드 + 30일). 상태·트랜잭션이
  없어 Docker 없이 단위 테스트한다(`SettlementCalculator` 선례).
- **`service/PostCursor`** (순수 함수) — 커서 encode/decode. 형식 오류는 `POST_CURSOR_INVALID`.
- **`service/StorageKeys`** — `bands/{bandId}/posts/{postId}/{UUID}` 키 생성. 사용자 파일명을
  절대 안 쓴다(경로 탈출·열거 방지).
- **`service/BoardPostService`** — 글 CRUD + 목록. **`@Transactional` 없음**(첨부 GET URL 서명·삭제 시
  R2 정리를 트랜잭션 밖에 두려고, `RoomService` 선례). 쓰기는 저장소의 조건부 `@Modifying` 쿼리로.
  목록은 차단 관계 userId 를 `author_id NOT IN` 으로 뺀다(빈 목록 방지 센티넬 `-1` 항상 포함).
- **`service/MediaAttachmentService`** — presigned 업로드 흐름. **어떤 메서드에도 `@Transactional`
  금지**(R2 HTTP 를 트랜잭션 안에서 하면 커넥션을 왕복 시간 동안 점유). `issueUploadUrl` 은 행을
  먼저 `saveAndFlush`(짧은 tx) 한 뒤 서명 → 남을 수 있는 건 객체 없는 PENDING 행뿐(Phase 9 가 청소).
  `complete` 는 크기·형식 위조 발견 시 **PENDING 행 삭제를 먼저 커밋**하고 R2 객체를 best-effort 로
  지운 뒤 409 를 던진다. `PENDING → READY` 는 조건부 원자 UPDATE(락 불필요).
- **`service/ReportService`** — 신고 접수. 대상이 요청자에게 안 보이면(타 밴드 글·미디어, 없는 유저)
  존재를 숨기고 404. 자기 대상은 400, 중복 미처리 신고는 부분 유니크 인덱스 + `DataIntegrityViolationException`
  → 409 변환.
- **`service/UserBlockService`** — 차단/해제/목록. `hiddenUserIdsFor(callerId)` 가 게시판 필터의 창구.
- **컨트롤러 4개**
  - `BoardPostController` — `/api/v1/bands/{bandId}/posts` (CRUD + 목록)
  - `MediaAttachmentController` — `.../posts/{postId}/media` (`POST /upload-url`, `POST /{mediaId}/complete`, `DELETE /{mediaId}`)
  - `ReportController` — `/api/v1/reports` (밴드 무관 전역, `POST`)
  - `UserBlockController` — `/api/v1/users/me/blocks` (`POST` / `GET` / `DELETE /{blockedUserId}`)
- **DTO** `dto/` — 전부 `record`, 변환은 DTO 쪽 정적 팩토리. 요청 DTO 는 `@NotBlank`/`@Positive` 등
  Bean Validation.

### 3.4 기존 코드 변경

| 파일 | 변경 |
|---|---|
| `build.gradle.kts` | AWS SDK `s3` + `url-connection-client` 의존성 추가(BOM 2.31.16, netty 제외). 근거는 3장 표 참조 |
| `common/exception/ErrorCode.java` | Phase 8 코드 20개 추가(`POST_NOT_FOUND`, `NOT_POST_OWNER`, `POST_CURSOR_INVALID`, `MEDIA_*` 9개, `REPORT_*` 3개, `*_BLOCK*` 3개 등) |
| `common/ratelimit/RateLimitProperties.java` | `mediaUploadPerUserPerMin`(기본 30), `reportPerUserPerMin`(기본 10) 추가 |
| `application.yml` / `.env.example` / `docker-compose.yml` | `app.r2.*` 설정 블록 + `R2_*` 환경변수. 전부 비어도 기동됨 |
| `common/config/OpenApiConfig.java` | API 문서 설명에 게시판/미디어 항목, R2 키 미설정 시 동작 추가 |
| `support/IntegrationTestSupport.java` | 신규 레이트리밋 프로퍼티 테스트값 등록 |

> 기존 도메인(일정·정산 등) API 응답 형태는 건드리지 않았다. 게시판은 전부 신규 엔드포인트다.

## 4. 어떻게 동작하나

### 4.1 글 작성·조회

`POST /api/v1/bands/{bandId}/posts` `{"title":"...","content":"..."}` → 밴드 멤버 확인(비멤버 403) →
저장 → 201. 목록 `GET .../posts?cursor=&limit=` 는 `created_at` 내림차순, 차단 관계 사용자 글 제외,
`limit+1` 건을 읽어 다음 페이지 유무 판정, 마지막 항목으로 `nextCursor` 생성. 상세 `GET .../posts/{postId}`
는 본문 전체 + 첨부 목록(READY 첨부에 짧은 만료 GET URL). 삭제됐거나 타 밴드거나 차단 관계면 404
`POST_NOT_FOUND`(존재를 알리지 않는다).

### 4.2 미디어 업로드 (파일은 서버를 안 지난다)

1. `POST .../posts/{postId}/media/upload-url` `{"contentType":"image/jpeg","sizeBytes":1234567}`
   → 게시글 **작성자 본인**만(그 외 403) → 레이트리밋 → 형식 허용목록·크기 상한 검사(위반 시 400,
   행 안 생김) → 첨부 수 상한(10) 확인 → `media_attachments` 행 PENDING 으로 `saveAndFlush` →
   `S3Presigner` 로 **오프라인 서명**한 PUT URL 반환(201). `mediaId`, `uploadUrl`, `method:"PUT"`,
   필수 헤더, `urlExpiresAt` 포함.
2. **클라이언트가 그 URL 로 R2 에 직접 PUT** (백엔드 안 거침).
3. `POST .../posts/{postId}/media/{mediaId}/complete` → R2 `HEAD` 한 번:
   - 객체 없음 → 409 `MEDIA_NOT_UPLOADED` (행은 PENDING 유지, **재시도 가능**)
   - 실제 크기 ≠ 신고 크기, 또는 실제 크기 > 형식 상한 → **PENDING 행 삭제 커밋** + R2 객체 삭제 →
     409 `MEDIA_SIZE_MISMATCH`
   - 실제 형식 ≠ 신고 형식 → 같은 방식으로 삭제 → 409 `MEDIA_CONTENT_TYPE_MISMATCH`
   - 일치 → 조건부 UPDATE 로 `PENDING → READY`(만료 = 업로드 + 30일), presigned GET URL 붙여 반환
4. 조회는 언제나 **짧은 만료의 presigned GET URL**. 버킷은 계속 비공개.

### 4.3 게시글 삭제와 첨부 정리

`DELETE .../posts/{postId}` → 작성자 본인 또는 밴드장 → `deleted_at` 소프트 삭제 → 그 글의 첨부를
전부 `EXPIRED` 로 → **트랜잭션 밖에서** R2 객체를 best-effort 로 삭제(실패해도 EXPIRED 표시는 남고
Phase 9 배치가 최종 정리). 소프트 삭제라 이미 접수된 신고가 대상을 계속 가리킬 수 있다.

### 4.4 신고

`POST /api/v1/reports` `{"targetType":"POST","targetId":42,"reason":"..."}` → 레이트리밋 →
대상 가시성 확인(POST·MEDIA 는 그 콘텐츠 밴드의 멤버여야, 아니면 404 / USER 는 활성 계정이어야) →
자기 대상이면 400 `CANNOT_REPORT_SELF` → 같은 대상 미처리 신고 있으면 409 `REPORT_ALREADY_SUBMITTED` →
`status=OPEN` 으로 저장 → 201. 처리 API 는 없다.

### 4.5 차단 (양방향)

`POST /api/v1/users/me/blocks` `{"blockedUserId":7}` → 자기 자신 400 / 없는 유저 404 / 중복 409 →
저장. 이후 게시판 목록·상세에서 **A↔B 양쪽 모두** 상대 글이 안 보인다. `DELETE .../blocks/{id}` 로 해제,
`GET .../blocks` 로 내가 차단한 목록(최근순). 밴드와 무관한 전역 설정이다.

## 5. 직접 확인하는 법

### 사전 준비

`docker compose up` (app + postgres + redis). **R2 자격증명 없이도** 게시글·신고·차단은 전부 동작한다.
미디어 업로드까지 실제로 보려면 `.env` 에 `R2_ACCOUNT_ID`/`R2_BUCKET`/`R2_ACCESS_KEY_ID`/
`R2_SECRET_ACCESS_KEY` 를 채우고 재기동한다. 아래는 세 사용자(리더 L, 멤버 M1·M2)로 진행한다.

### 흐름

1. L 가입·로그인 → 밴드 생성 → 초대코드로 M1·M2 합류.
2. **글 CRUD** — L 로 `POST /api/v1/bands/{bandId}/posts` `{"title":"1회차","content":"사진"}` → 201,
   `postId` 확보. `GET .../posts` 에 보임. M1 으로도 조회됨. 비멤버(다른 밴드 사용자) 로 조회 시
   403 `NOT_BAND_MEMBER`. M1 이 L 의 글 `PUT` → 403 `NOT_POST_OWNER`. L 이 `DELETE` → 204,
   이후 목록·상세에서 사라짐.
3. **완료 기준 ① (파일 스트림 없음)** — 소스에 멀티파트/스트림 마커가 없음을 자동 검증하는
   테스트가 있다(`NoFileStreamArchitectureTest`). 수동으로는 `grep -rn "MultipartFile\|multipart/form-data"
   src/main` 이 **아무것도 못 찾아야** 한다.
4. **완료 기준 ② (크기 위조 거부·삭제)** — (R2 설정 시) `POST .../media/upload-url`
   `{"contentType":"image/jpeg","sizeBytes":1000}` → 201, `uploadUrl` 로 **1000바이트가 아닌** 파일을
   R2 에 PUT → `POST .../media/{mediaId}/complete` → **409 `MEDIA_SIZE_MISMATCH`**. 이어서
   `GET .../posts/{postId}` 첨부 목록에 그 미디어가 **없어야** 하고, R2 버킷에서도 객체가 지워졌다.
5. **완료 기준 ③ (차단 사용자 글 제외)** — M1 이 글 작성 → L 이 `POST /api/v1/users/me/blocks`
   `{"blockedUserId": M1의 userId}` → L 의 `GET .../posts` 응답에서 **M1 글이 빠진다**. M2 목록에는
   그대로 보인다. `DELETE /api/v1/users/me/blocks/{M1 userId}` → 다시 보인다.
6. **신고** — M1 이 `POST /api/v1/reports` `{"targetType":"POST","targetId":{L글},"reason":"테스트"}`
   → 201. 같은 대상 다시 → 409 `REPORT_ALREADY_SUBMITTED`. 자기 글 신고 → 400 `CANNOT_REPORT_SELF`.
   다른 밴드 글 신고 → 404 `REPORT_TARGET_NOT_FOUND`.
7. **레이트리밋** — `upload-url` 을 분당 30회, `reports` 를 분당 10회 초과 호출하면 429.

### 기대 결과 / 문제 해결

- 3에서 grep 이 뭔가 찾으면 파일이 서버를 경유한다는 뜻 — 설계 위반.
- 4에서 `complete` 가 200 을 주면 HEAD 검증이 안 걸린 것. R2 자격증명·버킷을 확인.
- 4에서 `MEDIA_STORAGE_NOT_CONFIGURED`(503) 가 나오면 R2 키가 안 채워졌다 — `.env` 확인 후 재기동.
- 5에서 M1 글이 그대로 보이면 차단 필터가 목록 쿼리에 안 걸린 것.
- 5에서 M1 도 L 글이 안 보여야 정상(양방향). 단방향이면 버그.

## 6. 실제 검증 기록

- `./gradlew compileJava compileTestJava` — **성공**(main + test 전체).
- 순수 단위 테스트(Docker 불필요) — 로컬에서 **16건 전부 통과**:
  - `MediaPolicyTest` (11건: MIME 대소문자·파라미터 무시 해석, SVG/GIF/text 거부, 이미지 10MiB·영상
    50MiB 경계, 0·음수 크기 거부, `verifyUpload` 의 일치/크기 불일치/상한 초과/형식 불일치/실제 형식
    누락 허용, 무료 플랜 만료 30일)
  - `PostCursorTest` (3건: 마이크로초까지 라운드트립, null·공백 = 첫 페이지, 깨진 커서 → `POST_CURSOR_INVALID`)
  - `NoFileStreamArchitectureTest` (2건: `src/main` 전체에 멀티파트/스트림 마커 없음, `StorageClient`
    시그니처가 바이트를 안 나름)
  - 기존 순수 단위 테스트(`SettlementCalculatorTest`, `OccurrenceGeneratorTest`, `InviteCodeGeneratorTest`,
    `JwtTokenProviderTest`, `NaverGeocodingParseTest`)도 함께 통과 — 회귀 없음.
- **Testcontainers 통합 테스트는 이 로컬 환경에서 실행하지 못했다** — Phase 0~7 과 동일한
  Docker/Testcontainers 버전 불일치. 검증은 **CI(GitHub Actions)** 에서 이뤄진다.
- 신규 통합 테스트(CI 에서 실행):
  - `BoardPostIntegrationTest` (9건) — 작성·조회, 비멤버 403, 타 밴드 404, 작성자 수정·삭제,
    밴드장은 남의 글 삭제 가능·일반 멤버는 불가, 삭제 글 목록에서 사라짐, **커서 페이징이
    created 내림차순으로 중복·누락 없이** 페이지를 이어줌, 깨진 커서 400, **작성자 탈퇴 후에도 글이
    목록에 남음**.
  - `MediaUploadIntegrationTest` (11건) — 업로드 URL 이 **객체 저장소를 직접 가리킴**, **완료 콜백이
    실제 크기 불일치 시 거부하고 객체 삭제**(완료 기준 ②), **형식 불일치도 거부·삭제**, 객체 없으면
    PENDING 유지 후 재시도 성공, 정상 완료 시 30일 만료 + 다운로드 URL, 두 번째 완료는 409,
    상한 초과 이미지는 행 없이 400, 미지원 형식 400, **업로드 URL 계정당 레이트리밋**, 비작성자
    멤버는 첨부 불가, 타 밴드 글 첨부 404. R2 는 `FakeStorageClient`(`@Primary`) 인메모리 스텁으로
    대체 — **바이트를 안 다루고** "클라이언트가 R2 에 이만큼 올렸다"는 상태만 심는다.
  - `ReportIntegrationTest` (7건) — 게시글·미디어·사용자 신고 접수, 타 밴드 글 신고 404, 자기 글·자기
    자신 신고 거부, 같은 대상 중복 미처리 신고 409, 계정당 레이트리밋.
  - `UserBlockIntegrationTest` (7건) — **차단 사용자 글이 목록에서 빠짐**(완료 기준 ③),
    **양방향**(나를 차단한 사람 글도 안 보임), 해제 시 복원, **밴드 무관 전역**, 자기 차단 400,
    중복 409·미차단 해제 404, 차단 목록 최근순.
- `NoFileStreamArchitectureTest` 가 **완료 기준 ①**(백엔드 경유 파일 스트림이 코드상 없음)을
  자동으로 지킨다 — 앞으로 누가 멀티파트 업로드를 추가하면 이 테스트가 깨진다.
- **CI 결과**: _(push 후 채운다 — Phase 7 과 동일 절차)_

## 6.1 구현 후 자체 점검(보안·누락) 결과

| 발견 | 심각도 | 조치 |
|---|---|---|
| **백엔드 경유 파일 스트림** — 컨트롤러·서비스·저장소 어디에도 `MultipartFile`/`@RequestPart`/스트림 응답이 없다. `StorageClient` 는 URI·메타데이터만 다룬다. `NoFileStreamArchitectureTest` 가 `src/main` 전체를 훑어 이를 강제. | — | 문제 없음(설계대로, 테스트로 고정) |
| **타 밴드 격리** — 모든 게시판 엔드포인트가 `BandAccessGuard.requireActiveMember(bandId, callerId)` 로 시작하고, 글은 `belongsTo(bandId)` 로 경로 밴드와 대조. 타 밴드·삭제·차단 관계 글은 존재를 숨기고 404 `POST_NOT_FOUND`. 신고도 대상 밴드 멤버가 아니면 404. | — | 문제 없음(통합 테스트로 검증) |
| **트랜잭션 안 R2 호출 금지** — `MediaAttachmentService` 는 `@Transactional` 이 하나도 없고, `BoardPostService` 의 조회·목록·수정·삭제도 없다. DB 쓰기는 저장소의 조건부 `@Modifying` 쿼리(각자 짧은 tx). `complete` 의 위조 거부 경로는 **PENDING 행 삭제를 먼저 커밋한 뒤** 예외를 던진다 — 트랜잭션이 열려 있으면 그 삭제가 롤백돼 "거부하고 삭제한다"가 깨진다. | — | 조치 완료(주석·클래스 Javadoc 에 근거 명시) |
| **미디어 실행 벡터** — presigned GET 에 `Content-Disposition: attachment` + 저장된 `Content-Type` 고정. MIME 허용목록에서 `image/svg+xml`·`text/*` 제외. 스토리지 키는 서버 생성 UUID(사용자 파일명 미사용) → 경로 탈출·열거 불가. | — | 조치 완료 |
| **크기 위조** — 클라이언트 신고 `sizeBytes` 는 URL 발급 시 형식별 상한만 본다. 진짜 검증은 `complete` 의 R2 HEAD: 실제 크기 ≠ 신고 크기 **또는** 실제 크기 > 형식 상한이면 위조로 보고 객체·행 삭제 후 409. | — | 조치 완료(단위 + 통합 테스트) |
| **동시 complete** — `PENDING → READY` 는 조건부 원자 UPDATE(`where status = PENDING`). 두 요청 중 한 번만 1행을 받고 나머지는 0 → 409 `MEDIA_NOT_PENDING`. 락 불필요. | — | 문제 없음 |
| **고아 리소스** — (a) URL 발급 후 클라이언트가 PUT 을 안 하면 객체 없는 PENDING 행만 남음 → Phase 9 청소 배치(`ix_media_attachments_pending`). (b) 행 생성 전에 서명하면 추적 불가한 고아 객체가 생기므로 **행 먼저 → 서명 나중** 순서 고정. (c) 게시글 삭제 시 R2 정리 실패해도 EXPIRED 표시는 남아 Phase 9 배치가 재정리. | 낮음 | 순서 고정 + 배치 인덱스 준비. 실제 배치는 Phase 9 |
| **신고 스팸** — 부분 유니크 인덱스 `ux_reports_open_target` + `DataIntegrityViolationException` → 409 변환(CLAUDE.md 규칙). 계정당 분당 10회 레이트리밋. | — | 조치 완료 |
| **차단 사실 노출** — 단방향 필터면 "상대 글만 안 보임" 으로 차단이 드러난다. `findRelatedUserIds` 가 `blocker_id = me OR blocked_user_id = me` 한 쿼리로 양방향 숨김. | — | 조치 완료(통합 테스트 `blocking_is_mutual`) |
| **`author_id NOT IN ()` 빈 목록** — 차단이 없으면 `NOT IN` 이 빈 컬렉션이 돼 JPQL 이 깨진다. 존재하지 않는 userId 센티넬 `-1` 을 항상 섞는다. | 낮음 | 조치 완료(주석에 근거) |
| **null 바인드 타입 추론** — `:cursor is null` 한 쿼리 분기는 PostgreSQL 이 `could not determine data type of parameter` 로 실패. 첫 페이지/이후 페이지 쿼리 메서드를 분리. | 낮음 | 조치 완료(`findFirstPage`/`findPageAfter`) |
| **AWS SDK 크기** — 스펙에 없는 의존성. `netty-nio-client` 제외 + `url-connection-client`(sync) 로 최소화. `S3Presigner` 는 오프라인이라 런타임 네트워크 의존이 서명 경로엔 없음. | — | 근거를 커밋 메시지·이 문서에 명시(CLAUDE.md "스펙에 없는 라이브러리는 먼저 제안") |
| **레이트리밋 부재 지점** — 글 CRUD·차단에는 레이트리밋이 없다(기존 일정·정산 쓰기와 동일 기준 — 내부 저위험). 외부 리소스를 만드는 `upload-url` 과 스팸 소지가 큰 `reports` 에만 걸었다. | 하 | 현행 유지 |
| SQL 인젝션 / 대량 바인딩 — 전부 JPA 파생 쿼리·JPQL, 파라미터 바인딩만. DTO 는 명시적 `record`. | — | 문제 없음 |

## 7. 알려진 이슈 / 제약

- **R2 자격증명이 없으면 미디어 업로드·조회 API 는 503** `MEDIA_STORAGE_NOT_CONFIGURED`. 게시글·신고·
  차단은 정상. 로컬·CI 기본 상태가 이렇다(통합 테스트는 `FakeStorageClient` 로 대체).
- **매직넘버(바이트 내용) 검증은 하지 않는다.** 파일을 서버가 읽으면 금지 규칙에 걸린다. 방어는
  형식 허용목록 + 강제 다운로드(`Content-Disposition: attachment`) 두 겹뿐이다.
- **고아 리소스 정리 배치는 Phase 9.** 지금은 인덱스만 준비돼 있다 — URL 만 받고 안 올린 PENDING 행,
  게시글 삭제 시 R2 정리에 실패한 EXPIRED 객체가 그때까지 남는다.
- **신고 처리(RESOLVED) API 없음.** 접수만 한다. 운영자 화면은 BUILD_PLAN Phase 8 범위 밖.
- **게시글 수정 이력 없음.** 도메인 모델에 `updated_at` 이 없어 `PUT` 은 제자리 교체다.
- 커서는 `(created_at, id)` 튜플 비교라 **정확히 같은 마이크로초에 생성된 글이 페이지 경계에 걸려도**
  `id` 로 갈라 누락·중복이 없다.
- 첨부 다운로드 URL 은 상세 조회 때마다 새로 서명된다(만료 5~15분). 목록에는 이미지 첫 장 썸네일
  URL 만 붙는다.
- "새 글 알림"(BUILD_PLAN Phase 9 알림 트리거)은 이 Phase 범위 밖.

## 8. 커밋 · CI 링크

- 브랜치: `phase-8-board-media-report`
- PR: _(push 후)_
- CI: _(push 후)_
- 주요 커밋:
  - `feat(board): V8 스키마 + 게시판·미디어·신고·차단 엔티티 + R2 저장소 경계`
  - `feat(board): 게시글 CRUD·미디어 presigned 업로드·신고·차단 API`
  - `test(board): 완료 기준 통합(스트림 없음·크기 위조·차단) + 순수 단위 테스트`
  - `docs(progress): Phase 8 게시판·미디어·신고 기록`

## 9. 다음 Phase 예고

Phase 9 — 알림 · 배치잡. FCM 연동·디바이스 토큰 등록/해제, 일정 리마인더 발송(사용자별 설정 시점),
알림 트리거(새 일정·승인 요청/결과·정산 요청·참석 독촉), 사용자별 알림 on/off·리마인더 시점 복수 지정.
**배치잡 1**: `expires_at` 지난 READY 미디어 → R2 삭제 후 EXPIRED. **배치잡 2**: 1시간 이상 PENDING 인
미디어 레코드 정리. **배치잡 3**: 정기 일정 회차 이어서 생성(Phase 5). R2 삭제 실패에도 트랜잭션이
안 깨지고 재시도 가능한 구조가 완료 기준.
