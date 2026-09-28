-- 계정 이용 정지 (LAUNCH_REVIEW P7, 약관 제14조).
--
-- 운영자가 tools/moderate.py 로 채운다 — 앱 전역 관리자 역할이 없어 API 를 두지 않는다(V12 주석과 같은 사유).
-- suspended_until 이 지금보다 뒤면 정지 중이다. 영구 정지는 먼 미래(9999-12-31)로 적는다.
-- 기간이 지나면 저절로 풀린다 — 풀 때 행을 고칠 필요가 없고, 지난 정지 이력은 그대로 남는다.
-- suspension_reason 은 본인에게 보여 주는 문구가 아니라 운영 기록이다(앱에는 기간과 문의처만 보인다).
-- 탈퇴 후 개인정보 파기(anonymize) 때 사유도 함께 지운다.

ALTER TABLE users ADD COLUMN suspended_until TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN suspension_reason VARCHAR(200);

COMMENT ON COLUMN users.suspended_until IS
    '이용 정지가 끝나는 시각. NULL 이거나 지났으면 정지가 아니다. 영구 정지는 9999-12-31.';
COMMENT ON COLUMN users.suspension_reason IS
    '정지 사유(운영 기록). 본인에게 그대로 보여 주지 않는다. 개인정보 파기 때 함께 지운다.';
