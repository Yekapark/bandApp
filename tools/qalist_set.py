"""QALIST 세 파일의 항목 판정 갱신 + 상태별 파일 이동 + 집계 재계산.

    python tools/qalist_set.py ID 마크 "현재 판정 문장" "이번에 할 일 문장"
    python tools/qalist_set.py --recount

마크: ✅ 🟡 ❌ ⬜ 🔕(출시 비차단·알려진 한계) 📵(기기 없음·추후 시험)
✅ → docs/QALIST_DONE.md, 🔕·📵 → docs/QALIST_PARKED.md, 나머지 → docs/QALIST.md(진행할 것).
각 파일의 `<!-- items:start -->`~`<!-- items:end -->` 사이는 이 도구가 매번 다시 쓴다 — 손으로 고치지 말고
항목 블록(### ID … 다음 ### 전까지)만 고친 뒤 --recount 를 돌린다.
"""
import io
import os
import re
import sys

OPEN, DONE, PARKED = 'docs/QALIST.md', 'docs/QALIST_DONE.md', 'docs/QALIST_PARKED.md'
FILES = (OPEN, DONE, PARKED)
CHECKLIST = 'docs/QA_CHECKLIST.md'
MARKS = '✅🟡❌⬜🔕📵'
LABEL = {'✅': '✅ 모든 명시 조건 완료', '🟡': '🟡 일부 확인', '❌': '❌ 기대 불일치', '⬜': '⬜ 미실행',
         '🔕': '🔕 출시 비차단·알려진 한계', '📵': '📵 기기 없음·추후 시험'}
TOTAL_NAME = {'✅': '명시 조건 실제 확인', '🟡': '일부 확인·추가 조건 남음', '❌': '기대 불일치',
              '⬜': '실제 미실행·준비 조건 대기', '🔕': '출시 비차단·알려진 한계 (사용자 결정)',
              '📵': '기기 없음·추후 시험 (준비 불가 기기)'}
AREAS = [('AUTH', 'auth', '인증·계정'), ('BAND', 'band', '밴드·초대·멤버'), ('ROOM', 'room', '합주실·지도'),
         ('CAL', 'cal', '일정·참석·셋리스트'), ('REC', 'rec', '정기 일정'), ('SET', 'set', '정산'),
         ('POST', 'post', '게시판·신고·차단'), ('MEDIA', 'media', '사진·영상·보관'), ('PUSH', 'push', '알림·권한'),
         ('BILL', 'bill', '구독·결제·쿠폰'), ('PRIV', 'priv', '개인정보·운영 조치'), ('UI', 'ui', '화면·접근성·복구'),
         ('OPS', 'ops', '운영·스토어·출시')]
START, END = '<!-- items:start -->', '<!-- items:end -->'
TSTART, TEND = '<!-- totals:start -->', '<!-- totals:end -->'


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, t):
    if read(p) == t:
        return
    # 임시 파일에 쓰고 바꿔 끼운다 — 쓰다가 실패해도 원본이 잘리지 않게(Windows 에서 Errno 22 를 겪었다).
    io.open(p + '.tmp', 'w', encoding='utf-8', newline='').write(t)
    os.replace(p + '.tmp', p)


def mark_of(block):
    return re.match(r'### \S+ · \S+ · (\S)', block).group(1)


def dest(mark):
    return DONE if mark == '✅' else PARKED if mark in '🔕📵' else OPEN


def key(qid):
    p, n = qid.rsplit('-', 1)
    return [a[0] for a in AREAS].index(p), int(n)


def load():
    items = {}
    for f in FILES:
        body = read(f).split(START, 1)[1].split(END, 1)[0]
        for blk in re.split(r'\n(?=### )', body):
            if not blk.startswith('### '):
                continue
            lines = [l for l in blk.split('\n')
                     if not re.match(r'^<a id="[a-z0-9-]+"></a>$', l) and not re.match(r'^## ', l)
                     and not re.match(r'^(✅|🟡|❌|⬜|🔕|📵) \d+ · ', l)]
            qid = lines[0].split()[1]
            items[qid] = '\n'.join(lines).rstrip() + '\n'
    return items


def set_item(items, qid, mark, verdict, todo):
    blk = items.get(qid) or sys.exit(f'{qid} 없음')
    m = re.match(r'### (\S+) · (\S+) · .*', blk)
    blk = blk.replace(m.group(0), f'### {qid} · {m.group(2)} · {LABEL[mark]}', 1)
    blk = re.sub(r'^- \[[ x]\] \*\*시험:\*\*', f'- [{"x" if mark == "✅" else " "}] **시험:**', blk, count=1, flags=re.M)
    for k, v in (('현재 판정', verdict), ('이번에 할 일', todo)):
        if v == '-':  # 그대로 둔다
            continue
        line = f'- **{k}:** {v}'
        if re.search(rf'^- \*\*{k}:\*\*', blk, re.M):
            blk = re.sub(rf'^- \*\*{k}:\*\*.*$', lambda _: line, blk, count=1, flags=re.M)
        else:
            blk = re.sub(r'^(- \[[ x]\] \*\*시험:\*\*.*)$', lambda mm: mm.group(1) + '\n' + line, blk, count=1, flags=re.M)
    items[qid] = blk


def render(items):
    area_total = {a[0]: {k: 0 for k in MARKS} for a in AREAS}
    total = {k: 0 for k in MARKS}
    where = {}
    for qid, blk in items.items():
        mk = mark_of(blk)
        area_total[qid.rsplit('-', 1)[0]][mk] += 1
        total[mk] += 1
        where[qid] = dest(mk)
    for f in FILES:
        out = []
        for pre, anchor, name in AREAS:
            ids = sorted((q for q in items if q.startswith(pre + '-') and where[q] == f), key=key)
            if not ids:
                continue
            c = area_total[pre]
            out.append(f'<a id="{anchor}"></a>\n\n## {name} — 이 파일 {len(ids)}개\n\n'
                       + ' · '.join(f'{k} {c[k]}' for k in MARKS) + ' (영역 전체)\n')
            for q in ids:
                out.append(f'<a id="{q.lower()}"></a>\n\n{items[q]}')
        t = read(f)
        head, rest = t.split(START, 1)
        t = head + START + '\n\n' + '\n'.join(out) + '\n' + END + rest.split(END, 1)[1]
        if f == OPEN:
            n = sum(total.values())
            pct = lambda v: f'{v * 100 / n:.1f}%'
            rows = '\n'.join(f'| {k} {TOTAL_NAME[k]} | {total[k]} | {pct(total[k])} |' for k in MARKS)
            open_n = total['🟡'] + total['❌'] + total['⬜']
            tbl = (f'| 상태 | 개수 | 비율 |\n|---|---:|---:|\n{rows}\n| 전체 고유 ID | {n} | 100% |\n\n'
                   f'모든 조건 실제 확인 **{total["✅"]}/{n}={pct(total["✅"])}**. 이 파일에서 진행할 항목 **{open_n}개**'
                   f'(🟡{total["🟡"]}+❌{total["❌"]}+⬜{total["⬜"]}). 🔕{total["🔕"]}·📵{total["📵"]}는 QALIST_PARKED.md.')
            head, rest = t.split(TSTART, 1)
            t = head + TSTART + '\n' + tbl + '\n' + TEND + rest.split(TEND, 1)[1]
        write(f, t)
    # QA_CHECKLIST 의 근거 링크를 항목이 지금 있는 파일로 맞춘다.
    cl = read(CHECKLIST)
    fix = lambda m: f'{where.get(m.group(2).upper(), OPEN).split("/")[1]}#{m.group(2)}'
    write(CHECKLIST, re.sub(r'(QALIST(?:_DONE|_PARKED)?\.md)#([a-z]+-\d+)', fix, cl))
    print(' '.join(f'{k}{v}' for k, v in total.items()), f'/ {sum(total.values())}')


if __name__ == '__main__':
    its = load()
    if sys.argv[1] != '--recount':
        set_item(its, *sys.argv[1:5])
    render(its)
