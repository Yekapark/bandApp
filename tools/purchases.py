"""밴드를 못 찾은 결제 처리 도구 — 매일 점검의 「밴드없는결제」 경보를 끝낸다(QA BILL-31, V24 unmatched_store_purchases).

돈은 들어왔는데 서버가 PREMIUM 을 줄 밴드를 못 찾은 구독이 남는다. 대부분은 서버 배치(10분마다)가 스스로 닫는다 —
밴드에 붙었거나, Google 이 환불·만료했으면. 확인 처리를 못 받은 구매는 **결제 3일 뒤 Google 이 자동 환불**하므로
아무것도 안 해도 경보는 꺼진다. 결제한 밴드를 알면 그 밴드에 반영할 수 있다.

    python tools/purchases.py list                     # 미해결 목록 (매일 점검 메일에 나온 번호)
    python tools/purchases.py show 3                   # 3번 자세히 — 이어받은 예전 구독의 밴드(원래 밴드 후보)
    python tools/purchases.py link 3 123               # 3번 결제를 밴드 123 에 반영 — 서버 배치가 10분 안에 PREMIUM·확인 처리
    python tools/purchases.py close 3 "환불함"          # 반영 없이 닫기 (Console 에서 직접 환불했을 때 등)
    python tools/purchases.py find-band 노을밴드          # 밴드ID 찾기 — 밴드 이름 일부, 또는 멤버 가입 이메일
    python tools/purchases.py drill                    # 훈련: 종류별 가짜 미해결 건을 넣어 점검·메일을 실제로 돌려 본다
    python tools/purchases.py drill-clean              # 훈련 건만 지운다

무엇을 바꿀지 먼저 보여 주고 `yes` 를 입력해야 실행한다. 구매 토큰은 화면에 앞 12자만 보인다.
필요한 것: 배포 키 `~/.ssh/bandule_deploy` (docs/NEW_PC_SETUP.md). 다른 키·호스트는 환경변수 BANDULE_SSH_KEY·BANDULE_HOST
(BANDULE_HOST=local 이면 내 PC 의 docker compose 에 한다 — 시험용).
"""

import argparse
import os
import subprocess
import sys

HOST = os.environ.get("BANDULE_HOST", "root@64.176.231.126")
KEY = os.path.expanduser(os.environ.get("BANDULE_SSH_KEY", "~/.ssh/bandule_deploy"))
# 운영도 로컬 docker compose 도 컨테이너 이름이 같다(deploy/play-revoke.sh 와 같은 방식).
PSQL = "docker exec -i bandapp-postgres-1 psql -U bandapp -d bandapp"


def ssh(remote_cmd, stdin_text=None):
    # BANDULE_HOST=local 이면 내 PC 의 docker compose 에 한다 — 운영을 건드리지 않고 시험할 때.
    local = HOST == "local"
    argv = remote_cmd if local else ["ssh", "-i", KEY, "-o", "BatchMode=yes", HOST, remote_cmd]
    result = subprocess.run(
        argv, shell=local,
        input=stdin_text, capture_output=True, text=True, encoding="utf-8",
    )
    if result.returncode != 0:
        sys.exit(f"!! 서버 명령 실패 (exit {result.returncode})\n{result.stderr.strip()}")
    return result.stdout


def sql(statements, *, table=True):
    """SQL 을 표준입력으로 넘긴다 — 메모의 따옴표가 셸을 거치지 않게. 한 트랜잭션으로 묶는다."""
    flags = "-v ON_ERROR_STOP=1 -1 -f -" + ("" if table else " -At")
    return ssh(f"{PSQL} {flags}", statements)


def quote(text):
    return "'" + text.replace("'", "''") + "'"


def confirm(what):
    print()
    print(what)
    if input("진행하려면 yes 를 입력: ").strip() != "yes":
        sys.exit("취소했다. 아무것도 바꾸지 않았다.")


def must_be_open(row_id):
    """번호를 잘못 치거나 이미 끝난 건이면 멈춘다 — 0행이 바뀌어도 "완료" 가 찍히면 처리한 줄 알고 넘어간다."""
    state = sql(f"SELECT CASE WHEN resolved_at IS NULL THEN 'open' ELSE 'closed' END "
                f"FROM unmatched_store_purchases WHERE id = {row_id};", table=False).strip()
    if state == "":
        sys.exit(f"!! {row_id}번이 없다. list 로 번호를 확인한다. 아무것도 바꾸지 않았다.")
    if state == "closed":
        sys.exit(f"!! {row_id}번은 이미 해결됐다(show 로 어떻게 끝났는지 본다). 아무것도 바꾸지 않았다.")


# Google 알림 번호를 무슨 일인지로 풀어 쓴다 — 「복구·갱신」 같은 Google 용어는 알아보기 어렵다.
KIND = ("CASE notification_type "
        "WHEN 4 THEN '새로 결제함' "
        "WHEN 2 THEN '1년 자동 갱신 결제' "
        "WHEN 7 THEN '해지·만료했다가 다시 구독' "
        "WHEN 1 THEN '카드 문제로 멈췄다가 결제 다시 됨' "
        "WHEN 6 THEN '카드 문제로 결제 실패, 유예 중' "
        "ELSE '알림 ' || notification_type END")

ROW = (f"SELECT id AS 번호, received_at AT TIME ZONE 'Asia/Seoul' AS 받은시각_KST, {KIND} AS 무슨_결제, "
       "product_id AS 상품, order_id AS 주문ID, left(purchase_token, 12) || '…' AS 토큰, "
       "(received_at + interval '3 days') AT TIME ZONE 'Asia/Seoul' AS 자동환불_예정_KST, "
       "target_band_id AS 반영할밴드, resolved_at AT TIME ZONE 'Asia/Seoul' AS 해결_KST, resolution AS 해결 "
       "FROM unmatched_store_purchases")


def cmd_list(_):
    print(sql(f"{ROW} WHERE resolved_at IS NULL ORDER BY id;"))
    print("결제한 밴드를 알면: python tools/purchases.py link <번호> <밴드ID>  /  모르면 두면 3일 뒤 자동 환불로 닫힌다.")


def cmd_show(a):
    print(sql(f"{ROW} WHERE id = {a.id};"))
    # 이 결제가 이어받은 예전 구독이 붙어 있던 밴드 — 원래 밴드의 가장 강한 후보. 예전 밴드가 지워졌거나 이어받은 구독이
    # 아니면(새 결제 등) 없다. Play Console 에는 결제자 이메일이 안 나오므로, 그때는 결제한 사람의 문의로 찾는다.
    prev = sql(f"SELECT b.id || ' (' || b.name || ', ' || p.tier || ')' "
               f"FROM unmatched_store_purchases u JOIN band_plans p ON p.purchase_token = u.linked_purchase_token "
               f"JOIN bands b ON b.id = p.band_id WHERE u.id = {a.id};", table=False).strip()
    if prev:
        print(f"이어받은 예전 구독의 밴드: {prev}  → 맞으면 python tools/purchases.py link {a.id} {prev.split(' ')[0]}")
    else:
        print("이어받은 예전 구독의 밴드: 없음 (예전 밴드가 지워졌거나, 새 결제라 이어받은 구독이 없다)")
        print("→ 결제한 사람이 문의하면 영수증 메일의 주문번호(GPA.…)가 위 주문ID 와 같은지 보고,"
              " 밴드 이름·가입 이메일로 python tools/purchases.py find-band <이름 또는 이메일>")
        print("→ 문의가 없으면 그대로 둔다 — 자동환불 예정 시각에 Google 이 환불하고 저절로 닫힌다")


def cmd_find_band(a):
    q = quote(a.query.strip().lower())
    out = sql(
        "SELECT b.id AS 밴드ID, b.name AS 밴드, u.name AS 밴드장, "
        "(SELECT count(*) FROM band_members m WHERE m.band_id = b.id AND m.left_at IS NULL) AS 인원, "
        "p.tier AS 요금제, p.expires_at AT TIME ZONE 'Asia/Seoul' AS 끝나는날_KST "
        "FROM bands b JOIN users u ON u.id = b.leader_id LEFT JOIN band_plans p ON p.band_id = b.id "
        f"WHERE position({q} in lower(b.name)) > 0 "
        f"   OR b.id IN (SELECT m.band_id FROM band_members m JOIN users w ON w.id = m.user_id "
        f"               WHERE lower(w.email) = {q} AND m.left_at IS NULL) "
        "ORDER BY b.id LIMIT 20;")
    print(out)
    print("밴드 이름의 일부, 또는 멤버의 가입 이메일(정확히)로 찾는다. 카카오 가입자는 이메일이 없을 수 있다 — 밴드 이름으로.")
    print("→ 맞는 밴드를 찾았으면: python tools/purchases.py link <번호> <밴드ID>")


def cmd_link(a):
    must_be_open(a.id)
    band = sql(f"SELECT b.id, b.name, p.tier, p.expires_at, p.store FROM bands b "
               f"LEFT JOIN band_plans p ON p.band_id = b.id WHERE b.id = {a.band_id};")
    if "(0 rows)" in band or "(0개 행)" in band:
        sys.exit(f"!! 밴드 {a.band_id} 가 없다. 아무것도 바꾸지 않았다.")
    cmd_show(a)
    print(band)
    confirm(f"{a.id}번 결제를 밴드 {a.band_id} 에 반영한다. 서버 배치가 10분 안에 PREMIUM 으로 올리고 Google 에 확인 처리한다.\n"
            f"(그 밴드에 이미 다른 구독이 살아 있으면 서버가 거부하고 미해결로 남는다 — 그때는 다른 밴드를 고르거나 close.)")
    sql(f"UPDATE unmatched_store_purchases SET target_band_id = {a.band_id} WHERE id = {a.id} AND resolved_at IS NULL;")
    print(f"예약했다. 10분 뒤 `python tools/purchases.py show {a.id}` 의 해결 칸에 「밴드 {a.band_id} 에 연결(운영자)」 이 나오면 끝.")


def cmd_close(a):
    if not a.memo.strip():
        sys.exit("!! 메모를 적는다 — 예: \"Console 에서 환불함\", \"결제자 확인 결과 중복 결제\".")
    must_be_open(a.id)
    cmd_show(a)
    confirm(f"{a.id}번을 반영 없이 닫는다. 메모: {a.memo}\n(돈은 그대로다 — 환불이 필요하면 Play Console 에서 먼저 한다.)")
    sql(f"UPDATE unmatched_store_purchases SET resolved_at = now(), resolution = {quote('운영자 종료: ' + a.memo[:180])} "
        f"WHERE id = {a.id} AND resolved_at IS NULL;")
    print("닫았다. 다음 매일 점검부터 이 건으로는 경보가 안 온다.")


# 훈련: 종류(알림 번호)별로 가짜 미해결 건을 넣어, 매일 점검 → 메일 → list/close 를 실제로 돌려 본다.
# 토큰이 drill- 로 시작해 진짜 결제와 섞이지 않고 drill-clean 이 이것만 지운다. 서버 배치가 10분마다 이 토큰을
# Google 에 물어 "없는 토큰" 이면 스스로 닫으므로, 넣은 뒤 바로 점검을 돌린다.
DRILL = {1: 2, 2: 1, 4: 3, 6: 1, 7: 2}   # Google 알림 번호: 건수 (뜻은 KIND)


def cmd_drill(a):
    rows = []
    n = 0
    for kind, count in DRILL.items():
        for i in range(count):
            n += 1
            rows.append(f"('GOOGLE_PLAY', 'drill-{kind}-{i + 1}-' || extract(epoch FROM now())::bigint, {kind}, "
                        f"'premium_yearly', 'GPA.DRILL-{kind}-{i + 1}', "
                        f"now() - interval '{a.days} days' + interval '{n} minutes')")
    print(f"넣을 것: {n}건 (새로 결제 3 · 1년 갱신 1 · 다시 구독 2 · 결제 다시 됨 2 · 유예 중 1), 받은 시각 약 {a.days}일 전")
    confirm("운영 DB 에 훈련용 미해결 건을 넣는다 (토큰 drill-…). 끝나면 drill-clean 으로 지운다.")
    sql("INSERT INTO unmatched_store_purchases "
        "(store, purchase_token, notification_type, product_id, order_id, received_at) VALUES\n"
        + ",\n".join(rows) + ";")
    cmd_list(a)
    print("→ 바로 점검(메일): gh workflow run prod-check.yml --ref main   — 서버 배치가 10분 안에 닫을 수 있다")


def cmd_drill_clean(_):
    print(sql("SELECT id, notification_type, resolved_at IS NOT NULL AS 해결됨, resolution "
              "FROM unmatched_store_purchases WHERE purchase_token LIKE 'drill-%' ORDER BY id;"))
    confirm("훈련용(drill-) 건을 모두 지운다. 진짜 결제 건은 건드리지 않는다.")
    print(sql("DELETE FROM unmatched_store_purchases WHERE purchase_token LIKE 'drill-%';"))


def main():
    parser = argparse.ArgumentParser(description="밴드를 못 찾은 결제 처리 (매일 점검 「밴드없는결제」)")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list", help="미해결 목록").set_defaults(fn=cmd_list)
    p = sub.add_parser("show", help="한 건 자세히")
    p.add_argument("id", type=int)
    p.set_defaults(fn=cmd_show)
    p = sub.add_parser("link", help="그 결제를 밴드에 반영 (서버 배치가 10분 안에)")
    p.add_argument("id", type=int)
    p.add_argument("band_id", type=int)
    p.set_defaults(fn=cmd_link)
    p = sub.add_parser("close", help="반영 없이 닫기")
    p.add_argument("id", type=int)
    p.add_argument("memo")
    p.set_defaults(fn=cmd_close)
    p = sub.add_parser("find-band", help="밴드 이름 일부 또는 멤버 가입 이메일로 밴드ID 찾기")
    p.add_argument("query")
    p.set_defaults(fn=cmd_find_band)
    p = sub.add_parser("drill", help="훈련: 종류별 가짜 미해결 건 넣기 (점검·메일 시험)")
    p.add_argument("--days", type=int, default=2, help="받은 시각을 며칠 전으로 (기본 2)")
    p.set_defaults(fn=cmd_drill)
    sub.add_parser("drill-clean", help="훈련용 건만 지우기").set_defaults(fn=cmd_drill_clean)
    args = parser.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
