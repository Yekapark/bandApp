"""스토어(Google Play)에 올릴 AAB 를 만들고, 올리기 전에 거절 사유를 먼저 검사한다.

    cd client
    python tools/release_store.py            # 빌드 번호 +1 → AAB 빌드 → 검사
    python tools/release_store.py --check-only build/app/outputs/bundle/prodRelease/app-prod-release.aab

테스터 APK 는 `release_tester.py` 가 만든다. 스토어는 APK 가 아니라 **AAB** 를 받고,
Play 가 거절하는 조건이 따로 있어서 스크립트를 나눴다. 이 스크립트가 잡는 것:

  1. **서버 주소.** `dart_defines.json` 의 `API_BASE_URL` 은 로컬(`http://localhost:8080`)이라
     `--dart-define=API_BASE_URL=...` 을 빠뜨리면 아무 데도 못 붙는 앱이 스토어에 올라간다.
  2. **targetSdk.** 2026-08-31 부터 신규 앱·업데이트는 36 이상이어야 업로드된다.
  3. **16KB 페이지 크기.** targetSdk 35 이상은 64비트 네이티브 라이브러리(.so)의 LOAD
     세그먼트가 16KB(0x4000) 정렬이어야 한다. 카카오맵 SDK 가 .so 를 싣고 온다.
  4. **서명 키.** `android/key.properties` 가 없으면 Gradle 이 디버그 키로 서명하고
     조용히 넘어간다 — 그 AAB 는 Play 가 받지 않는다.
  5. **설정 화면 버전 표시.** `BUILD_LABEL` 을 안 넣으면 "개발 빌드" 로 찍힌다.

검사가 하나라도 실패하면 **빌드 번호를 되돌린다** — 올리지 못한 빌드가 번호를 먹지 않게.
성공하면 올릴 파일 경로와 커밋 명령을 알려 준다. **업로드는 사람이 Play Console 에서 한다**
(첫 업로드·트랙 선택·출시 노트는 콘솔에서 보는 편이 안전하다).

옵션:
    --api-url URL   서버 주소 (기본 https://api.bandule.com)
    --no-bump       빌드 번호를 올리지 않는다 (같은 번호로 다시 빌드해 검사만 할 때)
    --check-only AAB  빌드 없이 이미 있는 AAB 만 검사한다
"""

import argparse
import re
import shutil
import struct
import subprocess
import sys
import time
import zipfile
from pathlib import Path

CLIENT = Path(__file__).resolve().parent.parent
PUBSPEC = CLIENT / "pubspec.yaml"
DART_DEFINES = CLIENT / "dart_defines.json"
KEY_PROPERTIES = CLIENT / "android" / "key.properties"
FLAVOR = "prod"
AAB = CLIENT / f"build/app/outputs/bundle/{FLAVOR}Release/app-{FLAVOR}-release.aab"
INTERMEDIATES = CLIENT / "build/app/intermediates"

DEFAULT_API = "https://api.bandule.com"
# build.gradle.kts 의 playMinTargetSdk 와 같은 값. 해마다 8월 31일에 오른다.
MIN_TARGET_SDK = 36
PAGE_16K = 0x4000
# 16KB 요건은 64비트 ABI 에만 걸린다(32비트 기기는 4KB 페이지뿐이다).
ABIS_64 = ("arm64-v8a", "x86_64")


def fail(message):
    print(f"\n!! {message}")
    sys.exit(1)


def ok(message):
    print(f"   OK  {message}")


def bump_build_number():
    """`version: 0.1.0+29` 의 뒤 숫자를 하나 올리고 (이름, 번호, 원래 내용)을 돌려준다."""
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


def current_version():
    text = PUBSPEC.read_text(encoding="utf-8")
    match = re.search(r"^version:\s*(\d+\.\d+\.\d+)\+(\d+)\s*$", text, re.M)
    if not match:
        fail(f"{PUBSPEC.name} 에서 'version: x.y.z+N' 을 찾지 못했다")
    return match.group(1), int(match.group(2))


def run(command, what):
    """윈도우의 flutter 는 flutter.bat 라 PATH 에서 찾아 절대경로로 부른다(release_tester.py 와 같다)."""
    exe = shutil.which(command[0])
    if not exe:
        fail(f"{command[0]} 을 PATH 에서 찾지 못했다")
    print(f"\n== {what}")
    print("   " + " ".join(command[:4]) + " …")
    if subprocess.run([exe] + command[1:], cwd=CLIENT, shell=False).returncode != 0:
        raise RuntimeError(f"{what} 실패")


# --- 검사 -------------------------------------------------------------------------------


def check_server_url(zf, api_url):
    """Dart 코드는 libapp.so 로 컴파일되므로 그 안에서 서버 호스트 문자열을 찾는다."""
    host = api_url.split("//")[-1].encode()
    found = False
    for name in zf.namelist():
        if name.endswith("/libapp.so"):
            data = zf.read(name)
            if host in data:
                found = True
            else:
                raise AssertionError(
                    f"{name} 에 서버 주소({api_url})가 없다 — --dart-define=API_BASE_URL 이 안 먹었다")
    if not found:
        raise AssertionError("AAB 에 libapp.so 가 없다 — Flutter 릴리스 빌드가 아니다")
    ok(f"서버 주소 {api_url}")


def check_16k_alignment(zf):
    """64비트 .so 의 PT_LOAD 세그먼트 정렬(p_align)이 16KB 이상인지 본다.

    ELF64 헤더: e_phoff @32(8바이트), e_phentsize @54(2), e_phnum @56(2).
    프로그램 헤더: p_type @0(4), p_align @48(8). PT_LOAD = 1.
    """
    bad, checked = [], 0
    for name in zf.namelist():
        if not name.endswith(".so"):
            continue
        parts = name.split("/")
        if len(parts) < 2 or parts[-2] not in ABIS_64:
            continue
        data = zf.read(name)
        if data[:4] != b"\x7fELF" or data[4] != 2:
            continue  # ELF64 가 아니면 대상이 아니다
        endian = "<" if data[5] == 1 else ">"
        phoff = struct.unpack_from(endian + "Q", data, 32)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 54)
        aligns = []
        for i in range(phnum):
            off = phoff + i * phentsize
            p_type = struct.unpack_from(endian + "I", data, off)[0]
            if p_type == 1:
                aligns.append(struct.unpack_from(endian + "Q", data, off + 48)[0])
        checked += 1
        if aligns and min(aligns) < PAGE_16K:
            bad.append(f"{name} (LOAD 정렬 {hex(min(aligns))})")
    if bad:
        raise AssertionError(
            "16KB 페이지를 지원하지 않는 네이티브 라이브러리:\n      "
            + "\n      ".join(bad)
            + "\n      → 해당 SDK·플러그인을 16KB 지원 버전으로 올려야 한다")
    ok(f"16KB 페이지 정렬 ({checked}개 64비트 .so)")


def check_target_sdk():
    """빌드가 남긴 병합 매니페스트에서 targetSdkVersion 을 읽는다.

    AAB 안의 매니페스트는 protobuf 라 바로 못 읽는다. bundletool 이 PATH 에 있으면 그걸로
    AAB 를 직접 확인하고, 없으면 Gradle 중간 산출물의 병합 매니페스트를 본다.
    """
    bundletool = shutil.which("bundletool")
    if bundletool:
        out = subprocess.run(
            [bundletool, "dump", "manifest", f"--bundle={AAB}", "--xpath=/manifest/uses-sdk/@android:targetSdkVersion"],
            capture_output=True, text=True)
        value = out.stdout.strip()
        if value.isdigit():
            return int(value), "bundletool"

    candidates = [
        p for p in INTERMEDIATES.rglob("AndroidManifest.xml")
        if f"{FLAVOR}Release" in p.as_posix() and "targetSdkVersion" in p.read_text(encoding="utf-8", errors="ignore")
    ]
    if not candidates:
        raise AssertionError("병합 매니페스트를 찾지 못했다 — targetSdk 를 확인할 수 없다 (bundletool 을 설치하면 AAB 를 직접 읽는다)")
    newest = max(candidates, key=lambda p: p.stat().st_mtime)
    match = re.search(r'targetSdkVersion="(\d+)"', newest.read_text(encoding="utf-8", errors="ignore"))
    if not match:
        raise AssertionError(f"{newest} 에서 targetSdkVersion 을 읽지 못했다")
    return int(match.group(1)), newest.relative_to(CLIENT).as_posix()


def check_signing():
    """디버그 키로 서명된 AAB 는 Play 가 받지 않는다. keytool 로 서명 인증서를 본다."""
    keytool = shutil.which("keytool")
    if not keytool:
        print("   ??  keytool 이 PATH 에 없어 서명 인증서는 확인하지 못했다 (JDK bin 을 PATH 에)")
        return
    out = subprocess.run([keytool, "-printcert", "-jarfile", str(AAB)], capture_output=True, text=True)
    text = out.stdout + out.stderr
    if "CN=Android Debug" in text:
        raise AssertionError("디버그 키로 서명됐다 — android/key.properties·bandule-release.jks 를 확인")
    owner = re.search(r"(?:Owner|소유자):\s*(.+)", text)
    ok(f"업로드 키 서명 ({owner.group(1).strip() if owner else '인증서 확인됨'})")


def check_all(api_url):
    if not AAB.exists():
        raise AssertionError(f"{AAB.relative_to(CLIENT)} 이 없다")
    print("\n== 스토어 거절 사유 검사")
    with zipfile.ZipFile(AAB) as zf:
        check_server_url(zf, api_url)
        check_16k_alignment(zf)
    target, source = check_target_sdk()
    if target < MIN_TARGET_SDK:
        raise AssertionError(f"targetSdk {target} < {MIN_TARGET_SDK} — Play 가 업로드를 거절한다 ({source})")
    ok(f"targetSdk {target} ({source})")
    check_signing()


def main():
    parser = argparse.ArgumentParser(description="스토어용 AAB 를 만들고 Play 거절 사유를 검사한다")
    parser.add_argument("--api-url", default=DEFAULT_API)
    parser.add_argument("--no-bump", action="store_true")
    parser.add_argument("--check-only", metavar="AAB")
    args = parser.parse_args()
    global AAB

    if not args.api_url.startswith("https://"):
        fail(f"스토어 빌드의 서버 주소는 https 여야 한다: {args.api_url}")

    if args.check_only:
        AAB = Path(args.check_only).resolve()
        try:
            check_all(args.api_url)
        except AssertionError as e:
            fail(str(e))
        print("\n== 검사 통과")
        return

    if not DART_DEFINES.exists():
        fail(f"{DART_DEFINES.name} 이 없다 — 카카오 앱 키가 들어가지 않아 로그인이 막힌다")
    if not KEY_PROPERTIES.exists():
        fail("android/key.properties 가 없다 — 디버그 키로 서명돼 Play 가 받지 않는다 (LAUNCH_CHECKLIST 9단계)")

    original_pubspec = None
    if args.no_bump:
        name, build = current_version()
    else:
        name, build, original_pubspec = bump_build_number()
    print(f"== 버전 {name}+{build}")

    def rollback():
        if original_pubspec is not None:
            PUBSPEC.write_text(original_pubspec, encoding="utf-8", newline="\n")
            print("   (빌드 번호를 되돌렸다)")

    started = time.time()
    try:
        run(
            [
                "flutter", "build", "appbundle", "--release",
                "--flavor", FLAVOR,
                f"--dart-define-from-file={DART_DEFINES.name}",
                # dart_defines.json 의 API_BASE_URL 은 로컬 주소다. 어느 쪽이 이기는지에
                # 기대지 않고, 아래 check_server_url 이 실제로 박힌 주소를 확인한다.
                f"--dart-define=API_BASE_URL={args.api_url}",
                f"--dart-define=BUILD_LABEL={name}+{build}",
            ],
            "스토어용 AAB 빌드",
        )
        if not AAB.exists() or AAB.stat().st_mtime < started:
            raise AssertionError(f"{AAB.name} 이 이번 빌드에서 만들어지지 않았다")
        check_all(args.api_url)
    except (AssertionError, RuntimeError) as e:
        rollback()
        fail(str(e))

    size_mb = AAB.stat().st_size / 1024 / 1024
    print(
        f"\n== 끝. {name}+{build}  ({size_mb:.0f}MB)\n"
        f"   올릴 파일: {AAB}\n"
        f"   Play Console › 테스트 및 출시 › (비공개 테스트 또는 프로덕션) › 새 버전 만들기 에 올린다.\n"
        f"   빌드 번호가 바뀌었으니 커밋해 둔다:\n"
        f"     git add client/pubspec.yaml && git commit -m \"chore(client): 스토어 빌드 {name}+{build}\""
    )


if __name__ == "__main__":
    main()
