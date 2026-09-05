# 배포 · 운영 런북 (Phase 11)

단일 VM + Docker Compose 구성. 이 문서 하나로 **처음 배포 → 매일 자동 배포 → 사고 복구**까지 된다.
구현 명세는 `docs/BUILD_PLAN.md`, 진행 기록은 `docs/progress/phase-11-deploy.md`.

구성 파일

| 파일 | 역할 |
|---|---|
| `docker-compose.prod.yml` | 운영 스택 (postgres · redis · app · nginx · certbot) |
| `.env.prod.example` | 운영 환경변수 견본. VM 에서 `.env.prod` 로 복사해 채운다 |
| `deploy/nginx/templates/app.conf.template` | Nginx 리버스 프록시 설정 (`${DOMAIN}` 치환) |
| `deploy/nginx/proxy-headers.conf` | 프록시 헤더. **X-Forwarded-For 를 실제 접속자로 덮어쓴다** |
| `deploy/nginx/cloudflare-realip.conf` | Cloudflare 엣지 대역 목록 + `CF-Connecting-IP` 신뢰 설정 (자동 생성물) |
| `deploy/nginx/update-cloudflare-ips.sh` | 위 목록을 Cloudflare 에서 다시 받아 갱신 (분기 1회) |
| `deploy/nginx/test-realip.sh` | 접속자 IP 판정이 위조에 안 뚫리는지 도커로 검증 (VM 불필요) |
| `deploy/init-letsencrypt.sh` | 인증서 최초 발급 (VM 에서 1회) |
| `deploy/deploy.sh` | 이미지 교체 + 헬스체크. GitHub Actions 가 SSH 로 호출 |
| `deploy/backup/pg-backup.sh` | 일 1회 pg_dump → R2 업로드 → 7개 보관 |
| `deploy/backup/pg-restore.sh` | 덤프에서 복구. 훈련 모드 있음 |
| `.github/workflows/deploy.yml` | CI 통과 → 이미지 빌드 → GHCR → SSH 배포 |

---

## 1. 사전 준비

**VM** — Oracle Cloud Always Free(ARM) 또는 저가 VPS. Ubuntu 22.04+ 기준.
메모리 2GB 이상(1GB 면 Postgres + JVM 이 빠듯하다).

```bash
# docker + compose plugin
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"   # 다시 로그인해야 적용된다

sudo mkdir -p /opt/bandapp && sudo chown "$USER" /opt/bandapp
git clone https://github.com/Yekapark/bandApp.git /opt/bandapp
```

**방화벽** — 80·443·SSH 만 연다. 앱(8080)·Postgres(5432)·Redis(6379)는
`docker-compose.prod.yml` 에서 호스트로 게시하지 않으므로 애초에 밖에서 닿지 않는다.

```bash
sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw enable
```

**DNS / Cloudflare** — `DOMAIN` 의 A 레코드를 VM 공인 IP 로 만들고, **주황 구름(Proxied)을 켠다.**

주황 구름을 켜면 서버의 진짜 IP 가 밖에 보이지 않는다. 무료 VM 한 대로 굴리는 구성에서
원본 주소가 공개되면 누가 작정하고 두들길 때 막을 수단이 없으므로, 이 이점이 크다.
DDoS 방어도 함께 붙는다.

대신 **켜기 전에 반드시 확인할 것 두 가지**가 있다.

**① Cloudflare SSL/TLS 모드를 `Full (strict)` 로.**
`Flexible` 로 두면 Cloudflare 가 원본 서버에 평문(HTTP)으로 말을 걸고, 우리 Nginx 는 그걸
HTTPS 로 돌려보내고, Cloudflare 가 다시 평문으로 오는 **무한 리다이렉트**가 된다.
`Full (strict)` 는 원본의 정식 인증서를 검증하는 모드이고, 우리는 Let's Encrypt 인증서가
있으므로 그대로 통과한다.

**② 인증서를 먼저 발급하고 나서 주황으로 켠다.**
`init-letsencrypt.sh` 는 certbot 이 80 포트를 직접 잡는 방식이라, 회색 구름 상태에서
받는 것이 확실하다. 순서: 회색으로 DNS 등록 → §2 로 인증서 발급·기동 → 주황으로 전환.
(이후 갱신은 웹루트 방식이라 주황 상태에서도 그대로 동작한다.)

**접속자 IP 는 이미 처리돼 있다.** 주황 구름을 켜면 모든 요청이 Cloudflare 를 거치므로
서버 눈에는 전 사용자가 Cloudflare 주소 하나로 보인다. 그대로 두면 IP 기준 레이트리밋
(로그인 브루트포스·초대코드 대입 방지)이 **정상 사용자끼리 서로를 막는다.**
`deploy/nginx/cloudflare-realip.conf` 가 "Cloudflare 대역에서 온 요청에 한해"
`CF-Connecting-IP` 헤더를 진짜 사용자 IP 로 인정하게 해서 이걸 푼다.
회색 구름으로 되돌려도 같은 설정이 그대로 맞다(헤더가 없으면 소켓 주소를 쓴다).

Cloudflare 가 대역을 추가하는 일이 가끔 있다. 분기에 한 번:

```bash
sh deploy/nginx/update-cloudflare-ips.sh
docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -s reload
```

설정이 맞는지는 VM 없이도 확인된다 — 도커로 Cloudflare 대역과 바깥에서 각각 요청을 넣어
위조가 통하는지 본다:

```bash
sh deploy/nginx/test-realip.sh
```

> **정적 페이지(개인정보처리방침·이용약관·초대 랜딩)는 별개 호스트로 두는 것이 좋다.**
> 그쪽은 캐시 이득이 실제로 있어서 Cloudflare Pages 무료 호스팅이 잘 맞는다.

**방화벽** 재확인 — 주황 구름을 켜도 **원본 IP 를 알아낸 사람은 직접 접속할 수 있다.**
80·443 을 Cloudflare 대역에서만 받도록 좁히면 더 안전하다(선택):

```bash
for cidr in $(curl -s https://www.cloudflare.com/ips-v4); do sudo ufw allow from "$cidr" to any port 80,443 proto tcp; done
sudo ufw delete allow 80,443/tcp
```

이렇게 잠그면 `init-letsencrypt.sh` 의 최초 발급(80 직접 사용)이 막히므로, **인증서를 먼저
받은 뒤에** 적용한다.

---

## 2. 첫 배포

```bash
cd /opt/bandapp
cp .env.prod.example .env.prod && chmod 600 .env.prod
$EDITOR .env.prod          # DOMAIN, LETSENCRYPT_EMAIL, 비밀번호 3종은 반드시 채운다
```

비밀값 생성:

```bash
openssl rand -base64 32   # DB_PASSWORD, REDIS_PASSWORD
openssl rand -base64 48   # JWT_SECRET  (비어 있으면 앱이 부팅에 실패한다 — 의도된 동작)
```

FCM 을 쓰면 서비스 계정 JSON 을 VM 에 두고 `chmod 600`,
`FCM_CREDENTIALS_HOST_PATH` 에 그 경로를 적는다.

```bash
docker login ghcr.io -u <github-id>     # 첫 pull 전 1회 (이후엔 Actions 가 알아서 로그인한다)
sh deploy/init-letsencrypt.sh           # 인증서 발급 + nginx 기동
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
curl https://$DOMAIN/actuator/health    # {"status":"UP"}
```

`init-letsencrypt.sh` 를 먼저 도는 이유: Nginx 는 기동할 때 인증서 파일을 읽는다.
없으면 설정 오류로 뜨지 못하고, 그러면 챌린지를 서빙할 Nginx 가 없어 발급도 못 한다.
그래서 최초 1회만 certbot 이 직접 80 을 잡는 `--standalone` 으로 끊고 시작한다.
설정만 시험할 때는 `STAGING=1 sh deploy/init-letsencrypt.sh` (레이트리밋 없는 테스트 CA).

**인증서 갱신은 자동이다.** compose 의 `certbot` 컨테이너가 12시간마다 `certbot renew`
(만료 30일 전부터만 실제 갱신), `nginx` 컨테이너가 6시간마다 `nginx -s reload` 로
갱신된 파일을 집어 든다. reload 는 무중단이다.

---

## 3. 자동 배포

`main` 의 CI 가 통과하면 `deploy.yml` 이 이미지를 만들어 GHCR 에 올리고 VM 에서
`deploy/deploy.sh` 를 돌린다. `deploy.sh` 는 **교체 전에 백업을 한 번 뜨고**, 교체 후
5분 안에 컨테이너가 healthy 가 되지 않으면 앱 로그를 찍고 실패로 끝난다.

GitHub 리포지토리 시크릿:

| 이름 | 값 |
|---|---|
| `DEPLOY_HOST` | VM 주소 |
| `DEPLOY_USER` | SSH 사용자 (docker 그룹) |
| `DEPLOY_KEY` | 그 사용자의 SSH 개인키 (OpenSSH 형식 전문) |
| `DEPLOY_PORT` | (선택) SSH 포트, 기본 22 |

GHCR 인증은 워크플로의 `GITHUB_TOKEN` 을 SSH 세션으로 넘겨 쓴다 — VM 에 PAT 를 심어 둘 필요가 없다.

**롤백** — 이전 태그로 되돌린다:

```bash
cd /opt/bandapp && sh deploy/deploy.sh sha-abc1234
```

배포된 태그는 `.env.prod` 의 `IMAGE_TAG` 에 적혀 있고, 후보는 GHCR 패키지 페이지에서 본다.

> 마이그레이션은 되돌아가지 않는다. 스키마를 바꾼 배포를 롤백하려면 §5 의 복구가 필요하다.

---

## 4. 백업 (단일 VM 이라 이게 유일한 데이터 보호 수단)

크론에 등록한다:

```bash
crontab -e
# 매일 03:30 KST
30 3 * * * cd /opt/bandapp && sh deploy/backup/pg-backup.sh >> /var/log/bandapp-backup.log 2>&1
```

동작: `pg_dump -Fc` → `backups/bandapp-<UTC타임스탬프>.dump` →
**뜬 자리에서 `pg_restore --list` 로 읽히는지 검증** → R2 `s3://<버킷>/db-backups/` 업로드 →
로컬·원격 각각 최근 `BACKUP_KEEP`(기본 7)개만 남기고 삭제.

검증 단계가 있는 이유: "백업은 매일 도는데 복구가 안 되는" 사고는 대개 빈 파일이나
에러 메시지가 섞인 파일이 그대로 쌓인 경우다. 유효하지 않으면 파일을 지우고 실패로 끝나
크론 메일/로그에 남는다.

R2 자격증명(`R2_*`)이 비어 있으면 로컬에만 보관하고 조용히 넘어간다.

---

## 5. 복구 절차

### 5-1. 백업이 살아 있는지 확인 (운영 DB 를 건드리지 않는 훈련)

**분기마다 한 번은 돌려 볼 것.** 빈 DB 를 새로 만들어 거기로 복구하고 행 수만 본다.

```bash
cd /opt/bandapp
RESTORE_DB=restore_drill sh deploy/backup/pg-restore.sh backups/bandapp-<타임스탬프>.dump
# 끝나면
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres \
    dropdb -U bandapp restore_drill
```

### 5-2. 실제 복구 (VM 이 날아갔거나 데이터가 깨졌을 때)

R2 에서 받아오는 것부터:

```bash
cd /opt/bandapp && . ./.env.prod
docker run --rm \
  -e AWS_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" -e AWS_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" \
  -e AWS_DEFAULT_REGION=auto -v "$PWD/backups:/backup" amazon/aws-cli \
  --endpoint-url "https://$R2_ACCOUNT_ID.r2.cloudflarestorage.com" \
  s3 ls "s3://$R2_BUCKET/db-backups/"          # 목록 확인

docker run --rm ... s3 cp "s3://$R2_BUCKET/db-backups/bandapp-<타임스탬프>.dump" /backup/
```

복구:

```bash
sh deploy/backup/pg-restore.sh backups/bandapp-<타임스탬프>.dump
```

스크립트가 하는 일: 앱 정지(커넥션 차단) → `DROP SCHEMA public CASCADE` →
`pg_restore --no-owner --exit-on-error` → 마이그레이션 버전·주요 테이블 행 수 출력 → 앱 재기동.
`--no-owner` 라서 롤 이름이 다른 새 VM 으로도 그대로 옮겨진다.

**VM 을 통째로 새로 만드는 경우**: §1·§2 로 스택을 올린 다음(앱이 뜨면 Flyway 가 빈 스키마를
만든다) 위 복구를 돌리면 덤프의 스키마·데이터로 덮인다.

**복구되지 않는 것** — DB 밖의 상태다.

| | |
|---|---|
| R2 의 사진·영상 | 별개 저장소라 VM 과 함께 죽지 않는다. 단, DB 를 과거 시점으로 되돌리면 그 뒤 올라온 객체는 참조가 끊긴 채 R2 에 남는다(고아 정리 배치가 지우지 않는다 — 수동 확인) |
| Redis (리프레시 토큰) | 복구 대상이 아니다. 전 사용자가 다시 로그인하면 된다 |
| Let's Encrypt 인증서 | `certbot-conf` 볼륨. 없으면 `init-letsencrypt.sh` 를 다시 돌린다 |

### 5-3. 검증 기록

2026-09-06, 로컬 스택(`docker-compose.yml`, 마이그레이션 V13)에서 실제로 수행:

1. `pg-backup.sh` 로 덤프 → 자체 검증 통과 → R2 `db-backups/` 업로드 성공
2. `RESTORE_DB=restore_drill` 로 **비어 있는 새 DB** 에 복구 →
   `flyway=13`, `users=9 / bands=5 / reservations=7 / board_posts=1` 로 백업 시점과 일치
3. 운영 DB 경로(스키마 드롭 후 복구 → 앱 재기동)도 함께 수행 → 앱 `healthy`, 행 수 동일

---

## 6. 운영 중 자주 쓰는 것

```bash
cd /opt/bandapp
C="docker compose -f docker-compose.prod.yml --env-file .env.prod"

$C ps                       # 상태
$C logs -f --tail 200 app   # 앱 로그
$C exec -T postgres psql -U bandapp -d bandapp    # DB 접속
$C restart app
```

**모니터링** — UptimeRobot 으로 `https://<도메인>/actuator/health` 를 5분 간격 감시.
Nginx 는 `/actuator/health` 만 통과시키고 나머지 `/actuator/*` 는 404 로 막는다.
`prod` 프로파일에서는 health 응답이 상세 없이 `{"status":"UP"}` 만 나가고, Swagger UI 와
`/v3/api-docs` 는 아예 등록되지 않는다.

---

## 7. 아직 안 한 것

- 이 문서의 절차는 **실제 VM 에서는 아직 돌려 보지 않았다.** 백업·복구만 로컬 스택에서 검증했다.
  VM 을 잡으면 §2 를 그대로 따라가며 확인할 것.
- 로그 로테이션(도커 기본 json-file 은 무한히 자란다). VM 배포 시
  `/etc/docker/daemon.json` 에 `log-opts.max-size` 를 건다.
- Sentry 연동(에러 트래킹) — `docs/DESIGN.md` §3 에 계획만 있다.
- 스토어 심사 요건(개인정보처리방침·이용약관 URL, 패키지명 `com.example.*` 교체, 릴리스 서명)은
  `docs/BACKLOG.md` §1 과 `docs/progress/NEXT.md` §2-A 참조. 배포 파이프라인과 별개 트랙이다.
