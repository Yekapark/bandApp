// 실행 중인 네이티브 ABI가 카카오맵 SDK를 지원하는지.
// 웹에서는 `dart:ffi`를 못 쓰므로 조건부 import 로 갈라 둔다.
export 'native_abi_io.dart'
    if (dart.library.js_interop) 'native_abi_web.dart';
