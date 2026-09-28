"""신고 처리 도구 — 글 숨김·계정 정지·해제·신고 정리를 운영 서버에 한 번에 한다(LAUNCH_REVIEW P7).

앱에는 운영자 역할이 없다(남의 밴드 글을 앱으로 지울 수 없다). 그래서 SSH 로 운영 DB·Redis 에 직접 쓴다.
무엇을 바꿀지 먼저 보여 주고 `yes` 를 입력해야 실행한다. 절차와 판단 기준은 docs/MODERATION.md.

    python tools/moderate.py reports                       # 처리 안 된 신고 목록
    python tools/moderate.py post 123                      # 글 123 내용 보기 (신고 확인용)
    python tools/moderate.py hide-post 123                 # 글 123 숨김 + 첨부 삭제 예약 + 관련 신고 처리 완료
    python tools/moderate.py hide-media 77                 # 첨부 77 이 달린 글을 통째로 숨김 (첨부 하나만 지우는 기능은 없다)
    python tools/moderate.py suspend 45 --days 7 --reason "불법촬영물 게시(신고 12)"
    python tools/moderate.py suspend 45 --forever --reason "..." --hide-posts   # 그 사람 글 전부 숨김까지
    python tools/moderate.py unsuspend 45                  # 정지 해제(이의 인정 등)
    python tools/moderate.py resolve 12                    # 조치 없이 신고만 처리 완료

- 정지하면: 로그인·토큰 갱신이 막히고(앱에 기간·문의처 안내), 쓰던 토큰도 즉시 막히고, 그 사람 기기로 가던
  푸시가 끊긴다. 밴드 멤버십·글은 그대로다(글까지 내리려면 --hide-posts). 기간이 지나면 저절로 풀린다.
- 숨긴 글의 사진·영상은 매일 04:15 만료 배치가 저장소(R2)에서 지운다. 그 전에도 앱에서는 안 보인다.
- **본인 고지는 따로 한다** — 약관 제14조. 정지하면 가입 이메일로 사유·기간·이의 방법을 보낸다(출력에 주소가 나온다).

필요한 것: 배포 키 `~/.ssh/bandule_deploy` (docs/NEW_PC_SETUP.md). 다른 키·호스트는 환경변수 BANDULE_SSH_KEY·BANDULE_HOST.
"""

import argparse
import os
import subprocess
import sys

HOST = os.environ.get("BANDULE_HOST", "root@64.176.231.126")
KEY = os.path.expanduser(os.environ.get("BANDULE_SSH_KEY", "~/.ssh/bandule_deploy"))
COMPOSE = "cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod"

# access 토큰 수명(application.yml jwt.access-token-ttl = 30분). 차단 표시는 이만큼만 있으면 된다 — 그 뒤엔 토큰
# 갱신이 DB 의 정지 기록을 보고 막는다. 값 "S" 는 서버(AccessTokenBlocklist.SUSPENDED)와 약속한 것이다.
BLOCK_SECONDS = 30 * 60
FOREVER = "9999-12-31T00:00:00Z"


def ssh(remote_cmd, stdin_text=None):
    """원격 명령을 돌리고 표준출력을 돌려준다. 실패하면 멈춘다."""
    result = subprocess.run(
        ["ssh", "-i", KEY, "-o", "BatchMode=yes", HOST, remote_cmd],
        input=stdin_text, capture_output=True, text=True, encoding="utf-8",
    )
    if result.returncode != 0:
        sys.exit(f"!! 서버 명령 실패 (exit {result.returncode})\n{result.stderr.strip()}")
    return result.stdout


def sql(statements, *, table=True):
    """SQL 을 표준입력으로 넘긴다 — 사유 문구의 따옴표가 셸을 거치지 않게. 한 트랜잭션으로 묶는다."""
    flags = "-v ON_ERROR_STOP=1 -1 -f -" + ("" if table else " -At")
    return ssh(f"{COMPOSE} exec -T postgres psql -U bandapp -d bandapp {flags}", statements)


def redis(*args):
    # 비밀번호는 컨테이너 안의 환경변수로만 쓴다 — 로컬·명령줄에 나오지 않게.
    joined = " ".join(str(a) for a in args)
    return ssh(f"{COMPOSE} exec -T redis sh -c 'REDISCLI_AUTH=\"$REDIS_PASSWORD\" redis-cli {joined}'").strip()


def quote(text):
    return "'" + text.replace("'", "''") + "'"


def confirm(what):
    print()
    print(what)
    if input("진행하려면 yes 를 입력: ").strip() != "yes":
        sys.exit("취소했다. 아무것도 바꾸지 않았다.")


def resolve_reports_sql(condition):
    return f"UPDATE reports SET status = 'RESOLVED' WHERE status = 'OPEN' AND ({condition});\n"


def hide_post_sql(post_id):
    # 앱의 글 삭제와 같은 표시(deleted_at). 첨부는 보관기한을 지금으로 당겨 만료 배치가 R2 에서 지우게 한다 —
    # 여기서 EXPIRED 로 바꾸면 배치가 건너뛰어 파일이 저장소에 남는다.
    return (
        f"UPDATE board_posts SET deleted_at = now() WHERE id = {post_id} AND deleted_at IS NULL;\n"
        f"UPDATE media_attachments SET expires_at = now() WHERE board_post_id = {post_id} AND status = 'READY';\n"
        + resolve_reports_sql(
            f"(target_type = 'POST' AND target_id = {post_id}) OR (target_type = 'MEDIA' AND target_id IN "
            f"(SELECT id FROM media_attachments WHERE board_post_id = {post_id}))")
    )


def cmd_reports(_):
    print(sql("SELECT id, target_type, target_id, reporter_id, left(reason, 60) AS reason, created_at "
              "FROM reports WHERE status = 'OPEN' ORDER BY created_at DESC LIMIT 30;"))


def cmd_post(a):
    print(sql(f"SELECT p.id, p.band_id, b.name AS band, p.author_id, u.name AS author, p.title, p.content, "
              f"p.created_at, p.deleted_at FROM board_posts p JOIN bands b ON b.id = p.band_id "
              f"JOIN users u ON u.id = p.author_id WHERE p.id = {a.post_id};"))
    print(sql(f"SELECT id, type, status, size_bytes, uploaded_at FROM media_attachments "
              f"WHERE board_post_id = {a.post_id} ORDER BY id;"))


def cmd_hide_post(a):
    cmd_post(a)
    confirm(f"글 {a.post_id} 을 숨기고, 첨부를 저장소 삭제 대기로 돌리고, 관련 신고를 처리 완료로 바꾼다.")
    print(sql(hide_post_sql(a.post_id)))
    print("완료. 첨부는 오늘 04:15 배치가 저장소에서 지운다.")


def cmd_hide_media(a):
    post_id = sql(f"SELECT board_post_id FROM media_attachments WHERE id = {a.media_id};", table=False).strip()
    if not post_id:
        sys.exit(f"!! 첨부 {a.media_id} 가 없다.")
    print(f"첨부 {a.media_id} 는 글 {post_id} 에 달려 있다. 글을 통째로 숨긴다.")
    a.post_id = int(post_id)
    cmd_hide_post(a)


def cmd_suspend(a):
    if not a.forever and not a.days:
        sys.exit("!! --days N 또는 --forever 중 하나를 준다.")
    if not a.reason.strip():
        sys.exit("!! --reason 에 사유(운영 기록)를 적는다. 신고 번호를 같이 적어 두면 나중에 찾기 쉽다.")
    print(sql(f"SELECT id, name, email, social_provider, deleted_at, suspended_until, suspension_reason "
              f"FROM users WHERE id = {a.user_id};"))
    if a.hide_posts:
        print(sql(f"SELECT id, band_id, title, created_at FROM board_posts "
                  f"WHERE author_id = {a.user_id} AND deleted_at IS NULL ORDER BY id;"))
    until = quote(FOREVER) if a.forever else f"now() + interval '{a.days} days'"
    period = "영구" if a.forever else f"{a.days}일"
    confirm(f"사용자 {a.user_id} 를 {period} 정지한다. 쓰던 토큰을 막고 푸시를 끊는다."
            + (" 이 사람의 글도 모두 숨긴다." if a.hide_posts else ""))

    statements = (
        f"UPDATE users SET suspended_until = {until}::timestamptz, suspension_reason = {quote(a.reason.strip()[:200])} "
        f"WHERE id = {a.user_id};\n"
        # 정지된 사람의 기기로 밴드 알림(일정·정산·글)이 계속 가지 않게.
        f"DELETE FROM device_tokens WHERE user_id = {a.user_id};\n"
        + resolve_reports_sql(f"target_type = 'USER' AND target_id = {a.user_id}")
    )
    if a.hide_posts:
        ids = sql(f"SELECT id FROM board_posts WHERE author_id = {a.user_id} AND deleted_at IS NULL;",
                  table=False).split()
        statements += "".join(hide_post_sql(int(i)) for i in ids)
    print(sql(statements))
    # DB 를 먼저 — 차단 표시만 있고 DB 가 안 바뀌면 30분 뒤 저절로 풀려 버린다.
    print("Redis:", redis("SET", f"auth:blocked:{a.user_id}", "S", "EX", BLOCK_SECONDS))
    print(sql(f"SELECT id, email, suspended_until FROM users WHERE id = {a.user_id};"))
    print("완료. 위 이메일로 사유·기간·이의 방법(notice@bandule.com, 7일 안)을 보낸다 — 약관 제14조.")


def cmd_unsuspend(a):
    print(sql(f"SELECT id, name, email, suspended_until, suspension_reason FROM users WHERE id = {a.user_id};"))
    confirm(f"사용자 {a.user_id} 의 정지를 지금 푼다. 사유 기록은 남긴다.")
    # 사유는 지우지 않는다 — 언제 왜 정지됐었는지가 다음 판단의 근거다.
    print(sql(f"UPDATE users SET suspended_until = now() WHERE id = {a.user_id} AND suspended_until > now();"))
    print("Redis:", redis("DEL", f"auth:blocked:{a.user_id}"))
    print("완료. 다시 로그인하면 된다(기기 푸시는 로그인하면 다시 등록된다).")


def cmd_resolve(a):
    print(sql(f"SELECT id, target_type, target_id, reason, status FROM reports WHERE id = {a.report_id};"))
    confirm(f"신고 {a.report_id} 를 조치 없이 처리 완료로 바꾼다.")
    print(sql(f"UPDATE reports SET status = 'RESOLVED' WHERE id = {a.report_id};"))


def main():
    parser = argparse.ArgumentParser(description="밴듈 신고 처리 (docs/MODERATION.md)")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("reports", help="처리 안 된 신고 목록").set_defaults(fn=cmd_reports)
    p = sub.add_parser("post", help="글 내용 보기")
    p.add_argument("post_id", type=int)
    p.set_defaults(fn=cmd_post)
    p = sub.add_parser("hide-post", help="글 숨김 + 첨부 삭제 예약 + 신고 처리")
    p.add_argument("post_id", type=int)
    p.set_defaults(fn=cmd_hide_post)
    p = sub.add_parser("hide-media", help="첨부가 달린 글을 숨김")
    p.add_argument("media_id", type=int)
    p.set_defaults(fn=cmd_hide_media)
    p = sub.add_parser("suspend", help="계정 이용 정지")
    p.add_argument("user_id", type=int)
    p.add_argument("--days", type=int)
    p.add_argument("--forever", action="store_true")
    p.add_argument("--reason", required=True)
    p.add_argument("--hide-posts", action="store_true", help="이 사람의 글도 모두 숨긴다")
    p.set_defaults(fn=cmd_suspend)
    p = sub.add_parser("unsuspend", help="정지 해제")
    p.add_argument("user_id", type=int)
    p.set_defaults(fn=cmd_unsuspend)
    p = sub.add_parser("resolve", help="조치 없이 신고만 처리 완료")
    p.add_argument("report_id", type=int)
    p.set_defaults(fn=cmd_resolve)
    args = parser.parse_args()
    if getattr(args, "days", None) is not None and args.days <= 0:
        sys.exit("!! --days 는 1 이상.")
    args.fn(args)


if __name__ == "__main__":
    main()
