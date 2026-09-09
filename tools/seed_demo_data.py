"""스토어 스크린샷용 데모 데이터를 만든다.

    # PowerShell
    $env:BANDULE_DEMO_PW = "고를비밀번호8자이상"
    python tools/seed_demo_data.py                  # 운영(api.bandule.com)
    python tools/seed_demo_data.py http://localhost:8080

데모 계정 4개(밴드장 1 + 멤버 3)를 만들고 밴드 하나를 채운다. 합주실 3곳(지도 마커),
지난·다가올 합주 7건, 참석 현황, 셋리스트, 정산(일부 납부), 정기 일정, 사진 붙은 게시글까지.
스크린샷 8장에 필요한 화면이 전부 비지 않게 하는 것이 목적이다.

**이미 있는 계정이면 로그인으로 넘어간다.** 두 번 돌려도 계정은 안 늘지만 밴드·일정은 또
생기므로, 다시 채우려면 앱에서 만들어진 밴드를 지우고 돌리는 편이 깔끔하다.

Pillow 가 필요하다 (`pip install pillow`) — 첨부 사진을 외부 파일 없이 만들기 때문이다.
"""

import io
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

BASE = (sys.argv[1] if len(sys.argv) > 1 else "https://api.bandule.com").rstrip("/")
PW = os.environ.get("BANDULE_DEMO_PW")
if not PW or len(PW) < 8:
    sys.exit("!! 환경변수 BANDULE_DEMO_PW 에 8자 이상 비밀번호를 넣고 실행한다.")

KST = timezone(timedelta(hours=9))
NOW = datetime.now(KST)

MEMBERS = [
    ("bandule.demo.leader@gmail.com", "박정우"),
    ("bandule.demo.gt@gmail.com", "이서현"),
    ("bandule.demo.bs@gmail.com", "김도윤"),
    ("bandule.demo.dr@gmail.com", "최유진"),
]
BAND = "노을밴드"
ROOMS = [
    ("사운드랩 합주실 홍대점", "서울 마포구 와우산로 94", "02-334-1234", 37.5511, 126.9250,
     "3번 방이 제일 넓다. 주차 2대"),
    ("드럼앤베이스 신촌", "서울 서대문구 연세로 12", "02-312-5678", 37.5585, 126.9370,
     "전화 예약만 받는다"),
    ("뮤직하우스 합정", "서울 마포구 양화로 45", "02-322-9012", 37.5495, 126.9137,
     "주말은 2주 전에 잡아야 한다"),
]
SETLIST = [("사랑이 지나가면", "이문세"), ("빗속에서", "들국화"), ("그대에게", "무한궤도"),
           ("한 페이지가 될 수 있게", "DAY6"), ("소녀", "이문세")]
POSTS = [
    ("9월 첫 합주 사진",
     "새 합주실에서 첫 합주했어요. 드럼 소리가 확실히 다르네요.\n다음 주에도 같은 방으로 잡았습니다.", 2),
    ("공연 셋리스트 정리",
     "이번 공연 순서 이대로 갈게요.\n1. 그대에게\n2. 빗속에서\n3. 사랑이 지나가면\n앵콜은 소녀로 합니다.", 0),
    ("합주실 옮길까요?",
     "합정 쪽이 다들 오기 편할 것 같은데 어때요?\n가격은 비슷하고 방은 조금 더 큽니다.", 1),
]


def call(method, path, token=None, body=None, url=None):
    u = url or (BASE + path)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(u, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            text = r.read().decode("utf-8", "replace")
            return r.status, (json.loads(text) if text.strip()[:1] in "{[" else text)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:400]


def ok(res, what):
    code, body = res
    if code >= 300:
        sys.exit("!! %s 실패 %s: %s" % (what, code, body))
    if isinstance(body, dict) and "data" in body:
        return body["data"]
    return body


def account(email, name):
    code, body = call("POST", "/api/v1/auth/signup",
                      body={"email": email, "password": PW, "name": name})
    if code >= 300:
        code, body = call("POST", "/api/v1/auth/login", body={"email": email, "password": PW})
        if code >= 300:
            sys.exit("!! %s 가입·로그인 모두 실패 %s: %s\n"
                     "   이미 다른 비밀번호로 만든 계정이면 BANDULE_DEMO_PW 를 그 값으로 맞춘다."
                     % (name, code, body))
    d = body["data"]
    return {"email": email, "name": name, "token": d["tokens"]["accessToken"], "id": d["user"]["id"]}


def at(days, hour):
    d = (NOW + timedelta(days=days)).replace(hour=hour, minute=0, second=0, microsecond=0)
    return d.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def jpeg(width, height, top, bottom, label):
    """첨부용 이미지. 외부 파일 없이 세로 그라디언트 + 글자로 만든다."""
    from PIL import Image, ImageDraw, ImageFont
    img = Image.new("RGB", (width, height))
    px = img.load()
    for y in range(height):
        t = y / height
        row = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
        for x in range(width):
            px[x, y] = row
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("C:\\Windows\\Fonts\\malgunbd.ttf", height // 14)
    except OSError:
        font = ImageFont.load_default()
    draw.text((width // 12, height - height // 5), label, font=font, fill=(255, 255, 255))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=88)
    return buf.getvalue()


def upload(bid, pid, token, blob):
    info = ok(call("POST", "/api/v1/bands/%s/posts/%s/media/upload-url" % (bid, pid), token,
                   {"contentType": "image/jpeg", "sizeBytes": len(blob)}), "업로드 URL")
    headers = info.get("requiredHeaders") or {}
    req = urllib.request.Request(info["uploadUrl"], data=blob, method=info.get("method") or "PUT")
    for key, value in headers.items():
        req.add_header(key, value)
    if not any(k.lower() == "content-type" for k in headers):
        req.add_header("Content-Type", "image/jpeg")
    with urllib.request.urlopen(req, timeout=180) as r:
        if r.status >= 300:
            sys.exit("!! R2 업로드 실패 %s" % r.status)
    ok(call("POST", "/api/v1/bands/%s/posts/%s/media/%s/complete" % (bid, pid, info["mediaId"]),
            token), "업로드 완료 처리")


def main():
    print("대상: %s\n" % BASE)

    print("1) 계정 4개")
    accs = [account(email, name) for email, name in MEMBERS]
    lead = accs[0]
    for a in accs:
        print("   %-6s id=%s" % (a["name"], a["id"]))

    print("2) 밴드")
    bid = ok(call("POST", "/api/v1/bands", lead["token"], {"name": BAND}), "밴드 생성")["id"]
    print("   %s id=%s" % (BAND, bid))
    invite = ok(call("POST", "/api/v1/bands/%s/invites" % bid, lead["token"]), "초대코드 발급")
    for a in accs[1:]:
        ok(call("POST", "/api/v1/bands/join", a["token"], {"code": invite["code"]}),
           "%s 참여" % a["name"])
    print("   멤버 %d명" % len(accs))

    print("3) 합주실 3곳")
    rids = []
    for name, addr, phone, lat, lng, memo in ROOMS:
        room = ok(call("POST", "/api/v1/bands/%s/rooms" % bid, lead["token"],
                       {"name": name, "address": addr, "phone": phone,
                        "lat": lat, "lng": lng, "memo": memo}), name)
        rids.append(room["id"])
        print("   %s" % name)

    print("4) 합주 일정 7건")
    plan = [(-14, 15, 18, 0, 36000, "정기 합주 · 예약자 박정우"),
            (-7, 15, 18, 0, 36000, "정기 합주 · 예약자 박정우"),
            (-3, 19, 22, 1, 45000, "공연 전 마지막 맞춤"),
            (2, 15, 18, 0, 36000, "정기 합주 · 예약자 박정우"),
            (5, 19, 22, 2, 42000, "카톡으로 예약 완료"),
            (9, 15, 18, 0, 36000, "정기 합주 · 예약자 박정우"),
            (16, 14, 17, 1, 45000, "합주실 옮김 · 이서현 예약")]
    reservations = []
    for days, start_h, end_h, room_i, cost, note in plan:
        r = ok(call("POST", "/api/v1/bands/%s/reservations" % bid, lead["token"],
                    {"roomId": rids[room_i], "startAt": at(days, start_h),
                     "endAt": at(days, end_h), "cost": cost, "note": note}), "일정 생성")
        reservations.append((r.get("reservation") or r)["id"])
    past, upcoming = reservations[2], reservations[3]
    print("   %d건 (지난 3 · 다가올 4)" % len(reservations))

    print("5) 참석 현황")
    for a, status in zip(accs, ["ATTENDING", "ATTENDING", "ATTENDING", "ABSENT"]):
        ok(call("PUT", "/api/v1/bands/%s/reservations/%s/attendances/%s" % (bid, upcoming, a["id"]),
                a["token"], {"status": status}), "%s 참석" % a["name"])
    for a, status in zip(accs, ["ATTENDING", "ATTENDING", "ABSENT", "ATTENDING"]):
        ok(call("PUT", "/api/v1/bands/%s/reservations/%s/attendances/%s" % (bid, past, a["id"]),
                a["token"], {"status": status}), "%s 참석(지난)" % a["name"])
    print("   다가올 합주 3명 참석 · 1명 불참")

    print("6) 셋리스트")
    for title, artist in SETLIST:
        ok(call("POST", "/api/v1/bands/%s/reservations/%s/setlist" % (bid, upcoming),
                lead["token"], {"title": title, "artist": artist}), "셋리스트 추가")
    print("   %d곡" % len(SETLIST))

    print("7) 정산")
    ok(call("POST", "/api/v1/bands/%s/reservations/%s/settlement" % (bid, past), lead["token"],
            {"totalAmount": 45000, "splitType": "ATTENDEES_ONLY"}), "정산 생성")
    for a in accs[:2]:
        ok(call("PUT", "/api/v1/bands/%s/reservations/%s/settlement/shares/%s" % (bid, past, a["id"]),
                a["token"], {"paid": True}), "%s 납부" % a["name"])
    print("   45,000원 · 참석자 균등 · 2명 납부 완료")

    print("8) 정기 일정")
    ok(call("POST", "/api/v1/bands/%s/recurring-rules" % bid, lead["token"],
            {"roomId": rids[0], "frequency": "WEEKLY", "dayOfWeek": "SATURDAY",
             "startTime": "15:00", "endTime": "18:00",
             "startDate": (NOW + timedelta(days=1)).date().isoformat(),
             "endDate": (NOW + timedelta(days=90)).date().isoformat(),
             "cost": 36000, "memo": "정기 합주 · 예약자 박정우"}), "정기 일정")
    print("   매주 토요일 15:00~18:00")

    print("9) 게시글 3개")
    tints = [((0x3A, 0x1E, 0x2E), (0x12, 0x10, 0x18)),
             ((0x1E, 0x2E, 0x3A), (0x10, 0x14, 0x18)),
             ((0x2E, 0x2A, 0x1E), (0x18, 0x14, 0x10))]
    for i, (title, content, shots) in enumerate(POSTS):
        post = ok(call("POST", "/api/v1/bands/%s/posts" % bid, lead["token"],
                       {"title": title, "content": content}), "게시글 작성")
        for k in range(shots):
            top, bottom = tints[(i + k) % 3]
            upload(bid, post["id"], lead["token"], jpeg(1280, 960, top, bottom, "노을밴드 · 합주 기록"))
        print("   %s (사진 %d장)" % (title, shots))

    print("""
─────────────────────────────────────────────
끝났다. 밴드 id=%s

폰에서 이 계정으로 로그인해 찍으면 된다 (Play 심사자용으로도 그대로 쓴다):

  이메일   %s
  비밀번호 BANDULE_DEMO_PW 에 넣은 값

찍을 화면과 순서는 docs/store-listing.md 참고.
지도(/map)는 ARM 기기에서만 뜬다 — x86 에뮬레이터에서는 목록만 보인다.
─────────────────────────────────────────────""" % (bid, lead["email"]))


if __name__ == "__main__":
    main()
