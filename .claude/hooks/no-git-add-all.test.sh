# 훅이 막아야 할 것과 통과시켜야 할 것. 고친 뒤 이걸 돌린다:  sh .claude/hooks/no-git-add-all.test.sh
H=".claude/hooks/no-git-add-all.sh"
fail=0
check() {
  printf '{"tool_name":"Bash","tool_input":{"command":"%s"}}' "$2" | sh "$H" 2>/dev/null
  rc=$?
  want=2; [ "$1" = pass ] && want=0
  if [ "$rc" != "$want" ]; then echo "  X [$1 기대] rc=$rc : $2"; fail=1; else echo "  . [$1] $2"; fi
}
check block "git add -A"
check block "git add ."
check block "git add --all"
check block "git add -Av"
check block "git commit -am 'x'"
check block "git commit -a"
check block "cd client && git add -A"
check pass  "git add client/pubspec.yaml"
check pass  "git add docs/TROUBLESHOOTING.md docs/BACKLOG.md"
check pass  "git commit -m 'fix: 뭔가'"
check pass  "git commit --amend --no-edit"
check pass  "git status --short"
check pass  "git add ./src/main/java/A.java"
check pass  "ls -A"
exit $fail
