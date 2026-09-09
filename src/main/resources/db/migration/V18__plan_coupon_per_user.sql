-- 쿠폰을 한 사람이 통째로 태우지 못하게 막는다.
--
-- V12 의 유니크는 (coupon_id, band_id) 뿐이라 "같은 밴드에서 두 번" 만 막혔다. 밴드 생성은
-- 개수 제한도 레이트리밋도 없어서(BandService.create), 코드가 한 번 새면 한 사람이 밴드를
-- max_uses 개 만들어 혼자 다 쓸 수 있었다. 횟수 상한은 지켜지지만 "여러 밴드에 맛보기를
-- 뿌린다"는 쿠폰의 목적이 무너진다.
--
-- (coupon_id, redeemed_by) 유니크를 하나 더 걸어 한 계정은 한 쿠폰을 한 번만 쓰게 한다.
-- 애플리케이션은 안 고쳐도 된다 — PlanCouponService 의 DataIntegrityViolationException catch 가
-- 이 위반도 그대로 COUPON_ALREADY_USED(409) 로 옮긴다.
--
-- 적용 전 확인(중복이 있으면 이 마이그레이션이 실패한다. 있으면 남길 행을 정하고 지운 뒤 배포한다):
--
--   SELECT coupon_id, redeemed_by, count(*)
--     FROM plan_coupon_redemptions
--    GROUP BY coupon_id, redeemed_by HAVING count(*) > 1;

ALTER TABLE plan_coupon_redemptions
    ADD CONSTRAINT ux_plan_coupon_redemptions_user UNIQUE (coupon_id, redeemed_by);
