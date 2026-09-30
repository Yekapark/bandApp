-- 스토어 구독을 결제한 회원 (LAUNCH_REVIEW B13).
--
-- 결제한 사람이 탈퇴하면 서버가 그 사람의 자동 갱신 구독을 Play 에서 해지한다 — 탈퇴한 사람에게 매년 청구가
-- 이어지지 않게. 그러려면 "누가 결제했나" 를 알아야 하는데 지금까지는 "어느 밴드의 구독" 만 적었다. 밴드장을 위임하면
-- 결제자와 밴드장이 달라지므로 밴드장으로 추정할 수 없다.
--
-- 앱이 결제 뒤 검증(POST /plan/google/verify, /plan/google/restore)을 보낼 때 그 요청자를 적는다. 웹훅만으로 반영된
-- 구매(결제 직후 앱이 꺼진 경우)는 앱이 다음에 켜져 검증을 보낼 때 채워진다. 구매 토큰이 바뀌면(다른 결제·쿠폰 전환·환불)
-- 비우고, 탈퇴 처리 때도 비운다 — 해지를 마친 뒤로는 연결을 남길 이유가 없다.

ALTER TABLE band_plans ADD COLUMN purchased_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX ix_band_plans_purchased_by ON band_plans (purchased_by_user_id)
    WHERE purchased_by_user_id IS NOT NULL;

COMMENT ON COLUMN band_plans.purchased_by_user_id IS
    '지금 구매 토큰의 구독을 결제한 회원(검증 요청자). 모르면 NULL. 토큰이 바뀌거나 그 회원이 탈퇴하면 비운다.';
