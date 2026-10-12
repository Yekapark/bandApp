"""밴드를 못 찾은 결제 처리 도구 — 매일 점검의 「밴드없는결제」 경보를 끝낸다(QA BILL-31, V24 unmatched_store_purchases).

돈은 들어왔는데 서버가 PREMIUM 을 줄 밴드를 못 찾은 구독이 남는다. 대부분은 서버 배치(10분마다)가 스스로 닫는다 —
밴드에 붙었거나, Google 이 환불·만료했으면. 확인 처리를 못 받은 구매는 **결제 3일 뒤 Google 이 자동 환불**하므로
아무것도 안 해도 경보는 꺼진다. 결제한 밴드를 알면 그 밴드에 반영할 수 있다.

    python tools/purchases.py list                     # 미해결 목록 (매일 점검 메일에 나온 번호)
    python tools/purchases.py show 3                   # 3번 자세히 — 주문 ID 로 Play Console › 주문 관리에서 찾는다
    python tools/purchases.py link 3 123               # 3번 결제를 밴드 123 에 반영 — 서버 배치가 10분 안에 PREMIUM·확인 처리
    python tools/purchases.py close 3 "환불함"          # 반영 없이 닫기 (Console 에서 직접 환불했을 때 등)

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


ROW = ("SELECT id AS 번호, received_at AT TIME ZONE 'Asia/Seoul' AS 받은시각_KST, notification_type AS 알림, "
       "product_id AS 상품, order_id AS 주문ID, left(purchase_token, 12) || '…' AS 토큰, "
       "(received_at + interval '3 days') AT TIME ZONE 'Asia/Seoul' AS 자동환불_예정_KST, "
       "target_band_id AS 반영할밴드, resolved_at AT TIME ZONE 'Asia/Seoul' AS 해결_KST, resolution AS 해결 "
       "FROM unmatched_store_purchases")


def cmd_list(_):
    print(sql(f"{ROW} WHERE resolved_at IS NULL ORDER BY id;"))
    print("알림 번호: 1 복구 · 2 갱신 · 4 구매 · 6 유예 · 7 재시작(만료 뒤 Play 「다시 구독」 등)")
    print("결제한 밴드를 알면: python tools/purchases.py link <번호> <밴드ID>  /  모르면 두면 3일 뒤 자동 환불로 닫힌다.")


def cmd_show(a):
    print(sql(f"{ROW} WHERE id = {a.id};"))
    # 이어받은 이전 토큰이 예전에 어디 붙어 있었는지 — 밴드가 지워졌으면 안 나온다. 결제한 사람에게 물어보는 게 확실하다.
    print(sql(f"SELECT b.id AS 밴드ID, b.name AS 밴드, p.tier, p.expires_at "
              f"FROM unmatched_store_purchases u JOIN band_plans p ON p.purchase_token = u.linked_purchase_token "
              f"JOIN bands b ON b.id = p.band_id WHERE u.id = {a.id};"))
    print("주문ID 로 Play Console › 주문 관리에서 결제한 사람을 찾을 수 있다. 위 표가 비었으면 이전 구독의 밴드가 남아 있지 않다.")


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
    args = parser.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
