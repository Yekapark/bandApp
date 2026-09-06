"""테스터에게 새 빌드를 보낸다 — 빌드 번호 올리기부터 업로드까지 한 번에.

    cd client
    python tools/release_tester.py "지도 핀이 바로 안 뜨던 것 수정 · 업로드 진행률 표시"

손으로 하면 셋 중 하나는 꼭 빠진다.

  1. **빌드 번호를 안 올린다.** `pubspec.yaml` 의 `version: 0.1.0+N` 에서 `+N` 이 빌드
     번호인데, 이걸 그대로 두고 올리면 App Tester 목록에 같은 버전으로 보여 테스터가
     무엇이 새 것인지 모른다. 안드로이드도 같은 versionCode 를 업데이트로 치지 않는다.
  2. **`--dart-define-from-file` 을 빠뜨린다.** 그러면 카카오 앱 키가 안 들어가 로그인이
     "앱 키 미설정" 으로 막힌다(실제로 겪었다).
  3. **`API_BASE_URL` 을 빠뜨린다.** 앱 기본값이 localhost 라 테스터 폰에서 아무 데도
     못 붙는다.

셋 다 조용히 실패해서 테스터가 알려주기 전엔 모른다. 그래서 한 명령으로 묶었다.

옵션:
    --no-upload     빌드까지만 (업로드 전에 APK 를 확인하고 싶을 때)
    --group NAME    보낼 테스터 그룹 (기본 밴듈테스트)
    --api-url URL   서버 주소 (기본 https://api.bandule.com)
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

CLIENT = Path(__file__).resolve().parent.parent
PUBSPEC = CLIENT / "pubspec.yaml"
DART_DEFINES = CLIENT / "dart_defines.json"
GOOGLE_SERVICES = CLIENT / "android/app/google-services.json"
APK = CLIENT / "build/app/outputs/flutter-apk/app-release.apk"

DEFAULT_API = "https://api.bandule.com"
DEFAULT_GROUP = "밴듈테스트"


def fail(message):
    print(f"\n!! {message}")
    sys.exit(1)


def bump_build_number():
    """`version: 0.1.0+7` 의 뒤 숫자를 하나 올리고, 올린 뒤 값을 돌려준다."""
    text = PUBSPEC.read_text(encoding="utf-8")
    match = re.search(r"^version:\s*(\d+\.\d+\.\d+)\+(\d+)\s*$", text, re.M)
    if not match:
        fail(f"{PUBSPEC.name} 에서 'version: x.y.z+N' 을 찾지 못했다")
    name, build = match.group(1), int(match.group(2)) + 1
    PUBSPEC.write_text(
        text[: match.start()] + f"version: {name}+{build}" + text[match.end():],
        encoding="utf-8",
        newline="\n",
    )
    return name, build


def firebase_app_id():
    """앱 ID 를 google-services.json 에서 읽는다 — 하드코딩하면 프로젝트를 바꿀 때 어긋난다."""
    if not GOOGLE_SERVICES.exists():
        fail(
            f"{GOOGLE_SERVICES} 가 없다.\n"
            "   Firebase 콘솔에서 받아 넣는다 (docs/LAUNCH_CHECKLIST.md 7-B 단계)."
        )
    data = json.loads(GOOGLE_SERVICES.read_text(encoding="utf-8"))
    clients = data.get("client") or []
    if not clients:
        fail("google-services.json 에 android 앱이 등록돼 있지 않다")
    return clients[0]["client_info"]["mobilesdk_app_id"]


def run(command, what):
    """첫 칸은 PATH 에서 찾아서 쓴다.

    윈도우에서 flutter·firebase 는 실제로는 `flutter.bat`·`firebase.cmd` 라,
    `shell=False` 로 이름만 넘기면 `FileNotFoundError` 가 난다(실제로 났다).
    `shell=True` 로도 풀리지만 그러면 릴리스 노트에 든 공백·따옴표가 셸에게 먹힌다.
    """
    exe = shutil.which(command[0])
    if not exe:
        fail(f"{command[0]} 을 PATH 에서 찾지 못했다")
    print(f"\n== {what}")
    print("   " + " ".join(command[:3]) + " …")
    if subprocess.run([exe] + command[1:], cwd=CLIENT, shell=False).returncode != 0:
        fail(f"{what} 실패")


def main():
    parser = argparse.ArgumentParser(description="테스터용 빌드를 만들고 배포한다")
    parser.add_argument("release_notes", help="테스터가 App Tester 에서 볼 설명. 뭘 봐줬으면 하는지 적는다")
    parser.add_argument("--group", default=DEFAULT_GROUP)
    parser.add_argument("--api-url", default=DEFAULT_API)
    parser.add_argument("--no-upload", action="store_true")
    args = parser.parse_args()

    if not DART_DEFINES.exists():
        fail(f"{DART_DEFINES.name} 이 없다 — 카카오 앱 키가 들어가지 않아 로그인이 막힌다")

    name, build = bump_build_number()
    print(f"== 버전 {name}+{build}")

    run(
        [
            "flutter", "build", "apk", "--release",
            f"--dart-define-from-file={DART_DEFINES.name}",
            f"--dart-define=API_BASE_URL={args.api_url}",
        ],
        "릴리스 APK 빌드",
    )

    if not APK.exists():
        fail(f"APK 가 만들어지지 않았다: {APK}")
    size_mb = APK.stat().st_size / 1024 / 1024
    print(f"   {APK.name}  {size_mb:.0f}MB")

    # 서버 주소가 실제로 박혔는지 본다. 릴리스는 Dart 가 기계어로 컴파일돼
    # `aapt dump strings` 로는 안 보이므로 libapp.so 를 직접 확인한다.
    import zipfile

    with zipfile.ZipFile(APK) as apk:
        native = apk.read("lib/arm64-v8a/libapp.so")
    host = args.api_url.split("//")[-1].encode()
    if host not in native:
        fail(f"APK 안에 서버 주소({args.api_url})가 없다 — --dart-define 이 안 먹었다")
    print(f"   서버 주소 확인: {args.api_url}")

    if args.no_upload:
        print("\n== --no-upload 라 여기서 멈춘다")
        return

    run(
        [
            "firebase", "appdistribution:distribute", str(APK),
            "--app", firebase_app_id(),
            "--groups", args.group,
            "--release-notes", args.release_notes,
        ],
        f"App Distribution 업로드 (그룹 {args.group})",
    )

    print(
        f"\n== 끝. {name}+{build} 가 테스터에게 갔다.\n"
        f"   pubspec.yaml 의 빌드 번호가 바뀌었으니 커밋해 둔다:\n"
        f"     git add client/pubspec.yaml && git commit -m \"chore(client): 테스터 빌드 {name}+{build}\""
    )


if __name__ == "__main__":
    main()
