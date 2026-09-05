-- V11  알림 목록(피드)
--
-- notification_dispatches 는 원래 "이미 보냈는가"만 판단하는 멱등 키라 문구를 담지 않았다.
-- 앱에 알림 목록 화면을 만들려면 보낸 문구가 남아 있어야 해서 세 컬럼을 더한다.
--
-- 모두 NULL 허용이다. 이 마이그레이션 이전에 쌓인 행은 문구가 없고, 조회 API 는 그런 행을
-- 건너뛴다(과거 알림은 목록에 안 뜬다). 새로 보내는 알림부터 채워진다.
--
-- 읽음 여부는 서버에 두지 않는다 — 클라이언트가 "마지막 확인 시각"을 기기에 저장하고
-- 그보다 새 알림 수를 배지로 센다.

ALTER TABLE notification_dispatches
    ADD COLUMN band_id BIGINT,
    ADD COLUMN title   VARCHAR(100),
    ADD COLUMN body    VARCHAR(500);

-- 목록 조회: 특정 밴드의 내 알림을 최신순으로. 문구 없는 옛 행은 대상이 아니다.
CREATE INDEX idx_notification_dispatches_feed
    ON notification_dispatches (user_id, band_id, id DESC)
    WHERE title IS NOT NULL;
