import 'dart:ffi' show Abi;

/// 카카오맵 SDK가 이 기기의 ABI를 지원하는지.
///
/// 카카오맵 네이티브 엔진(`libK3fAndroid.so`)은 **ARM 빌드만 배포된다** — APK를 열어 보면
/// `arm64-v8a`/`armeabi-v7a` 에만 들어 있고 `x86_64` 에는 없다. 그래서 흔한 x86_64
/// 에뮬레이터에서 SDK를 초기화하면 Java 쪽에서 `UnsatisfiedLinkError`가 나며 프로세스가
/// 통째로 죽는다.
///
/// **이건 Dart `try/catch`로 못 막는다** — 네이티브 메인 스레드에서 터지는 치명적 오류라
/// Dart 로 올라오지 않는다. 유일한 방어는 지원되지 않는 ABI에서 **아예 호출하지 않는 것**이다.
/// 그래서 지도는 조용히 꺼지고(안내 문구로 폴백) 앱의 나머지는 정상 동작한다.
bool get kakaoMapAbiSupported {
  final abi = Abi.current();
  return abi == Abi.androidArm64 ||
      abi == Abi.androidArm ||
      abi == Abi.iosArm64 ||
      abi == Abi.iosArm;
}
