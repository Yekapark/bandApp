"""밴듈 아이콘 생성기 — client/brand/*.svg → 안드로이드·웹 리소스.

원본 SVG 가 선 2개 + 원 2개 + 그라디언트뿐이라, SVG 파서를 의존성으로 들이는 대신
Pillow 로 같은 도형을 직접 그린다. 4배 supersampling 후 축소해 안티에일리어싱을 얻는다.
도형 좌표는 원본 SVG 의 60 단위 공간을 그대로 옮긴 것이라, 디자인이 바뀌면 SHAPES 를 고친다.

    python tools/render_icons.py
"""

"""밴듈 앱 아이콘 렌더러.

원본 SVG(app-icon-1024.svg / logo-mark.svg)가 선 2개 + 원 2개 + 그라디언트뿐이라
SVG 파서를 들이는 대신 Pillow 로 같은 도형을 직접 그린다. 4배 supersampling 후 축소해
안티에일리어싱을 얻는다.

좌표는 원본의 60 단위 공간을 그대로 쓰고, 출력 크기에 맞춰 스케일만 바꾼다.
"""

from PIL import Image, ImageDraw

# --- 원본 SVG 에서 그대로 옮긴 값 (60 단위 공간) ---
STROKE_W = 7.0
SHAPES = [
    ("line", (9, 31), (22, 46), "#A06BFF"),
    ("line", (22, 46), (51, 11), "#FF6A2B"),
    ("dot", (51, 11), 5.2, "#FF8B5C"),
    ("dot", (9, 31), 4.4, "#C8AAFF"),
]
BG_FROM = (0x2A, 0x14, 0x20)   # 좌상단
BG_TO = (0x0E, 0x0E, 0x13)     # 우하단

SS = 4  # supersampling 배수


def _gradient(size):
    """SVG linearGradient(x1=0,y1=0 → x2=1,y2=1) 와 같은 대각선 그라디언트."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            # 대각선 진행도 0..1
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(
                round(BG_FROM[i] + (BG_TO[i] - BG_FROM[i]) * t) for i in range(3)
            )
    return img


def _draw_mark(draw, scale, offset):
    """60 단위 공간의 마크를 그린다. 둥근 캡은 끝점에 원을 얹어 만든다."""
    ox, oy = offset

    def pt(p):
        return (ox + p[0] * scale, oy + p[1] * scale)

    for shape in SHAPES:
        if shape[0] == "line":
            _, a, b, color = shape
            w = STROKE_W * scale
            draw.line([pt(a), pt(b)], fill=color, width=round(w))
            # 둥근 캡
            for end in (a, b):
                cx, cy = pt(end)
                r = w / 2
                draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
        else:
            _, c, radius, color = shape
            cx, cy = pt(c)
            r = radius * scale
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)


def render(size, *, background=True, mark_ratio=0.672, out=None):
    """정사각 아이콘 한 장.

    [mark_ratio] 는 캔버스 대비 마크(60 단위)가 차지하는 비율.
    원본 app-icon 은 1024 캔버스에 172 여백 → 688/1024 = 0.672 다.
    """
    big = size * SS
    if background:
        img = _gradient(big).convert("RGBA")
    else:
        img = Image.new("RGBA", (big, big), (0, 0, 0, 0))

    span = big * mark_ratio
    scale = span / 60.0
    pad = (big - span) / 2
    _draw_mark(ImageDraw.Draw(img), scale, (pad, pad))

    img = img.resize((size, size), Image.LANCZOS)
    if out:
        img.save(out)
    return img



import os

# 이 파일은 client/tools/ 에 있다 → 부모가 client/
CLIENT = os.path.dirname(os.path.dirname(os.path.abspath(__file__))).replace("\\", "/")

# 마크의 실제 내용 경계(60 단위). 끝 원과 선 캡까지 포함해서 잡는다.
_CAP = STROKE_W / 2
_xs, _ys = [], []
for shape in SHAPES:
    if shape[0] == "line":
        for p in (shape[1], shape[2]):
            _xs += [p[0] - _CAP, p[0] + _CAP]
            _ys += [p[1] - _CAP, p[1] + _CAP]
    else:
        c, r = shape[1], shape[2]
        _xs += [c[0] - r, c[0] + r]
        _ys += [c[1] - r, c[1] + r]
BBOX = (min(_xs), min(_ys), max(_xs), max(_ys))
BBOX_W = BBOX[2] - BBOX[0]
BBOX_H = BBOX[3] - BBOX[1]

SS = 4


def render_content(size, *, coverage, background=False):
    """마크의 실제 경계를 캔버스 중앙에 놓고 [coverage] 비율로 채운다.

    적응형 아이콘·마스커블처럼 "안전 영역 안에 들어가야 하는" 경우에 쓴다.
    coverage 는 (경계 긴 변 / 캔버스 변) 비율.
    """
    big = size * SS
    img = (_gradient(big).convert("RGBA") if background
           else Image.new("RGBA", (big, big), (0, 0, 0, 0)))

    scale = big * coverage / max(BBOX_W, BBOX_H)
    ox = (big - BBOX_W * scale) / 2 - BBOX[0] * scale
    oy = (big - BBOX_H * scale) / 2 - BBOX[1] * scale
    _draw_mark(ImageDraw.Draw(img), scale, (ox, oy))
    return img.resize((size, size), Image.LANCZOS)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("  ", path.replace(CLIENT + "/", ""), img.size)


def main():
    # --- 안드로이드 레거시 런처 아이콘(배경 포함) ---
    print("안드로이드 mipmap:")
    for density, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                        ("xxhdpi", 144), ("xxxhdpi", 192)]:
        save(render(px), f"{CLIENT}/android/app/src/main/res/mipmap-{density}/ic_launcher.png")

    # --- 적응형 아이콘 전경(투명, 108dp 캔버스) ---
    # 런처가 원형·스퀴클 등으로 잘라내므로 안전 원(66/108) 안에 들어가게 둔다.
    print("적응형 전경:")
    for density, px in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                        ("xxhdpi", 324), ("xxxhdpi", 432)]:
        save(render_content(px, coverage=0.52),
             f"{CLIENT}/android/app/src/main/res/mipmap-{density}/ic_launcher_foreground.png")

    # --- 웹 ---
    print("웹:")
    save(render(16), f"{CLIENT}/web/favicon.png")
    for px in (192, 512):
        save(render(px), f"{CLIENT}/web/icons/Icon-{px}.png")
        # 마스커블은 더 많이 잘려 나가므로 여백을 더 준다.
        save(render_content(px, coverage=0.44, background=True),
             f"{CLIENT}/web/icons/Icon-maskable-{px}.png")

    # --- 미리보기(스토어 제출용 1024) ---
    save(render(1024), f"{CLIENT}/brand/app-icon-1024.png")


if __name__ == "__main__":
    main()
