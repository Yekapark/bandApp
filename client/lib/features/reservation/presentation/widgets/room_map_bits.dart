import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:kakao_map_sdk/kakao_map_sdk.dart';

import '../../../../core/config/app_config.dart';
import '../../../../core/theme/app_colors.dart';

/// 합주실 지도(목록 화면·등록 폼)가 함께 쓰는 조각들.

/// 좌표가 하나도 없을 때 지도의 초기 위치 — 서울시청.
const kMapFallbackCenter = LatLng(37.5666, 126.9784);

/// 마커 아이콘. PNG 에셋 대신 위젯을 구워서 쓴다 — 앱 색을 그대로 따라가고 해상도별 에셋을
/// 관리할 필요가 없다. 굽는 비용이 있으므로 한 번만 만들어 재사용한다.
Future<KImage>? _pinImage;

/// 합주실 마커 스타일.
///
/// 이미지는 공유하지만 [PoiStyle] 객체 자체는 호출할 때마다 새로 만든다 — 스타일은 등록된
/// 지도를 기억하기 때문에(`_isAdded`), 지도 두 개가 한 인스턴스를 나눠 쓰면 두 번째 지도에서
/// 마커가 뜨지 않는다.
Future<PoiStyle> roomPoiStyle() async {
  final icon = await (_pinImage ??= KImage.fromWidget(
    const Icon(Icons.place, color: AppColors.primary, size: 36),
    const Size(36, 36),
  ));
  return PoiStyle(icon: icon);
}

/// 지도를 못 띄우는 이유를 사용자 말로. 셋 다 "고칠 방법"이 달라 문구를 나눈다.
String mapUnavailableMessage() {
  if (kIsWeb) {
    return '지도는 모바일 앱에서만 표시됩니다. 목록으로 확인하세요.';
  }
  if (AppConfig.kakaoNativeAppKey.isEmpty) {
    return '카카오 네이티브 앱 키(KAKAO_NATIVE_APP_KEY)를 설정하면 지도가 표시됩니다.';
  }
  return '카카오맵 인증에 실패했습니다. 카카오 개발자 콘솔에 이 앱의 패키지명과 키 해시가 '
      '등록돼 있는지 확인하세요.';
}

/// 지도를 못 띄울 때(웹·키 미설정·SDK 인증 실패) 지도 자리에 대신 놓는 안내.
class MapUnavailableNote extends StatelessWidget {
  const MapUnavailableNote({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      color: AppColors.surface,
      child: Text(
        message,
        style: const TextStyle(fontSize: 12, color: AppColors.textDim),
      ),
    );
  }
}
