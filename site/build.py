"""약관 문서(마크다운)를 게시용 HTML 로 바꾼다.

    pip install markdown
    python site/build.py

`docs/legal/` 의 마크다운이 **유일한 원본**이다. 법률 문서를 두 벌 두면 언젠가 갈라지고,
갈라진 약관은 그 자체가 분쟁거리가 된다. 그래서 HTML 은 손으로 고치지 않고 여기서 만든다.

**아직 안 채운 자리(`⟨⟩`)가 남아 있으면 빌드가 멈춘다.** 운영자명이나 시행일이 비어 있는
방침이 그대로 올라가는 것을 막는다.
"""

import re
import sys
from pathlib import Path

try:
    import markdown
except ImportError:
    sys.exit("!! markdown 이 없다:  pip install markdown")

ROOT = Path(__file__).resolve().parent.parent
LEGAL = ROOT / "docs/legal"
OUT = ROOT / "site"

PAGES = [
    ("privacy", "개인정보처리방침", LEGAL / "privacy-policy-ko.md"),
    ("terms", "이용약관", LEGAL / "terms-ko.md"),
]

TEMPLATE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title} · 밴듈</title>
<style>
  :root {{
    color-scheme: light dark;
    --bg: #ffffff; --fg: #1a1a1a; --dim: #5a5a5a;
    --line: #e2e2e2; --accent: #e2622a; --card: #fafafa;
  }}
  @media (prefers-color-scheme: dark) {{
    :root {{
      --bg: #141216; --fg: #ece9ee; --dim: #a09aa6;
      --line: #2e2a32; --accent: #ff7a3d; --card: #1c191f;
    }}
  }}
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0; background: var(--bg); color: var(--fg);
    font-family: "Pretendard", -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo",
                 "Malgun Gothic", "Noto Sans KR", sans-serif;
    font-size: 16px; line-height: 1.85;
    word-break: keep-all; overflow-wrap: break-word;
  }}
  .wrap {{ max-width: 720px; margin: 0 auto; padding: 32px 20px 80px; }}
  header {{ padding-bottom: 20px; border-bottom: 1px solid var(--line); margin-bottom: 32px; }}
  header a {{ font-weight: 800; font-size: 15px; color: var(--accent); text-decoration: none; letter-spacing: .5px; }}
  h1 {{ font-size: 26px; line-height: 1.4; margin: 0 0 24px; }}
  h2 {{ font-size: 18px; margin: 44px 0 12px; padding-top: 8px; }}
  h3 {{ font-size: 15px; margin: 28px 0 8px; color: var(--dim); }}
  p, li {{ font-size: 15px; }}
  ul, ol {{ padding-left: 22px; }}
  li {{ margin: 6px 0; }}
  strong {{ font-weight: 700; }}
  hr {{ border: 0; border-top: 1px solid var(--line); margin: 36px 0; }}
  blockquote {{
    margin: 20px 0; padding: 14px 18px;
    background: var(--card); border-left: 3px solid var(--accent); border-radius: 0 8px 8px 0;
  }}
  blockquote > :first-child {{ margin-top: 0; }}
  blockquote > :last-child {{ margin-bottom: 0; }}
  blockquote h2, blockquote h3 {{ margin-top: 4px; font-size: 16px; }}
  /* 표는 좁은 화면에서 가로로만 스크롤되게 한다 — 본문이 옆으로 밀리면 못 읽는다. */
  .table-wrap {{ overflow-x: auto; margin: 18px 0; }}
  table {{ border-collapse: collapse; width: 100%; min-width: 380px; font-size: 14px; }}
  th, td {{ border: 1px solid var(--line); padding: 9px 12px; text-align: left; vertical-align: top; }}
  th {{ background: var(--card); font-weight: 700; }}
  footer {{ margin-top: 56px; padding-top: 20px; border-top: 1px solid var(--line);
            color: var(--dim); font-size: 13px; }}
  footer a {{ color: var(--accent); }}
</style>
</head>
<body>
<div class="wrap">
  <header><a href="/">밴듈 BANDULE</a></header>
  {body}
  <footer>
    <a href="/privacy/">개인정보처리방침</a> · <a href="/terms/">이용약관</a>
  </footer>
</div>
</body>
</html>
"""

INDEX = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>밴듈 · 밴드 합주 관리</title>
<style>
  :root {{ color-scheme: light dark; --bg:#fff; --fg:#1a1a1a; --dim:#5a5a5a; --accent:#e2622a; }}
  @media (prefers-color-scheme: dark) {{
    :root {{ --bg:#141216; --fg:#ece9ee; --dim:#a09aa6; --accent:#ff7a3d; }}
  }}
  body {{ margin:0; background:var(--bg); color:var(--fg); min-height:100vh;
         display:flex; align-items:center; justify-content:center; text-align:center;
         font-family:-apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic",
                     "Noto Sans KR", sans-serif; word-break:keep-all; }}
  .box {{ padding: 40px 24px; }}
  h1 {{ font-size:34px; letter-spacing:4px; margin:0 0 10px; color:var(--accent); }}
  p {{ color:var(--dim); font-size:15px; line-height:1.8; margin:0 0 28px; }}
  a {{ color:var(--accent); font-size:14px; margin:0 10px; }}
</style>
</head>
<body>
  <div class="box">
    <h1>BANDULE</h1>
    <p>우리 밴드 합주, 한 곳에서 정리하자</p>
    <a href="/privacy/">개인정보처리방침</a><a href="/terms/">이용약관</a>
  </div>
</body>
</html>
"""


def body_of(path):
    """초안 머리말과 '채워야 하는 것' 꼬리를 걷어내고 본문만 남긴다.

    마크다운 파일은 초안 경고 → `---` → 실제 본문 순서다. 본문은 **두 번째 `# ` 제목**에서
    시작한다. 끝은 `⟨` 가 든 제목(채워야 하는 것) 직전 — 그 아래는 작업 메모라 게시하지 않는다.
    """
    text = path.read_text(encoding="utf-8")
    starts = [m.start() for m in re.finditer(r"^# ", text, re.M)]
    if len(starts) < 2:
        sys.exit(f"!! {path.name}: 본문 제목(두 번째 '# ')을 못 찾았다")
    body = text[starts[1]:]

    tail = re.search(r"^#{2,3} .*⟨", body, re.M)
    if tail:
        body = body[:tail.start()].rstrip().rstrip("-").rstrip()

    if "⟨" in body:
        missing = sorted(set(re.findall(r"⟨[^⟩]*⟩", body)))
        sys.exit(
            f"!! {path.name} 에 아직 안 채운 자리가 있다: {', '.join(missing)}\n"
            "   비워 둔 채로 게시하면 안 된다."
        )
    return body


def main():
    OUT.mkdir(exist_ok=True)
    (OUT / "index.html").write_text(INDEX, encoding="utf-8", newline="\n")
    print("index.html")

    for slug, title, path in PAGES:
        html = markdown.markdown(body_of(path), extensions=["tables", "sane_lists"])
        # 표만 가로 스크롤 상자에 넣는다. 좁은 화면에서 표 때문에 본문 전체가 밀리는 것을 막는다.
        html = html.replace("<table>", '<div class="table-wrap"><table>')
        html = html.replace("</table>", "</table></div>")

        target = OUT / slug
        target.mkdir(exist_ok=True)
        (target / "index.html").write_text(
            TEMPLATE.format(title=title, body=html), encoding="utf-8", newline="\n")
        print(f"{slug}/index.html  ({path.name})")

    print(f"\n== site/ 완성. Cloudflare Pages 에 이 폴더를 올리면 된다.")


if __name__ == "__main__":
    main()
