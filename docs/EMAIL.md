# 운영 메일 설정

`notice@bandule.com` 은 수신과 발신을 서로 다른 서비스가 담당한다.

- 수신: Cloudflare Email Routing → 운영자 Gmail 로 전달
- 발신: Resend SMTP → 가입 인증·비밀번호 재설정·신고 알림 발송

Cloudflare Email Sending 은 Workers Paid 요금제가 필요하므로 사용하지 않는다. Resend 무료
한도는 일 100통·월 3,000통이며, 초기 서비스의 트랜잭션 메일에 사용한다.

## 1. Resend 도메인 인증

1. Resend 무료 계정을 만들고 **Domains → Add domain** 에 `bandule.com` 을 추가한다.
2. Resend 가 제시하는 Cloudflare 자동 연결을 사용하거나 DNS 레코드를 수동으로 추가한다.
3. 수동 설정 시 Resend 화면에 표시된 값을 그대로 쓴다. 보통 `send.bandule.com` 용 MX·SPF와
   DKIM TXT 레코드가 추가된다.
4. **Verify DNS Records** 를 눌러 상태가 `Verified` 인지 확인한다.

`send.bandule.com` 레코드는 반송 처리용이라 루트 `bandule.com` 의 Cloudflare Email Routing
MX와 충돌하지 않는다. 기존 수신 라우팅 레코드를 지우거나 바꾸지 않는다.

## 2. 발송 전용 API 키

Resend **API Keys → Create API Key** 에서 다음과 같이 만든다.

- 이름: `bandule-prod-smtp`
- 권한: `Sending access`
- 도메인: `bandule.com`

키는 생성 직후 한 번만 보인다. 채팅·GitHub·문서에 적지 말고 서버
`/opt/bandapp/.env.prod` 의 `MAIL_SMTP_PASSWORD` 에만 저장한다.

## 3. 서버 환경변수

서버 `/opt/bandapp/.env.prod` 의 기존 `MAIL_*` 값을 아래처럼 교체한다.

```dotenv
MAIL_SMTP_HOST=smtp.resend.com
MAIL_SMTP_PORT=465
MAIL_SMTP_STARTTLS=false
MAIL_SMTP_SSL=true
MAIL_SMTP_USERNAME=resend
MAIL_SMTP_PASSWORD=<Resend API 키>
MAIL_FROM=밴듈 <notice@bandule.com>
```

`MAIL_SMTP_USERNAME` 은 사용자에게 보이는 주소가 아니라 Resend 가 요구하는 고정 SMTP 사용자명이다.
사용자에게 표시되는 발신자는 `MAIL_FROM` 이 결정한다.

## 4. 무중단 전환 순서

1. SMTP host/port/TLS/SSL 환경변수를 지원하는 앱 버전을 먼저 배포한다.
2. Resend 도메인 상태가 `Verified` 인지 확인한다.
3. 발송 전용 API 키를 만든다.
4. 서버 `.env.prod` 의 일곱 `MAIL_*` 값을 교체한다.
5. 앱 컨테이너만 다시 만든다.

```bash
cd /opt/bandapp
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --force-recreate app
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T app \
  sh -c "printenv | grep -c '^MAIL_'"
# 7 이 나와야 한다.
```

기존 서버 설정에는 `MAIL_SMTP_HOST` 등이 없으므로 새 앱을 먼저 배포해도 Gmail 기본값으로
계속 발송한다. Resend 인증과 키 준비가 끝난 뒤 환경변수를 바꿔야 전환 중 공백이 없다.

## 5. 확인

다른 계정으로 인증 메일 또는 비밀번호 재설정 메일을 요청한다.

- 보낸사람: `밴듈 <notice@bandule.com>`
- Resend Emails 로그: `Delivered`
- 앱 로그: `[email] 발송 실패`가 없어야 함

발송 실패는 가입·신고 같은 본 작업을 막지 않도록 앱이 삼키고 로그만 남긴다. 화면 성공만
보고 판단하지 말고 Resend 로그와 서버 로그를 함께 확인한다.
