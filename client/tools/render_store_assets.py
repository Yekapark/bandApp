"""Play Console 스토어 등록정보용 이미지 — client/brand/store/ 에 만든다.

    python client/tools/render_store_assets.py

앱 아이콘은 이미 렌더된 app-icon-1024.png 를 줄이기만 한다(같은 도형을 두 번 그리지 않는다).
기능 그래픽은 그 아이콘을 얹고 글자를 올린다.

Play 규격 — 아이콘 512x512 PNG / 기능 그래픽 1024x500, **투명도 없이**(RGB).
"""

from PIL import Image, ImageDraw, ImageFont

from pathlib import Path

BRAND = Path(__file__).resolve().parent.parent / "brand"
OUT = BRAND / "store"
BOLD = r"C:\Windows\Fonts\malgunbd.ttf"
REG = r"C:\Windows\Fonts\malgun.ttf"

BG_FROM = (0x2A, 0x14, 0x20)
BG_TO = (0x0E, 0x0E, 0x13)


def icon_512():
    src = Image.open(BRAND / "app-icon-1024.png").convert("RGBA")
    out = src.resize((512, 512), Image.LANCZOS)
    out.save(OUT / "icon-512.png")
    return out


def feature_graphic(icon):
    """1024x500. 대각 그라디언트 위에 아이콘 + 이름 + 한 줄 설명."""
    w, h = 1024, 500
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(BG_FROM, BG_TO))

    mark = icon.resize((200, 200), Image.LANCZOS)
    img.paste(mark, (84, 150), mark)

    d = ImageDraw.Draw(img)
    d.text((328, 178), "밴듈", font=ImageFont.truetype(BOLD, 82), fill=(0xF2, 0xEE, 0xF4))
    d.text((332, 286), "BANDULE", font=ImageFont.truetype(BOLD, 30),
           fill=(0xFF, 0x8B, 0x5C))
    d.text((332, 336), "우리 밴드 합주, 한 곳에서 정리하자",
           font=ImageFont.truetype(REG, 30), fill=(0xB9, 0xB2, 0xBF))

    img.save(OUT / "feature-graphic-1024x500.png")
    return img


def main():
    OUT.mkdir(exist_ok=True)
    icon = icon_512()
    feature_graphic(icon)
    for p in sorted(OUT.iterdir()):
        im = Image.open(p)
        print(f"{p.name}  {im.size[0]}x{im.size[1]}  {im.mode}  {p.stat().st_size // 1024}KB")


if __name__ == "__main__":
    main()
