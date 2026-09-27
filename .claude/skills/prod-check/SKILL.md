---
name: prod-check
description: 운영 서버(api.bandule.com)의 상태·설정·로그를 실제로 조회해서 답한다. "서버 어때?", "운영에 그 설정 켜져 있나?", "왜 안 올라왔지?", 배포 직후 확인, 장애 조사, `.env.prod` 를 고치거나 비교해야 할 때 쓴다. 로컬 파일이나 문서를 보고 서버 상태를 단정하지 않기 위한 절차다.
---

# 운영 서버 조회

**규칙: 서버 이야기를 할 때는 서버를 조회한다.** 로컬 `.env.prod` 는 git 에 없고
**서버 것과는 별개 파일**이다. 실제로 로컬 파일만 보고 "메일 설정 정상" 이라고 단정했다가
틀렸다 — 서버에서는 값이 앱에 전달조차 안 되고 있었다
(`docs/TROUBLESHOOTING.md` 2026-09-08 "신고·인증 메일이 한 통도 안 나갔다").

조회하지 않고 답할 거면, 답에 **"서버는 확인 안 했고 로컬 파일 기준"** 이라고 적는다.

## 접속

```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126
```

서버 앱은 `/opt/bandapp` 에 있고, 자주 쓰는 명령은 `bandule` 로 묶여 있다.
compose 옵션(`-f docker-compose.prod.yml --env-file .env.prod`)을 매번 치지 않는다.

```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'bandule health'
```

| 명령 | 보는 것 |
|---|---|
| `bandule health` | 컨테이너 안 / 바깥 주소 둘 다 응답하는지 |
| `bandule ps` | 컨테이너 5개 상태 |
| `bandule errors` | 최근 로그에서 error·exception·warn 만 |
| `bandule version` | **지금 돌고 있는 이미지 태그** — 배포가 실제로 반영됐는지 |
| `bandule disk` | 디스크 여유, 도커가 먹은 용량 |
| `bandule logs 200` | 앱 로그 (실시간이라 비대화식에서는 `--tail` 쪽을 쓴다) |

비대화식으로 로그를 볼 때는 `docker logs --since 10m bandapp-app-1` 처럼 범위를 준다.
`logs -f` 는 안 끝난다.

## 확인 순서 (장애 조사)

1. `bandule health` — 밖에서 죽었는데 안에서 살아 있으면 Nginx·인증서 쪽이다.
2. `bandule version` — 고쳤다는 코드가 **실제로 올라가 있는지** 부터 본다.
   CI 는 통과했는데 배포 단계에서 멈춰 있는 경우가 있다.
3. `bandule errors` — 스택트레이스 첫 줄이 아니라 **원인(Caused by)** 까지 본다.
4. 특정 엔드포인트가 맞고 있는지 세는 법:
   `docker logs --since 10m bandapp-nginx-1 2>&1 | grep -c '<경로>'`
   (7일 동안 초당 한 번씩 맞고 있던 걸 이걸로 찾았다 — TROUBLESHOOTING 2026-09-09)

## `.env.prod` 를 만질 때

- **덮어쓰기 전에 반드시 `diff`.** `IMAGE_TAG` 처럼 **서버가 관리하는 값**을 로컬 것으로
  밀면 옛 이미지로 롤백된다.
- 값을 고쳤으면 `bandule restart` 를 쓴다. `docker compose restart` 는 컨테이너만 다시 띄우고
  **.env.prod 를 다시 읽지 않는다** — 고쳐 놓고 restart 했는데 옛 값 그대로였던 적이 있다.
- 새 변수를 추가했으면 `docker-compose.prod.yml` 에서 앱에 **넘겨주고 있는지** 확인한다.
  `.env.prod` 에만 넣고 compose 에 안 엮어서 메일이 전부 no-op 이던 적이 있다.
- 스크립트에서 이 파일을 `source` 하지 않는다. compose 형식이지 셸 코드가 아니라
  값에 `#`·`<`·따옴표가 섞이면 문법 에러로 죽는다. 필요한 값만 `grep -m1` 로 뽑는다.

## 하지 않는 것

- **비밀값을 출력하지 않는다.** `cat .env.prod` 대신 키 이름만 확인한다
  (`grep -oE '^[A-Z_0-9]+' .env.prod`). 값이 필요하면 "설정돼 있음/비어 있음" 까지만 말한다.
- 서버에서 받은 파일을 작업 폴더에 두고 커밋하지 않는다. 그렇게 유출된 적이 있다.
- 조회로 끝내고, 고치는 명령(재시작·배포·DB 쓰기)은 **먼저 사용자에게 무엇을 할지 말한다.**

## 관련 문서

- `docs/OPERATIONS.md` — DB 접속, 통계·쿠폰·신고 쿼리
- `docs/DEPLOY.md` — 배포 구조
- `docs/TROUBLESHOOTING.md` — 겪은 장애 기록. 조사 전에 훑는다
