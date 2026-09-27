#!/bin/sh
# CLAUDE.md 의 규칙 중 **기계로 확인되는 것**만 검사한다. CI 에서 돌지만 로컬에서도 된다:
#
#     sh tools/check-repo-rules.sh
#
# 여기 없는 규칙(트랜잭션 안 외부 HTTP 호출, Controller 엔티티 노출 등)은 grep 으로
# 판단하면 오탐이 많아 일부러 뺐다. 그건 리뷰가 본다.

cd "$(dirname "$0")/.." || exit 1
fail=0

# ---------------------------------------------------------------- 1. 엔티티 Lombok
#
# 금지 이유는 CLAUDE.md 에 있다 — 양방향 연관관계에서 무한 재귀(@ToString/@EqualsAndHashCode),
# 지연로딩 강제 초기화, setter 로 상태를 바꿔 도메인 의미가 사라지는 것.
# 지금 위반 0건이다. 0건으로 유지하는 쪽이 터진 뒤에 찾는 것보다 싸다.

echo "== 엔티티 Lombok 금지 애너테이션"
banned='@Data|@Setter|@EqualsAndHashCode|@ToString|@AllArgsConstructor'
hits=$(find src/main/java -path '*/entity/*.java' -print0 2>/dev/null \
       | xargs -0 grep -nE "$banned" 2>/dev/null)
if [ -n "$hits" ]; then
    echo "$hits" | sed 's/^/   /'
    echo "   -> 엔티티에는 @Getter, @NoArgsConstructor(access = PROTECTED), @Builder 만 쓴다."
    echo "      상태 변경은 setter 가 아니라 의미 있는 메서드로 표현한다."
    fail=1
else
    echo "   통과"
fi

# ---------------------------------------------------------------- 2. 비밀 파일 추적
#
# `.githooks/pre-commit` 이 커밋 시점에 막지만 그건 **훅이 켜져 있는 PC 에서만** 돈다
# (`git config core.hooksPath .githooks`). 안 켜진 PC·`ALLOW_SECRET_COMMIT`·다른 도구로
# 들어온 것을 여기서 마지막으로 잡는다. 이 저장소는 공개라 한 번 올라가면 못 지운다.
#
# ponytail: 패턴 목록이 .githooks/pre-commit 과 두 벌이다. 한쪽을 고치면 다른 쪽도 고친다.
# (훅은 staged 를, 여기는 tracked 를 보므로 입력이 달라 한 파일로 합치려면 훅을 손대야 한다 —
#  잘 돌고 있는 방어선을 DRY 때문에 건드리지 않는다. 목록이 자주 바뀌면 그때 합친다.)

echo "== 비밀값 파일이 저장소에 추적되고 있는지"
blocked=""
for f in $(git ls-files); do
    case "$(basename "$f")" in
        *.example|*.sample|*.template) continue ;;
    esac
    case "$f" in
        .env|.env.*|env.*|*/.env|*/.env.*)                    blocked="$blocked $f" ;;
        *.jks|*.keystore|*.p12|*.pfx|*.pem|*.ppk)             blocked="$blocked $f" ;;
        *key.properties|*/local.properties|local.properties)  blocked="$blocked $f" ;;
        *google-services.json|*GoogleService-Info.plist)      blocked="$blocked $f" ;;
        *dart_defines.json)                                   blocked="$blocked $f" ;;
        secrets/*|*/secrets/*)                                blocked="$blocked $f" ;;
        *-adminsdk-*.json|*serviceAccount*.json)              blocked="$blocked $f" ;;
        id_rsa|id_ed25519|*/id_rsa|*/id_ed25519)              blocked="$blocked $f" ;;
    esac
done
if [ -n "$blocked" ]; then
    for f in $blocked; do echo "   $f"; done
    echo "   -> 공개 저장소다. 값이 들어 있었다면 **파일을 지우는 것으로 끝나지 않는다** —"
    echo "      노출된 값을 전부 교체한다 (docs/TROUBLESHOOTING.md 2026-09-08)."
    fail=1
else
    echo "   통과"
fi

exit $fail
