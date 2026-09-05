/// 웹에는 네이티브 ABI가 없다. 지도는 어차피 `kIsWeb` 가드로 막히므로 항상 false.
bool get kakaoMapAbiSupported => false;
