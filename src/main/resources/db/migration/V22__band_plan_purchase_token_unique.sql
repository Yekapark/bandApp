-- 한 구매 토큰은 한 밴드에만 붙는다.
--
-- 지금까지는 StoreSubscriptionService 가 "다른 밴드에 이미 붙었나" 를 먼저 읽어 보고 막았다(PURCHASE_ALREADY_LINKED). 읽고 쓰는
-- 사이에 같은 토큰으로 두 밴드의 검증이 동시에 들어오면 둘 다 통과해, 한 번 산 구독으로 두 밴드가 PREMIUM 이 되고 웹훅은
-- 둘 중 아무 밴드나 찾았다. DB 가 막게 한다. 위반은 서버가 같은 409(PURCHASE_ALREADY_LINKED)로 바꾼다.
--
-- 쿠폰·무료 밴드는 토큰이 NULL 이라 빠진다. 적용 전 운영 확인(2026-10-02): 15행, 토큰 1개, 중복 없음.

CREATE UNIQUE INDEX ux_band_plans_purchase_token ON band_plans (purchase_token)
    WHERE purchase_token IS NOT NULL;
