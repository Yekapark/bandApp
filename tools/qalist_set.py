"""QALIST 항목 판정 갱신 + 섹션·전체 집계 재계산.

    python tools/qalist_set.py ID ✅|🟡|❌|⬜ "현재 판정 문장" "이번에 할 일 문장"
    python tools/qalist_set.py --recount
"""
import io
import re
import sys

PATH = 'docs/QALIST.md'
LABEL = {'✅': '✅ 모든 명시 조건 완료', '🟡': '🟡 일부 확인', '❌': '❌ 기대 불일치', '⬜': '⬜ 미실행'}
MARKS = '✅🟡❌⬜'


def set_item(t, qid, mark, verdict, todo):
    m = re.search(rf'^### {re.escape(qid)} · (\S+) · .*$', t, re.M)
    if not m:
        sys.exit(f'{qid} 없음')
    start = m.start()
    end = t.find('\n### ', m.end())
    end = len(t) if end < 0 else end
    block = t[start:end]
    block = block.replace(m.group(0), f'### {qid} · {m.group(1)} · {LABEL[mark]}', 1)
    block = re.sub(r'^- \[[ x]\] \*\*시험:\*\*', f'- [{"x" if mark == "✅" else " "}] **시험:**', block, count=1, flags=re.M)
    for key, val in (('현재 판정', verdict), ('이번에 할 일', todo)):
        line = f'- **{key}:** {val}'
        if re.search(rf'^- \*\*{key}:\*\*.*$', block, re.M):
            block = re.sub(rf'^- \*\*{key}:\*\*.*$', lambda _: line, block, count=1, flags=re.M)
        else:  # 시험 줄 바로 뒤에 넣는다
            block = re.sub(r'^(- \[[ x]\] \*\*시험:\*\*.*)$', lambda mm: mm.group(1) + '\n' + line, block, count=1, flags=re.M)
    return t[:start] + block + t[end:]


def recount(t):
    total = {k: 0 for k in MARKS}
    secs = list(re.finditer(r'^## .*$', t, re.M))
    out, pos = [], 0
    for i, s in enumerate(secs):
        end = secs[i + 1].start() if i + 1 < len(secs) else len(t)
        body = t[s.start():end]
        heads = re.findall(r'^### [A-Z]+-\d+ · \S+ · (\S)', body, re.M)
        if heads:
            c = {k: heads.count(k) for k in MARKS}
            for k in MARKS:
                total[k] += c[k]
            body = re.sub(r'^✅ \d+ · 🟡 \d+ · ❌ \d+ · ⬜ \d+$',
                          f'✅ {c["✅"]} · 🟡 {c["🟡"]} · ❌ {c["❌"]} · ⬜ {c["⬜"]}', body, count=1, flags=re.M)
        out.append(t[pos:s.start()] + body)
        pos = end
    t = ''.join(out) + t[pos:]
    n = sum(total.values())
    pct = lambda v: f'{v * 100 / n:.1f}%'
    rows = {'✅': '명시 조건 실제 확인', '🟡': '일부 확인·추가 조건 남음', '❌': '기대 불일치', '⬜': '실제 미실행·준비 조건 대기'}
    for k, name in rows.items():
        t = re.sub(rf'^\| {k} [^|]*\| \d+ \| [\d.]+% \|$', f'| {k} {name} | {total[k]} | {pct(total[k])} |', t, count=1, flags=re.M)
    started = n - total['⬜']
    t = re.sub(r'일부라도 실제 진행 \*\*\d+/\d+=[\d.]+%\*\*, 모든 조건 실제 확인 \*\*\d+/\d+=[\d.]+%\*\*\. 완료가 남은 항목 \*\*\d+개\([\d+]+\)\*\*',
               f'일부라도 실제 진행 **{started}/{n}={pct(started)}**, 모든 조건 실제 확인 **{total["✅"]}/{n}={pct(total["✅"])}**. '
               f'완료가 남은 항목 **{n - total["✅"]}개({total["🟡"]}+{total["❌"]}+{total["⬜"]})**', t, count=1)
    print(' '.join(f'{k}{v}' for k, v in total.items()), f'/ {n}')
    return t


if __name__ == '__main__':
    t = io.open(PATH, encoding='utf-8').read()
    if sys.argv[1] != '--recount':
        t = set_item(t, *sys.argv[1:5])
    t = recount(t)
    io.open(PATH, 'w', encoding='utf-8', newline='').write(t)
