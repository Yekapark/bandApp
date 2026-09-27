-- 실제 합주 시각과 배치가 생성한 원래 회차를 구분한다.
ALTER TABLE reservations ADD COLUMN original_start_at TIMESTAMPTZ;

-- 기존 일정·정산은 그대로 보존한다. 과거에 이미 옮긴 회차의 원래 시각은 저장된 이력이 없어
-- 추정하지 않고, 마이그레이션 시점의 start_at 을 앞으로 유지할 기준으로 사용한다.
UPDATE reservations SET original_start_at = start_at WHERE recurring_rule_id IS NOT NULL;

ALTER TABLE reservations ADD CONSTRAINT ck_reservations_original_slot CHECK (
    (recurring_rule_id IS NULL AND original_start_at IS NULL)
    OR (recurring_rule_id IS NOT NULL AND original_start_at IS NOT NULL)
);

DROP INDEX ux_reservations_rule_slot;
CREATE UNIQUE INDEX ux_reservations_rule_slot ON reservations (recurring_rule_id, original_start_at)
    WHERE recurring_rule_id IS NOT NULL;

-- 상세·미래 회차 조회는 실제 시각을 사용하며, 겹치는 시작 시각도 허용한다.
CREATE INDEX ix_reservations_rule_start ON reservations (recurring_rule_id, start_at)
    WHERE recurring_rule_id IS NOT NULL;

COMMENT ON COLUMN reservations.original_start_at IS
    '반복 회차 생성 슬롯. 실제 시작 시각 수정과 무관하게 유지하며 중복 생성·연장 기준으로 사용. 단발 일정은 NULL.';
