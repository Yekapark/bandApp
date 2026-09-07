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
    --no-upload     빌드까지만. **빌드 번호는 되돌린다** — 나가지 않은 빌드가
                    번호를 먹지 않게. 커밋할 것이 남지 않는다
    --group NAME    보낼 테스터 그룹 (기본 밴듈테스트)
    --api-url URL   서버 주소 (기본 https://api.bandule.com)
    --app ID        App Distribution 앱 ID (기본은 dev 프로젝트. 아래 주석 참고)
    --abi 이름      올릴 CPU 종류 (기본 arm64-v8a). 32비트 폰을 쓰는 테스터가
                    "설치되지 않음" 이라고 하면 `--abi armeabi-v7a` 로 한 번 더 올린다
"""

import argparse
import re
import shutil
import subprocess
import time
import sys
from pathlib import Path

CLIENT = Path(__file__).resolve().parent.parent
PUBSPEC = CLIENT / "pubspec.yaml"
DART_DEFINES = CLIENT / "dart_defines.json"
# flavor 를 쓰면 산출물 이름에 flavor 가 붙는다: app-arm64-v8a-prod-release.apk
# (abi 가 먼저, flavor 가 뒤다 — 반대로 짐작했다가 한 번 틀렸다)
APK_DIR = CLIENT / "build/app/outputs/flutter-apk"
FLAVOR = "prod"

DEFAULT_API = "https://api.bandule.com"
# 64비트 ARM. 2015년 이후 안드로이드 폰은 사실상 전부 여기 해당한다.
# 32비트 기기(armeabi-v7a)를 쓰는 테스터가 나오면 `--abi armeabi-v7a` 로 한 번 더 올린다.
DEFAULT_ABI = "arm64-v8a"
DEFAULT_GROUP = "밴듈테스트"  # 테스터 2명. 본인만 볼 때는 --group 나만

# App Distribution 은 배포 채널일 뿐이고, 앱 안의 FCM 프로젝트와는 별개다 — 서로 달라도 된다.
#
#   테스터 배포 : bandapp-dev-67c6f  ← 이 앱 ID. 그룹 `밴듈테스트`와 지난 릴리스 이력이
#                 전부 여기 쌓여 있다. 프로젝트를 옮기면 테스터가 초대를 다시 받아야 한다.
#   앱의 푸시  : bandule-b94d2      ← prod flavor 의 google-services.json.
#                 서버 .env.prod 의 FCM_PROJECT_ID 와 같아야 한다.
#
# 패키지명이 같으므로 prod flavor APK 를 이 앱 ID 로 올리는 데 문제가 없다.
# 예전에는 android/app/google-services.json 에서 읽었지만, Firebase 설정이 개발용·운영용으로
# 갈리면서 그 파일이 src/{dev,prod}/ 로 옮겨져 경로가 깨졌다(docs/TROUBLESHOOTING.md).
DISTRIBUTION_APP_ID = "1:973100232123:android:45aab9e3dfda38629a289e"


def fail(message):
    print(f"\n!! {message}")
    sys.exit(1)


def bump_build_number():
    """`version: 0.1.0+7` 의 뒤 숫자를 하나 올리고, (이름, 번호, 원래 파일 내용)을 돌려준다.

    원래 내용을 함께 돌려주는 이유 — `--no-upload` 는 검증용이라 **번호를 되돌린다.**
    안 그러면 테스터에게 나가지도 않은 번호가 계속 타 없어지고, 커밋할 이유가 애매한
    변경이 작업 폴더에 남는다. 실제로 그러다 브랜치 사이에서 충돌이 났다.
    """
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
    return name, build, text


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
    parser.add_argument("--abi", default=DEFAULT_ABI)
    parser.add_argument("--app", default=DISTRIBUTION_APP_ID, help="App Distribution 앱 ID")
    parser.add_argument("--no-upload", action="store_true")
    args = parser.parse_args()

    if not DART_DEFINES.exists():
        fail(f"{DART_DEFINES.name} 이 없다 — 카카오 앱 키가 들어가지 않아 로그인이 막힌다")

    name, build, original_pubspec = bump_build_number()
    print(f"== 버전 {name}+{build}")
    started = time.time()

    # `--split-per-abi` — 하나로 합치면 CPU 4종류용 기계어를 다 실어 99MB 가 된다.
    # 테스터는 새 빌드마다 그걸 통째로 받는다(안드로이드는 부분 업데이트가 없다).
    # 쪼개면 30MB 대다.
    run(
        [
            "flutter", "build", "apk", "--release", "--split-per-abi",
            # 운영 Firebase 를 쓰는 빌드. flavor 를 빼면 Gradle 이 어느 쪽인지 못 골라
            # 빌드가 멈춘다. 개발용은 `flutter run --flavor dev`.
            "--flavor", "prod",
            f"--dart-define-from-file={DART_DEFINES.name}",
            f"--dart-define=API_BASE_URL={args.api_url}",
            # 앱 안에서 "새 버전 있어요" 를 띄우게 한다. 이 스위치가 없으면 그 코드가
            # 아예 안 돈다 — 스토어 빌드가 스토어 밖에서 앱을 받는 일이 없도록.
            "--dart-define=TESTER_BUILD=true",
            # 설정 화면 맨 아래에 찍힌다. 테스터가 "고쳤다는 게 안 보인다" 고 할 때
            # 어느 빌드를 깔고 있는지 물어볼 곳이 필요하다.
            f"--dart-define=BUILD_LABEL={name}+{build}",
        ],
        "릴리스 APK 빌드",
    )

    apk = APK_DIR / f"app-{args.abi}-{FLAVOR}-release.apk"
    if not apk.exists():
        made = sorted(p.name for p in APK_DIR.glob("app-*-release.apk"))
        fail(f"{apk.name} 이 없다. 만들어진 것: {', '.join(made) or '없음'}")
    # 방금 만든 것이 맞는지. 빌드가 조용히 실패하고 앞선 APK 가 그 자리에 남아 있으면
    # 옛 빌드를 새 것이라고 테스터에게 올리게 된다 — 아래 서버 주소 검사는 그걸 못 잡는다.
    # (실제로 한 번 헷갈렸다. APK 안에서 Dart 문자열을 찾아 확인하려 했지만, 문자열은
    #  압축돼 저장돼서 그 방법으로는 있는 것도 없다고 나온다.)
    if apk.stat().st_mtime < started:
        fail(f"{apk.name} 이 이번 빌드보다 오래됐다 — 빌드가 실제로 돌지 않았다")

    size_mb = apk.stat().st_size / 1024 / 1024
    print(f"   {apk.name}  {size_mb:.0f}MB")

    # 서버 주소가 실제로 박혔는지 본다. 릴리스는 Dart 가 기계어로 컴파일돼
    # `aapt dump strings` 로는 안 보이므로 libapp.so 를 직접 확인한다.
    import zipfile

    with zipfile.ZipFile(apk) as zf:
        native = zf.read(f"lib/{args.abi}/libapp.so")
    host = args.api_url.split("//")[-1].encode()
    if host not in native:
        fail(f"APK 안에 서버 주소({args.api_url})가 없다 — --dart-define 이 안 먹었다")
    print(f"   서버 주소 확인: {args.api_url}")

    if args.no_upload:
        # 검증만 했으니 번호를 되돌린다 — 나가지 않은 빌드가 번호를 먹지 않게.
        PUBSPEC.write_text(original_pubspec, encoding="utf-8", newline="\n")
        print("\n== --no-upload 라 여기서 멈춘다 (빌드 번호는 되돌렸다)")
        return

    run(
        [
            "firebase", "appdistribution:distribute", str(apk),
            "--app", args.app,
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
