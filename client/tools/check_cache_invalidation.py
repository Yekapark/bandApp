"""쓰기 후 provider 캐시를 비우지 않는 화면을 찾는다.

    python tools/check_cache_invalidation.py

이 앱의 `FutureProvider` 는 하나도 `autoDispose` 가 아니다 — 캐시가 영구히 남는다.
그래서 무언가를 저장한 뒤 화면 로컬 상태(`setState`)만 갱신하면, 그 화면을 벗어나는 순간
로컬 상태가 사라지고 다시 들어올 때 **저장 전 값이 다시 그려진다**. 서버에는 제대로
들어가 있는데 화면만 거짓말을 하는 형태라 눈치채기 어렵다.

실제로 참석 여부(2026-09-04)와 정산 납부 체크(2026-09-05)에서 같은 버그가 났다.
쓰기 메서드를 새로 만들 때 이 검사를 돌려 빠뜨린 곳을 잡는다.

판정: 아래 중 하나면 통과한다.
  1. 그 메서드가 직접 `ref.invalidate` / `.refresh()` 를 부른다
  2. 같은 파일에서 무효화를 하는 헬퍼를 부른다 (예: `_refreshCaches(bandId)`)
  3. 그 메서드를 부르는 쪽이 모두 위 조건을 만족한다 (헬퍼로 쪼갠 경우)
"""

import glob
import os
import re
import sys

# 윈도우 콘솔이 cp949 라 한글·기호 출력에서 죽는다.
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

WRITE = re.compile(
    r"RepositoryProvider\)\s*\.\s*("
    r"create|update|delete|remove|respond|markPaid|recalculate"
    r"|addSetlistItem|updateSetlistItem|deleteSetlistItem|reorderSetlist"
    r"|leave|kick|delegate|block|unblock|issue|revoke|join|changeTier"
    r"|approve|reject|cancel|uploadMedia|deleteMedia|withdraw|setPermission)",
    re.S,
)
INVALIDATES = re.compile(r"ref\.invalidate|\.refresh\(")
SPLIT_METHOD = re.compile(r"\n  (?=(?:Future|void|Widget)[\w<>?, ]* _?\w+\()")
METHOD_NAME = re.compile(r"\s*(?:Future|void|Widget)[\w<>?, ]* (_?\w+)\(")


def methods_of(src):
    """{메서드 이름: 본문}. 대략적인 분해지만 이 코드베이스 스타일에는 충분하다."""
    out = {}
    for block in SPLIT_METHOD.split(src):
        m = METHOD_NAME.match(block)
        if m:
            out[m.group(1)] = block
    return out


def check(path):
    """캐시를 안 비우는 쓰기 메서드 이름들."""
    src = open(path, encoding="utf-8").read()
    methods = methods_of(src)
    clears = {n for n, b in methods.items() if INVALIDATES.search(b)}

    # 무효화 헬퍼를 부르면 그 메서드도 비우는 것으로 본다(전이).
    for _ in range(len(methods)):
        grew = {n for n, b in methods.items()
                if n not in clears and any(f"{c}(" in b for c in clears)}
        if not grew:
            break
        clears |= grew

    bad = []
    for name, body in methods.items():
        if not WRITE.search(body) or name in clears:
            continue
        # 호출하는 쪽이 모두 비우면 헬퍼로 쪼갠 것이라 통과.
        callers = [n for n, b in methods.items() if n != name and f"{name}(" in b]
        if callers and all(c in clears for c in callers):
            continue
        bad.append(name)
    return bad


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "lib/features"
    files = sorted(
        set(glob.glob(f"{root}/*/presentation/**/*.dart", recursive=True))
        | set(glob.glob(f"{root}/*/presentation/*.dart"))
    )
    found = False
    for path in files:
        for name in check(path):
            found = True
            rel = os.path.relpath(path, root).replace(os.sep, "/")
            print(f"  X {rel}  ->  {name}()  쓰기 후 캐시 무효화 없음")
    if found:
        print("\n저장 후 ref.invalidate 로 관련 프로바이더를 비워야 한다.")
        return 1
    print(f"검사 {len(files)}개 파일: 쓰기 후 캐시 무효화 누락 없음")
    return 0


if __name__ == "__main__":
    sys.exit(main())
