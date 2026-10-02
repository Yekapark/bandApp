-- 정산 몫: 밴드장 대신 체크·면제 (LAUNCH_REVIEW 결정 #25)
--
-- 현금으로 받은 경우 밴드장이 멤버 대신 "냈음" 을 체크할 수 있게 하고, 앱이 "밴드장 확인" 으로 구분해 보여 주도록
-- 누가 체크했는지를 남긴다. 밴드를 나간 멤버의 미납 몫은 밴드장이 "면제" 로 정리해 미납으로 세지 않게 한다.
-- 면제와 납부는 함께일 수 없고, 밴드장 확인은 납부일 때만 의미가 있다.

ALTER TABLE settlement_shares
    ADD COLUMN paid_by_leader BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN exempt         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_settlement_shares_exempt_unpaid CHECK (NOT (exempt AND paid)),
    ADD CONSTRAINT ck_settlement_shares_leader_paid CHECK (paid OR NOT paid_by_leader);

COMMENT ON COLUMN settlement_shares.paid IS
    '납부 완료 여부. 본인이 체크하거나, 밴드장이 대신 체크한다(현금 등 — paid_by_leader).';
COMMENT ON COLUMN settlement_shares.paid_by_leader IS
    '밴드장이 대신 "냈음" 으로 체크했는지. 본인이 체크하면 false. paid 가 false 면 항상 false.';
COMMENT ON COLUMN settlement_shares.exempt IS
    '밴드장이 면제한 몫(밴드를 나간 멤버의 미납). 미납 집계에서 빠지고 재계산 때도 금액이 고정된다. paid 와 함께 true 일 수 없다.';
