# Phase 11 — 배포

## 1. 한 줄 요약

**서버 한 대에 서비스를 띄우고, 매일 자동으로 백업하고, 사고가 나면 되돌리는 장치**를 만들었다.
`main` 브랜치의 테스트가 통과하면 GitHub 이 알아서 앱 이미지를 만들어 서버에 갈아 끼우고,
매일 새벽 데이터베이스를 통째로 떠서 Cloudflare R2 에 올린다. **그 백업으로 빈 데이터베이스에
복구하는 절차를 실제로 한 번 돌려서 성공하는 것까지 확인했다** — 이게 이번 Phase 의 완료 기준이다.

실제 서버(VM)는 아직 없다. 서버가 생기면 [docs/DEPLOY.md](../DEPLOY.md) §2 를 그대로 따라가면 된다.

## 2. 이 Phase 의 목표 (`docs/BUILD_PLAN.md` 기준)

- 프로덕션용 Docker Compose 및 Nginx 설정
- Let's Encrypt 인증서 자동 갱신
- GitHub Actions 배포 파이프라인 (이미지 빌드 → GHCR → SSH 배포)
- DB 자동 백업 스크립트 — `pg_dump` 일 1회 크론 → R2 업로드, 최근 7일 보관
- 복구 절차 문서화 및 실제 복구 테스트 1회 수행

**완료 기준**: 백업 파일로부터 빈 DB 에 복구하는 절차가 실제로 성공한다. → **충족** (§5)

## 3. 무엇을 만들었나

| 파일 | 하는 일 |
|---|---|
| `docker-compose.prod.yml` | 운영용 스택 정의 — 데이터베이스·Redis·앱·Nginx·인증서 갱신기 다섯 덩어리 |
| `.env.prod.example` | 서버에 채워 넣을 설정값 견본 (비밀번호·도메인·외부 서비스 키) |
| `deploy/nginx/templates/app.conf.template` | 바깥에서 들어온 요청을 앱으로 넘기는 규칙, HTTPS 설정 |
| `deploy/nginx/proxy-headers.conf` | 앱에 넘길 헤더. **접속자 IP 위조를 막는 부분이 여기 있다** |
| `deploy/nginx/cloudflare-realip.conf` | Cloudflare 대역에서 온 요청만 `CF-Connecting-IP` 를 믿게 하는 목록 |
| `deploy/nginx/update-cloudflare-ips.sh` | 위 목록 갱신 (분기 1회) |
| `deploy/nginx/test-realip.sh` | IP 위조가 통하는지 도커로 검증 (VM 불필요) |
| `deploy/init-letsencrypt.sh` | HTTPS 인증서 최초 발급 (서버에서 딱 한 번) |
| `deploy/deploy.sh` | 새 버전 교체 + 정상 기동 확인. 실패하면 로그를 남기고 멈춘다 |
| `deploy/backup/pg-backup.sh` | 매일 데이터베이스 백업 → R2 업로드 → 7개만 남기기 |
| `deploy/backup/pg-restore.sh` | 백업에서 되돌리기. 운영을 건드리지 않는 "훈련 모드" 포함 |
| `.github/workflows/deploy.yml` | 테스트 통과 → 이미지 제작 → 서버 배포 자동화 |
| `docs/DEPLOY.md` | 위 전부를 순서대로 쓰는 법 + 복구 절차 (운영 런북) |

### 3.1 운영 스택이 로컬 개발용과 다른 점

로컬 `docker-compose.yml` 은 개발 편의를 위해 열어 둔 것이 많다. 운영은 반대로 잠근다.

| | 로컬 | 운영 |
|---|---|---|
| 앱 이미지 | 그 자리에서 빌드 | **GitHub 에서 만든 것을 내려받는다** — 저가 서버에서 빌드하면 메모리가 터진다 |
| 데이터베이스·Redis 포트 | 내 PC 에 열려 있음 | **바깥에 열지 않는다.** 컨테이너끼리만 통신 |
| Redis 비밀번호 | 없음 | **필수.** 로그인 세션과 차단 목록이 들어 있다 |
| 프로필 | `docker` | `prod` — API 문서(Swagger) 비공개, 상태 점검 응답에서 내부 정보 제거 |
| 앞단 | 없음 (직접 8080) | Nginx + HTTPS |

### 3.2 접속자 IP 위조 구멍을 닫았다 (보안, 배포 전 필수였던 항목)

`docs/BACKLOG.md` §1.9·§1.11 에 "배포 전 처리"로 두 번 올라와 있던 문제다.

**무엇이 문제였나.** 앱은 "같은 사람이 1분에 몇 번까지 시도할 수 있는지"를 IP 로 센다
(로그인 비밀번호 찍어보기, 초대코드 찍어보기, 업로드 도배 방지). 그런데 그 IP 를
`X-Forwarded-For` 라는 **요청에 아무나 적어 보낼 수 있는 값**에서 읽고 있었다. 즉 매 요청마다
다른 값을 적어 보내면 횟수 제한이 통째로 무력화된다 — 비밀번호를 무제한으로 찍어볼 수 있다는 뜻이다.

**어떻게 닫았나.** 세 겹이다.

1. **Nginx** — 바깥에서 들어온 `X-Forwarded-For` 를 **덮어쓴다**(관행적으로 쓰는 "이어붙이기"가
   아니다). 앱이 보는 값은 Nginx 가 실제로 본 접속 주소뿐이다.
2. **앱** — 톰캣의 표준 기능(`forward-headers-strategy: NATIVE`)을 켜서, **내부 네트워크의
   신뢰하는 프록시에서 온 요청의 헤더만** 해석하도록 했다. 바깥에서 앱에 직접 꽂은 위조 헤더는 무시된다.
3. **코드** — `ClientIp` 가 헤더를 직접 읽지 않고 소켓 주소만 본다. 헤더 해석은 2번이 담당하므로,
   이 함수를 쓰는 모든 자리(로그인·초대참여·업로드·신고)가 한 번에 안전해진다.

4. **Cloudflare 대역 목록** (`deploy/nginx/cloudflare-realip.conf`) — 주황 구름(프록시)을 켜면
   모든 요청이 Cloudflare 를 거쳐서 오므로, 위 1~3 만으로는 **전 사용자가 Cloudflare 주소 하나로
   뭉친다.** 그러면 남이 로그인 시도할 때 내가 막힌다. 이 목록은 "Cloudflare 대역에서 온 요청에
   한해" `CF-Connecting-IP` 를 진짜 사용자 IP 로 인정하게 해서 이걸 푼다. 바깥에서 그 헤더를
   위조해도 대역 밖이라 무시된다. 회색 구름으로 되돌려도 같은 설정이 그대로 맞다.

같이 필요해서 넣은 것: Redis 비밀번호 설정(`REDIS_PASSWORD`) — 지금까지 운영 설정에 항목 자체가 없었다.

**이 네 겹이 실제로 맞는지 도커로 검증했다** (`deploy/nginx/test-realip.sh`, VM 불필요).
Cloudflare 대역(104.16.99.0/24)과 바깥(203.0.113.0/24) 두 네트워크를 만들어 각각에서 요청을
넣고, 앱이 최종적으로 보는 IP 를 확인한다 — §5 참조.

### 3.3 HTTPS 인증서 — 왜 발급 스크립트가 따로 있나

닭과 달걀 문제가 있다. Nginx 는 켜질 때 인증서 파일을 읽는데, 없으면 아예 켜지지 않는다.
그런데 인증서를 받으려면 Let's Encrypt 가 "이 도메인이 정말 네 서버냐"를 확인하러 오는 요청을
받아 줄 웹서버가 있어야 한다. Nginx 가 없으면 발급도 안 된다.

그래서 **최초 1회만** `deploy/init-letsencrypt.sh` 가 certbot 에게 직접 80 포트를 잡게 해서
인증서를 받고, 그 다음에 Nginx 를 켠다. 이후 갱신은 자동이다 — certbot 컨테이너가 12시간마다
확인하고(만료 30일 전부터 실제 갱신), Nginx 컨테이너가 6시간마다 설정을 다시 읽어 새 인증서를
집어 든다. 다시 읽는 것은 접속이 끊기지 않는 방식이다.

### 3.4 배포 파이프라인

`main` 에 코드가 들어가면 → 기존 CI(빌드·테스트)가 돈다 → **통과했을 때만** 배포가 시작된다
(`workflow_run` 조건). 이미지를 만들어 GitHub 컨테이너 저장소(GHCR)에 올리고, SSH 로 서버에
접속해 `deploy/deploy.sh` 를 실행한다.

`deploy.sh` 는 **바꾸기 전에 백업을 한 번 뜬다.** 그리고 교체 후 5분 안에 앱이 정상이라고
응답하지 않으면 로그를 찍고 실패로 끝난다. 되돌리려면 이전 버전 태그로 같은 스크립트를 부르면 된다.

> 서버에 GitHub 비밀번호나 토큰을 심어 두지 않는다. 배포가 도는 동안만 유효한 임시 토큰을
> SSH 세션으로 넘겨 쓴다.

### 3.5 백업 — "도는데 복구가 안 되는" 사고를 막는 장치

단일 서버 구성이라 **이 백업이 사용자 데이터를 지키는 유일한 수단**이다.

매일 새벽 3시 30분(크론): 데이터베이스 덤프 → **그 자리에서 파일이 실제로 읽히는지 검증** →
R2 업로드 → 로컬·원격 각각 최근 7개만 남기고 삭제.

가운데 검증 단계가 핵심이다. 백업 사고의 대부분은 빈 파일이나 오류 메시지가 섞인 파일이 매일
조용히 쌓이다가, 정작 필요할 때 열리지 않는 경우다. 여기서는 유효하지 않으면 그 파일을 지우고
**실패로 끝내서** 크론 로그에 흔적을 남긴다.

R2 설정이 비어 있으면 로컬에만 보관하고 넘어간다(설정 안 했다고 백업 자체가 멈추지는 않는다).

## 4. 직접 확인하는 법

전부 로컬에서 확인할 수 있다. Docker Desktop 이 켜져 있어야 한다.

### 4.1 백업이 도는지

```bash
cd C:\band\bandApp
docker compose up -d                                    # 로컬 스택
MSYS2_ARG_CONV_EXCL='*' COMPOSE_FILE=docker-compose.yml ENV_FILE=.env sh deploy/backup/pg-backup.sh
```

기대 결과 — `== 검증 통과`, R2 키가 `.env` 에 있으면 업로드까지:

```
== pg_dump → ./backups/bandapp-20260905T155507Z.dump
== 검증 통과 (88K)
== R2 업로드 s3://bandapp-media-dev/db-backups/
== 백업 완료
```

> `MSYS2_ARG_CONV_EXCL='*'` 는 **윈도우 Git Bash 에서만** 필요하다. 안 붙이면 Git Bash 가
> 컨테이너 안 경로 `/backup/...` 를 `C:/Program Files/Git/backup/...` 으로 멋대로 바꿔서
> 업로드가 "파일이 없다"로 실패한다. 실제 서버(리눅스)에서는 필요 없다.

### 4.2 복구가 되는지 (운영 데이터를 건드리지 않는 훈련)

빈 데이터베이스를 새로 만들어 거기에 복구하고, 행 수가 백업 시점과 같은지 본다.

```bash
DUMP=$(ls -1 backups/bandapp-*.dump | tail -1)
ASSUME_YES=1 RESTORE_DB=restore_drill COMPOSE_FILE=docker-compose.yml ENV_FILE=.env \
  sh deploy/backup/pg-restore.sh "$DUMP"
```

기대 결과 — 마이그레이션 버전과 주요 테이블 행 수가 찍힌다. `ASSUME_YES=1` 을 빼면
`yes` 를 직접 입력해야 진행한다(실수 방지).

정리:

```bash
docker compose exec -T postgres dropdb -U bandapp restore_drill
```

### 4.3 운영 설정 파일이 문법적으로 맞는지

```bash
DOMAIN=api.example.com DB_NAME=b DB_USERNAME=b DB_PASSWORD=p REDIS_PASSWORD=r JWT_SECRET=j \
  docker compose -f docker-compose.prod.yml --env-file /dev/null config -q
```

아무것도 출력되지 않으면 통과다(변수 미설정 경고는 정상 — 견본값을 넣어 돌린 것이라 그렇다).

### 4.4 문제가 생기면

| 증상 | 원인·조치 |
|---|---|
| `pg_dump` 단계에서 멈춤 | 로컬 스택이 안 떠 있다. `docker compose up -d` 먼저 |
| `업로드 ... does not exist` | 4.1 의 `MSYS2_ARG_CONV_EXCL` 를 빠뜨렸다 (윈도우 전용 함정) |
| 복구 중 `database ... is being accessed` | 앱이 붙어 있다. 훈련 모드(`RESTORE_DB=`)를 쓰거나 `docker compose stop app` |
| `docker ... daemon is not running` | Docker Desktop 을 켠다 |

## 5. 검증 결과 — 완료 기준

**2026-09-06, 로컬 스택(마이그레이션 V13)에서 실제로 수행했다.**

| 단계 | 결과 |
|---|---|
| 백업 시점 데이터 | `users=9`, `bands=5`, `reservations=7`, `board_posts=1` |
| `pg-backup.sh` 덤프 + 자체 검증 | ✅ 88K, `pg_restore --list` 통과 |
| R2 업로드 | ✅ `s3://bandapp-media-dev/db-backups/bandapp-20260905T155507Z.dump` |
| **빈 DB(`restore_drill`)로 복구** | ✅ `flyway=13`, `users=9 / bands=5 / reservations=7 / board_posts=1` — **백업 시점과 완전 일치** |
| 운영 DB 경로 복구(스키마 드롭 → 복구 → 앱 재기동) | ✅ 앱 `healthy`, 행 수 동일 |
| 운영 compose 문법 검증 | ✅ |
| Nginx 설정 기동 검증 (`nginx -t`, 실제 컨테이너) | ✅ |
| **접속자 IP 위조 검증** (`deploy/nginx/test-realip.sh`) | ✅ 5케이스 전부 통과 (아래) |
| 백엔드 빌드·테스트 (`./gradlew build`) | (§8 결과 참조) |

→ **"백업 파일로부터 빈 DB 에 복구하는 절차가 실제로 성공한다" 충족.**

### 접속자 IP 위조 검증 상세

앱이 최종적으로 보게 되는 IP 를 그대로 돌려주는 가짜 백엔드를 두고, Cloudflare 대역과
바깥에서 각각 요청을 넣었다.

| 케이스 | 앱이 본 IP | |
|---|---|---|
| ① Cloudflare 대역에서 `CF-Connecting-IP: 9.9.9.9` | `9.9.9.9` | 진짜 사용자로 인정 ✅ |
| ② 바깥에서 `CF-Connecting-IP: 9.9.9.9` 위조 | `203.0.113.4` | 무시하고 실제 주소 ✅ |
| ③ 바깥에서 `X-Forwarded-For: 1.2.3.4` 위조 | `203.0.113.4` | 무시하고 실제 주소 ✅ |
| ④ 헤더 없음 (회색 구름 상황) | `104.16.99.4` | 소켓 주소 그대로 ✅ |
| ⑤ 바깥에서 두 헤더 동시 위조 | `203.0.113.4` | 둘 다 무시 ✅ |

**이 테스트에 실효성이 있는지도 확인했다.** `proxy-headers.conf` 를 인터넷에 흔한 예제대로
`$proxy_add_x_forwarded_for` 로 되돌린 뒤 돌리면 ③⑤ 가 `1.2.3.4, 203.0.113.4` 로 깨진다 —
위조값이 맨 앞에 오고, 톰캣은 그걸 클라이언트 IP 로 삼는다. 나중에 누가 무심코 그 예제를
가져다 붙이면 이 테스트가 잡는다.

## 6. 알려진 이슈 / 아직 안 한 것

- **실제 VM 에서는 아직 돌려 보지 않았다.** 서버가 없어서 백업·복구만 로컬에서 검증했다.
  Nginx·인증서 발급·SSH 배포는 코드로만 준비돼 있다. 서버를 잡으면 `docs/DEPLOY.md` §2 를
  따라가며 확인해야 한다.
- **Cloudflare 주황 구름(프록시)은 켜는 것으로 정했다.** 원본 서버 IP 를 감추는 이점이 무료 VM
  한 대 구성에서 크다. 켜기 전에 **Cloudflare SSL/TLS 모드를 `Full (strict)` 로** 바꿔야 한다 —
  `Flexible` 이면 무한 리다이렉트가 난다. 인증서는 회색 구름 상태에서 먼저 발급하고 켠다
  (`docs/DEPLOY.md` §1).
- **Cloudflare 대역 목록은 분기에 한 번 갱신한다** (`sh deploy/nginx/update-cloudflare-ips.sh`).
  Cloudflare 가 대역을 추가했는데 목록이 옛것이면 그 대역으로 들어온 사용자들의 IP 가 다시
  하나로 뭉쳐 레이트리밋이 오작동한다.
- **DB 를 과거로 되돌리면 그 뒤 올라간 사진·영상은 R2 에 고아로 남는다.** R2 는 서버와 별개라
  같이 죽지 않지만, 참조가 끊긴 파일을 자동으로 지우지는 않는다. 수동 확인 항목.
- **로그 로테이션 미설정.** 도커 기본 설정은 로그가 무한히 자란다. VM 배포 시
  `/etc/docker/daemon.json` 에 크기 상한을 건다.
- 마이그레이션은 되돌아가지 않는다. 스키마를 바꾼 배포를 롤백하려면 DB 복구가 함께 필요하다.
- 이번 Phase 범위 밖이지만 출시 전 남은 것 — 패키지명 `com.example.*` 교체, 릴리스 서명,
  개인정보처리방침·이용약관 URL. `docs/progress/NEXT.md` §2-A 참조.

## 7. 이 Phase 에서 정한 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| VM 에서 이미지 빌드 vs GHCR pull | **GHCR pull** | 무료 티어 ARM VM(2GB)에서 Gradle 빌드는 OOM 으로 죽는다. 빌드는 CI 가, 서버는 받아 쓰기만 |
| 배포 트리거 | CI 성공 후 `workflow_run` | 테스트를 배포 워크플로에 복제하지 않고 재사용. 테스트 실패분은 배포되지 않는다 |
| GHCR 인증 | 워크플로 `GITHUB_TOKEN` 을 SSH 로 전달 | 서버에 장기 PAT 를 심어 두지 않는다 |
| Nginx vs Caddy | **Nginx** (`BUILD_PLAN`·`DESIGN` 명시) | Caddy 였다면 인증서 발급·갱신·리로드 장치가 통째로 없어졌겠지만, 스펙 변경이라 임의로 바꾸지 않았다. 바꾸고 싶으면 별도 제안 |
| 인증서 최초 발급 | `--standalone` 1회 스크립트 | 흔히 쓰는 "가짜 인증서 만들고 → 발급 → 교체" 방식보다 단계가 적다 |
| 백업 포맷 | `pg_dump -Fc` | 자체 압축되고, 복구 시 테이블 단위 선택·병렬 복구가 된다 |
| 백업 보관 | 스크립트에서 7개 유지 | R2 수명주기 규칙으로도 되지만 콘솔 수동 설정이라 잊기 쉽다. 스크립트가 자체 완결 |
| 복구 훈련 모드 | `RESTORE_DB=` 로 별도 DB | 백업이 살아 있는지 확인하려고 운영 DB 를 날릴 이유가 없다 |
| XFF 처리 위치 | Nginx 덮어쓰기 + 톰캣 밸브 + `ClientIp` 소켓 주소 + Cloudflare 대역 목록 | 네 겹 중 하나만 어긋나도 우회되는 문제라 전부 닫았다. `ClientIp` 한 곳을 고치면 이 함수를 쓰는 모든 경로가 함께 안전해진다 |
| Cloudflare 프록시(주황 구름) | **켠다** | 원본 IP 를 감추는 이점이 무료 VM 한 대 구성에서 크다. API 라 캐시 이득은 없다. 레이트리밋이 뭉치는 부작용은 `cloudflare-realip.conf` 로 닫았고 테스트로 확인했다 |
| Cloudflare 대역 목록 관리 | 파일로 커밋 + 갱신 스크립트 | 기동할 때마다 받아 오면 Cloudflare 가 응답을 안 줄 때 서버가 못 뜬다. 갱신 스크립트도 목록이 비면 기존 파일을 그대로 둔다 |

## 8. 커밋 · CI

- 브랜치: `phase-11-deploy`
- PR: (머지 시 채운다)
- CI: (머지 시 채운다)
