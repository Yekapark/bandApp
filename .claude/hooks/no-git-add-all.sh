#!/bin/sh
# `git add -A` · `git add .` · `git commit -a` 를 Claude 가 실행하기 전에 막는다.
#
# 왜 있나 — 이 저장소는 **공개**고 작업 폴더에 `.env.prod` 같은 비밀 파일이 함께 있다.
# 2026-09-08 에 서버에서 내려받은 `env.prod.server` 가 `git add -A` 에 휩쓸려 푸시됐고,
# DB·Redis 비밀번호, JWT 서명키, R2 키, 카카오 어드민 키를 전부 교체해야 했다.
#
# `.githooks/pre-commit` 이 이미 있지만 그건 **이름을 아는 파일**만 막는다.
# 그때 그 파일은 새로 만들어진 이름이라 어떤 목록에도 없었다 — 그래서 "전부 담기" 라는
# 행위 자체를 여기서 막는다. 담을 파일은 하나씩 이름을 적는다.
#
# PreToolUse(Bash) 훅. exit 2 = 실행을 막고 stderr 를 Claude 에게 돌려준다.
#
# stdin 은 tool_input.command 가 든 JSON 이다. jq 도 없고 JSON 이스케이프를 따라가는
# sed 도 쓰지 않는다 — 구두점을 공백으로 바꿔 토큰만 남긴다. command 가 아닌 필드에
# 같은 문자열이 있어도 걸리지만, 그건 어차피 막을 명령이라 안전한 쪽으로 틀린다.

hit=$(tr '"{},:' '     ' | awk '
{
  for (i = 1; i <= NF; i++) {
    if ($i != "git") continue
    verb = $(i + 1)
    if (verb != "add" && verb != "commit") continue
    for (j = i + 2; j <= NF; j++) {
      t = $j
      if (t == "&&" || t == "||" || t == ";" || t == "|") break
      if (verb == "add") {
        if (t == "." || t == "*" || t == ":/" || t == "--all") { print "git add " t; exit }
        if (t ~ /^-[A-Za-z]*A[A-Za-z]*$/)                      { print "git add " t; exit }
      } else {
        if (t == "--all")                                      { print "git commit " t; exit }
        if (t ~ /^-[A-Za-z]*a[A-Za-z]*$/)                      { print "git commit " t; exit }
      }
    }
  }
}')

[ -z "$hit" ] && exit 0

cat >&2 <<MSG
  ✗ 막았다 — "$hit" 은 이 저장소에서 쓰지 않는다.

  공개 저장소이고 작업 폴더에 .env.prod 같은 비밀 파일이 같이 있다.
  전에 이렇게 서버 비밀값이 공개로 푸시돼 전부 교체해야 했다
  (docs/TROUBLESHOOTING.md 2026-09-08 항목).

  대신:
      git status --short          # 담길 것을 눈으로 확인한다
      git add <파일> <파일>        # 이름을 하나씩 적는다
      git commit -m "..."         # -a 없이

  모르는 파일이 보이면 담지 않는다.
MSG
exit 2
