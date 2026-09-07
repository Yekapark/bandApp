-- 테이블·컬럼 설명(주석)을 채운다.
--
-- 왜 필요한가 — HeidiSQL·DBeaver 같은 도구는 이 주석을 컬럼 옆에 그대로 보여준다.
-- 운영 중에 DB 를 직접 들여다볼 때(통계·장애 확인·데이터 수정) 컬럼 이름만으로는 뜻을 알 수
-- 없는 것이 많아서, 코드를 열지 않고도 읽히도록 여기 적어 둔다.
--
-- 스키마는 바꾸지 않는다. COMMENT 문뿐이라 되돌릴 일이 없고 테이블 잠금도 걸리지 않는다.
-- 이미 설명이 있던 컬럼은 건드리지 않았다(V1~V15 에서 적은 것을 그대로 둔다).
-- 앞으로 테이블·컬럼을 추가할 때는 그 마이그레이션에서 COMMENT 도 함께 적는다.

-- ===== 계정 =====
COMMENT ON TABLE  users IS '사용자 계정. 이메일 가입과 소셜(카카오) 가입이 한 테이블에 함께 산다 - ck_users_credentials 가 둘 중 하나의 형태만 허용한다.';
COMMENT ON COLUMN users.id IS '사용자 번호(PK). 다른 테이블이 이 값을 가리킨다.';
COMMENT ON COLUMN users.name IS '표시 이름. 밴드원 목록·게시글 작성자에 보인다.';
COMMENT ON COLUMN users.social_id IS '소셜 제공자가 준 고유 id. 카카오면 회원번호. 이메일 가입이면 NULL.';
COMMENT ON COLUMN users.email_verified IS '이메일 인증 완료 여부. 소셜 가입은 제공자가 확인해 주므로 true 로 시작한다.';
COMMENT ON COLUMN users.created_at IS '가입 시각.';

COMMENT ON TABLE  terms_agreements IS '약관·개인정보 처리방침 동의 이력. 버전이 올라가면 새 행이 쌓이고 지우지 않는다 - 언제 어떤 버전으로 동의받았는지가 증거다.';
COMMENT ON COLUMN terms_agreements.id IS '동의 이력 번호(PK).';
COMMENT ON COLUMN terms_agreements.user_id IS '동의한 사용자.';
COMMENT ON COLUMN terms_agreements.terms_version IS '동의한 이용약관 버전(예: 2026-09-06).';
COMMENT ON COLUMN terms_agreements.privacy_version IS '동의한 개인정보 처리방침 버전.';
COMMENT ON COLUMN terms_agreements.agreed_at IS '동의한 시각.';

-- ===== 밴드 =====
COMMENT ON TABLE  bands IS '밴드(팀). 모든 일정·게시글·정산이 밴드 단위로 격리된다.';
COMMENT ON COLUMN bands.id IS '밴드 번호(PK).';
COMMENT ON COLUMN bands.name IS '밴드 이름.';
COMMENT ON COLUMN bands.created_at IS '밴드를 만든 시각.';

COMMENT ON TABLE  band_members IS '밴드 소속. 나갔다 다시 들어올 수 있어 한 사람이 같은 밴드에 여러 행을 가질 수 있다 - 현재 소속은 left_at IS NULL 인 행이다.';
COMMENT ON COLUMN band_members.id IS '소속 번호(PK).';
COMMENT ON COLUMN band_members.band_id IS '어느 밴드인지.';
COMMENT ON COLUMN band_members.user_id IS '어느 사용자인지.';
COMMENT ON COLUMN band_members.role IS 'LEADER(밴드장) | MEMBER(밴드원). 밴드장은 한 명이며 bands.leader_id 와 일치한다.';
COMMENT ON COLUMN band_members.joined_at IS '가입한 시각.';

COMMENT ON TABLE  band_invites IS '밴드 초대 코드. 링크(bandule://invite/{code})로 공유한다. 만료·사용횟수·강제해지 중 하나라도 걸리면 못 쓴다.';
COMMENT ON COLUMN band_invites.id IS '초대 번호(PK).';
COMMENT ON COLUMN band_invites.band_id IS '어느 밴드로 초대하는지.';
COMMENT ON COLUMN band_invites.code IS '초대 코드 8자리. 링크에 실리는 값이라 헷갈리는 문자(0/O, 1/I)는 빼고 만든다.';
COMMENT ON COLUMN band_invites.expires_at IS '이 시각이 지나면 코드가 안 먹는다.';
COMMENT ON COLUMN band_invites.used_count IS '지금까지 이 코드로 가입한 사람 수. max_uses 에 닿으면 더 못 쓴다.';
COMMENT ON COLUMN band_invites.revoked IS 'TRUE 면 밴드장이 손으로 끊은 코드. 기간이 남아 있어도 안 먹는다.';
COMMENT ON COLUMN band_invites.created_by IS '코드를 만든 사람.';

-- ===== 합주실 · 일정 =====
COMMENT ON TABLE  rooms IS '합주실. 밴드가 직접 등록해 쓰는 장소 목록이다 - 예약 시스템 연동이 아니다(실제 예약은 전화·카톡 등 앱 밖에서 한다).';
COMMENT ON COLUMN rooms.id IS '합주실 번호(PK).';
COMMENT ON COLUMN rooms.band_id IS '이 합주실을 등록한 밴드. 밴드끼리 공유하지 않는다.';
COMMENT ON COLUMN rooms.name IS '합주실 이름.';
COMMENT ON COLUMN rooms.phone IS '예약 전화번호. 앱이 걸어 주지 않고 사람이 직접 건다.';
COMMENT ON COLUMN rooms.memo IS '자유 메모(주차·장비 등).';
COMMENT ON COLUMN rooms.created_by IS '등록한 사람.';
COMMENT ON COLUMN rooms.created_at IS '등록 시각.';

COMMENT ON TABLE  reservations IS '합주 일정. 이미 잡아 둔 예약을 기록하는 것이라 시간이 겹쳐도 저장을 막지 않는다(겹침은 응답에 경고로만 실린다).';
COMMENT ON COLUMN reservations.id IS '일정 번호(PK).';
COMMENT ON COLUMN reservations.band_id IS '어느 밴드의 일정인지.';
COMMENT ON COLUMN reservations.requested_by IS '일정을 등록한 사람.';
COMMENT ON COLUMN reservations.start_at IS '합주 시작 시각. UTC 로 저장하고 화면은 Asia/Seoul 로 보여준다.';
COMMENT ON COLUMN reservations.end_at IS '합주 종료 시각.';
COMMENT ON COLUMN reservations.created_at IS '등록 시각.';

COMMENT ON TABLE  recurring_rules IS '정기 합주 규칙(매주·격주·매월). 규칙 하나가 앞으로 8주분 reservations 행을 자동으로 만든다. 등록은 PREMIUM 전용이지만, 요금제가 무료로 내려가도 이미 만든 규칙의 회차 생성은 계속된다.';
COMMENT ON COLUMN recurring_rules.id IS '규칙 번호(PK).';
COMMENT ON COLUMN recurring_rules.band_id IS '어느 밴드의 규칙인지.';
COMMENT ON COLUMN recurring_rules.room_id IS '어느 합주실에서 하는지.';
COMMENT ON COLUMN recurring_rules.frequency IS 'WEEKLY(매주) | BIWEEKLY(격주) | MONTHLY(매월).';
COMMENT ON COLUMN recurring_rules.start_time IS '합주 시작 시각(한국 시간 기준 시:분).';
COMMENT ON COLUMN recurring_rules.end_time IS '합주 종료 시각.';
COMMENT ON COLUMN recurring_rules.start_date IS '이 날짜부터 회차를 만든다.';
COMMENT ON COLUMN recurring_rules.end_date IS '이 날짜까지만 만든다. NULL 이면 기한 없음 - 배치가 계속 이어 만든다.';
COMMENT ON COLUMN recurring_rules.note IS '회차마다 함께 들어갈 메모.';
COMMENT ON COLUMN recurring_rules.created_by IS '규칙을 만든 사람.';
COMMENT ON COLUMN recurring_rules.created_at IS '규칙을 만든 시각.';

COMMENT ON TABLE  reservation_attendances IS '일정별 참석 응답. 행이 없으면 아직 응답하지 않은 것이다.';
COMMENT ON COLUMN reservation_attendances.id IS '참석 응답 번호(PK).';
COMMENT ON COLUMN reservation_attendances.reservation_id IS '어느 일정에 대한 응답인지.';
COMMENT ON COLUMN reservation_attendances.user_id IS '응답한 사람.';
COMMENT ON COLUMN reservation_attendances.created_at IS '행이 생긴 시각. 실제 응답 시각은 responded_at.';

COMMENT ON TABLE  setlist_items IS '합주 곡 목록. 일정 하나에 여러 곡이 order_no 순서로 달린다.';
COMMENT ON COLUMN setlist_items.id IS '곡 번호(PK).';
COMMENT ON COLUMN setlist_items.reservation_id IS '어느 합주의 곡인지.';
COMMENT ON COLUMN setlist_items.title IS '곡 제목.';
COMMENT ON COLUMN setlist_items.artist IS '아티스트.';
COMMENT ON COLUMN setlist_items.reference_url IS '참고 링크(유튜브·악보 등).';
COMMENT ON COLUMN setlist_items.created_at IS '추가한 시각.';

-- ===== 정산 =====
COMMENT ON TABLE  settlements IS '합주 비용 정산. 일정 하나당 하나다. 실제 송금은 앱 밖에서 하고 앱은 누가 얼마를 낼지 기록만 한다.';
COMMENT ON COLUMN settlements.id IS '정산 번호(PK).';
COMMENT ON COLUMN settlements.reservation_id IS '어느 합주의 정산인지.';
COMMENT ON COLUMN settlements.created_at IS '정산을 만든 시각.';

COMMENT ON TABLE  settlement_shares IS '정산 참여자별 분담액. 정산이 지워지면 함께 지워진다(ON DELETE CASCADE).';
COMMENT ON COLUMN settlement_shares.id IS '분담 번호(PK).';
COMMENT ON COLUMN settlement_shares.settlement_id IS '어느 정산에 속하는지.';
COMMENT ON COLUMN settlement_shares.user_id IS '낼 사람.';
COMMENT ON COLUMN settlement_shares.created_at IS '행이 생긴 시각.';

-- ===== 게시판 · 첨부 · 신고 =====
COMMENT ON TABLE  board_posts IS '밴드 게시판 글. deleted_at 이 채워지면 삭제된 글이다 - 행은 남는다(신고 처리에 필요하다).';
COMMENT ON COLUMN board_posts.id IS '게시글 번호(PK).';
COMMENT ON COLUMN board_posts.band_id IS '어느 밴드의 게시판인지.';
COMMENT ON COLUMN board_posts.author_id IS '작성자.';
COMMENT ON COLUMN board_posts.title IS '제목.';
COMMENT ON COLUMN board_posts.content IS '본문.';
COMMENT ON COLUMN board_posts.created_at IS '작성 시각.';

COMMENT ON TABLE  media_attachments IS '게시글 첨부(사진·영상). 파일 자체는 Cloudflare R2 에 있고 이 테이블은 위치와 상태만 갖는다. 영상 업로드는 PREMIUM 전용이다.';
COMMENT ON COLUMN media_attachments.id IS '첨부 번호(PK).';
COMMENT ON COLUMN media_attachments.board_post_id IS '어느 게시글에 달린 첨부인지.';
COMMENT ON COLUMN media_attachments.type IS 'IMAGE | VIDEO.';
COMMENT ON COLUMN media_attachments.content_type IS 'MIME 타입(image/jpeg, video/mp4 등). 업로드 완료 시 실제 파일과 대조한다.';
COMMENT ON COLUMN media_attachments.uploaded_at IS '업로드가 실제로 끝난 시각. PENDING 인 동안은 NULL.';
COMMENT ON COLUMN media_attachments.created_at IS '행이 생긴 시각(업로드 URL 발급 시점).';

COMMENT ON TABLE  reports IS '신고 접수. 게시글·첨부·사용자를 신고할 수 있고 자기 것은 신고할 수 없다. 접수되면 REPORT_NOTIFY_USER_IDS 계정으로 푸시가 나간다 - 메일은 보내지 않는다.';
COMMENT ON COLUMN reports.id IS '신고 번호(PK).';
COMMENT ON COLUMN reports.target_type IS 'POST(게시글) | MEDIA(사진·영상) | USER(사용자). target_id 를 어느 테이블에서 찾을지 정한다.';
COMMENT ON COLUMN reports.reporter_id IS '신고한 사람.';
COMMENT ON COLUMN reports.reason IS '신고 사유(사용자가 쓴 글).';
COMMENT ON COLUMN reports.status IS 'OPEN(처리 대기) | RESOLVED(처리됨). 같은 사람이 같은 대상을 OPEN 상태로 두 번 신고할 수 없다.';
COMMENT ON COLUMN reports.created_at IS '신고 시각.';

COMMENT ON TABLE  user_blocks IS '사용자 차단. 차단하면 게시판에서 서로의 글이 양방향으로 빠진다. 밴드 단위가 아니라 계정 전역이다.';
COMMENT ON COLUMN user_blocks.id IS '차단 번호(PK).';
COMMENT ON COLUMN user_blocks.blocker_id IS '차단한 사람.';
COMMENT ON COLUMN user_blocks.created_at IS '차단한 시각.';

-- ===== 알림 =====
COMMENT ON TABLE  notification_settings IS '사용자별 알림 설정. 행이 없으면 기본값(푸시 켬 + 1시간 전 리마인더)으로 동작하고, 처음 조회하거나 바꿀 때 만들어진다.';
COMMENT ON COLUMN notification_settings.user_id IS '사용자(PK). 사용자당 한 행.';
COMMENT ON COLUMN notification_settings.push_enabled IS '앱 안의 푸시 on/off 스위치. FALSE 면 발송 대상에서 빠진다. 기기 토큰(device_tokens)과는 별개다 - 여기를 꺼도 토큰 행은 그대로 남는다.';
COMMENT ON COLUMN notification_settings.created_at IS '설정 행이 생긴 시각.';
COMMENT ON COLUMN notification_settings.updated_at IS '마지막으로 바꾼 시각.';

COMMENT ON TABLE  device_tokens IS 'FCM 기기 토큰 = 푸시를 보낼 주소. 이 테이블에 행이 없으면 푸시가 아예 안 나간다. 로그인·로그아웃·FCM 토큰 자동 갱신 때만 바뀌며 앱 안의 알림 토글로는 바뀌지 않는다. 재설치하면 토큰이 새로 발급돼 새 행이 생기고, 옛 토큰은 발송 때 FCM 이 무효라고 답하면 자동으로 지워진다.';
COMMENT ON COLUMN device_tokens.id IS '토큰 행 번호(PK).';
COMMENT ON COLUMN device_tokens.user_id IS '이 기기를 쓰는 사용자. 계정을 바꿔 로그인하면 같은 토큰 행의 주인이 바뀐다.';
COMMENT ON COLUMN device_tokens.platform IS 'IOS | ANDROID | WEB.';
COMMENT ON COLUMN device_tokens.created_at IS '이 토큰이 처음 등록된 시각.';
COMMENT ON COLUMN device_tokens.updated_at IS '마지막으로 등록·갱신된 시각. 앱을 켜서 로그인할 때마다 갱신된다.';

COMMENT ON TABLE  notification_dispatches IS '알림 발송 이력. (user, type, target, variant) 유니크 제약이 같은 알림이 두 번 나가는 것을 막는다 - 배치를 다시 돌리거나 서버를 재시작해도 안전하다. 앱의 알림 목록도 이 행들을 읽는다.';
COMMENT ON COLUMN notification_dispatches.id IS '발송 이력 번호(PK).';
COMMENT ON COLUMN notification_dispatches.user_id IS '받은 사람.';
COMMENT ON COLUMN notification_dispatches.type IS '알림 종류(RESERVATION_CREATED, SETTLEMENT_CREATED, REPORT_RECEIVED 등).';
COMMENT ON COLUMN notification_dispatches.target_id IS '알림이 가리키는 대상 번호. 종류에 따라 일정·정산·신고 등 다른 테이블을 가리킨다.';
COMMENT ON COLUMN notification_dispatches.band_id IS '어느 밴드의 알림인지. 앱의 알림 목록은 밴드별로 조회하므로 이 값이 NULL 이면 목록에 안 뜬다(푸시는 나간다). 신고 접수 알림처럼 밴드가 없는 알림이 여기 해당한다.';
COMMENT ON COLUMN notification_dispatches.created_at IS '발송한 시각.';

-- ===== 요금제 =====
COMMENT ON TABLE  band_plans IS '밴드별 요금제(FREE/PREMIUM). 밴드당 한 행이며 티어가 바뀌어도 새 행을 만들지 않고 이 행을 고친다. 해지는 즉시 무료가 아니라 만료일 강등 예약이다 - subscription_ref 가 비면 해지된 것이고, expires_at 이 지나면 야간 배치가 FREE 로 내린다.';
COMMENT ON COLUMN band_plans.id IS '요금제 행 번호(PK).';
COMMENT ON COLUMN band_plans.band_id IS '어느 밴드의 요금제인지. 밴드당 한 행.';
COMMENT ON COLUMN band_plans.started_at IS '지금 티어가 시작된 시각. 업그레이드·강등 때마다 갱신된다(해지 예약 때는 안 바뀐다).';
COMMENT ON COLUMN band_plans.created_at IS '행이 생긴 시각(밴드 생성 시점).';
COMMENT ON COLUMN band_plans.updated_at IS '마지막으로 바뀐 시각.';

COMMENT ON TABLE  plan_coupons IS '프리미엄 쿠폰. 결제 없이 기간을 주는 수단이며 지금은 이 경로로만 프리미엄을 준다. 발급 화면이 없어 코드는 사람이 직접 INSERT 로 만든다.';
COMMENT ON COLUMN plan_coupons.id IS '쿠폰 번호(PK).';
COMMENT ON COLUMN plan_coupons.code IS '쿠폰 코드 8자리. 사용자가 앱에 입력하는 값이며 대문자로 저장한다.';
COMMENT ON COLUMN plan_coupons.used_count IS '지금까지 쓰인 횟수. max_uses 에 닿으면 더 못 쓴다.';
COMMENT ON COLUMN plan_coupons.revoked IS 'TRUE 면 손으로 끊은 쿠폰. 기간·횟수가 남아 있어도 안 먹는다.';
COMMENT ON COLUMN plan_coupons.created_at IS '쿠폰을 만든 시각.';

COMMENT ON TABLE  plan_coupon_redemptions IS '쿠폰 사용 이력. 한 밴드가 같은 쿠폰을 두 번 쓰지 못하게 막는 역할도 한다.';
COMMENT ON COLUMN plan_coupon_redemptions.id IS '사용 이력 번호(PK).';
COMMENT ON COLUMN plan_coupon_redemptions.coupon_id IS '쓴 쿠폰.';
COMMENT ON COLUMN plan_coupon_redemptions.band_id IS '적용된 밴드.';
COMMENT ON COLUMN plan_coupon_redemptions.redeemed_by IS '쿠폰을 입력한 사람(밴드장).';
COMMENT ON COLUMN plan_coupon_redemptions.redeemed_at IS '사용한 시각.';
