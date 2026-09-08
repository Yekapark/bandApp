-- Phase 12 인앱결제 (Google Play). 스토어가 결제·갱신·해지를 관리하고, 서버는 클라이언트가 보낸
-- 구매 토큰을 Play Developer API 로 검증하고 RTDN(Pub/Sub) 웹훅으로 상태 변화를 반영만 한다.
--
-- band_plans.store           결제가 어느 스토어에서 일어났는지(GOOGLE_PLAY). 쿠폰·no-op 업그레이드는 NULL.
-- band_plans.purchase_token  Google Play 구독 구매 토큰. 서버가 구독의 현재 상태를 재조회하는 키다.
--                            PREMIUM 인 동안만 채워지고 FREE 로 내려가면 비운다.
--
-- processed_store_events     RTDN 메시지 멱등 처리. Pub/Sub 는 at-least-once + 순서 무보장이라 같은
--                            messageId 가 여러 번 올 수 있다. PK 충돌이면 이미 반영한 것으로 보고 무시한다.

ALTER TABLE band_plans ADD COLUMN store          VARCHAR(20);
ALTER TABLE band_plans ADD COLUMN purchase_token TEXT;

ALTER TABLE band_plans ADD CONSTRAINT ck_band_plans_store
    CHECK (store IS NULL OR store IN ('GOOGLE_PLAY'));
-- 스토어 결제로 올라간 PREMIUM 은 구매 토큰이 짝으로 있어야 한다. 쿠폰(store NULL)은 둘 다 NULL.
ALTER TABLE band_plans ADD CONSTRAINT ck_band_plans_store_token
    CHECK ((store IS NULL) = (purchase_token IS NULL));

COMMENT ON COLUMN band_plans.store IS
    '결제 스토어(GOOGLE_PLAY). 쿠폰·no-op 업그레이드는 NULL.';
COMMENT ON COLUMN band_plans.purchase_token IS
    'Google Play 구독 구매 토큰. 서버가 Play Developer API 로 구독 상태를 재조회하는 키.';

CREATE TABLE processed_store_events (
    message_id        VARCHAR(255) PRIMARY KEY,
    store             VARCHAR(20)  NOT NULL,
    notification_type INTEGER,
    purchase_token    TEXT,
    received_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_processed_store_events_store CHECK (store IN ('GOOGLE_PLAY'))
);
COMMENT ON TABLE processed_store_events IS
    'RTDN(Pub/Sub) 메시지 멱등 처리. message_id PK 충돌 = 이미 반영함.';
