import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:kakao_map_sdk/kakao_map_sdk.dart';

import '../../../../core/config/app_config.dart';
import '../../../../core/config/native_abi.dart';
import '../../../../core/theme/app_colors.dart';

/// 합주실 지도(목록 화면·등록 폼)가 함께 쓰는 조각들.

/// 좌표가 하나도 없을 때 지도의 초기 위치 — 서울시청.
const kMapFallbackCenter = LatLng(37.5666, 126.9784);

/// 마커 아이콘. PNG 에셋 대신 위젯을 구워서 쓴다 — 앱 색을 그대로 따라가고 해상도별 에셋을
/// 관리할 필요가 없다. 굽는 비용이 있으므로 한 번만 만들어 재사용한다.
Future<KImage>? _pinImage;

const double _pinSize = 36;

/// 여러 핀을 한 화면에 맞출 때(`CameraUpdate.fitMapPoints`) 가장자리 여백. **물리 픽셀**이다.
///
/// 카카오 Android SDK 는 이 값을 dp 가 아니라 px 로 받는다. 마커는 좌표에 **아래 끝**을
/// 맞춰(anchor 0.5,1.0) 그 위로 핀 높이만큼 솟는데, 예전 80px 은 S24(약 3배 밀도)에서
/// 27dp 남짓이라 36dp 핀보다 낮아 맨 위 핀 머리가 잘렸다(QA-F03). 핀 높이 + 여유를 dp 로
/// 잡고 밀도를 곱한다.
int mapFitPadding(BuildContext context, {double margin = 24}) =>
    ((_pinSize + margin) * MediaQuery.devicePixelRatioOf(context)).round();

/// 합주실 마커 스타일.
///
/// 이미지는 공유하지만 [PoiStyle] 객체 자체는 호출할 때마다 새로 만든다 — 스타일은 등록된
/// 지도를 기억하기 때문에(`_isAdded`), 지도 두 개가 한 인스턴스를 나눠 쓰면 두 번째 지도에서
/// 마커가 뜨지 않는다.
Future<PoiStyle> roomPoiStyle() async {
  final icon = await (_pinImage ??= KImage.fromWidget(
    const Icon(Icons.place, color: AppColors.primary, size: _pinSize),
    const Size(_pinSize, _pinSize),
  ));
  return PoiStyle(icon: icon);
}

/// 지도를 못 띄울 때 화면에 놓을 안내.
///
/// **원인은 사용자에게 말하지 않는다.** 키 미설정·키 해시 미등록·ABI 는 개발자가 고칠 일이고,
/// 그 문구를 본 사용자는 할 수 있는 게 하나도 없다(예전에는 환경변수 이름과 카카오 개발자 콘솔
/// 안내가 그대로 떴다). 원인 구분은 [debugPrint] 로 내리고 화면에는 한 줄만 남긴다.
///
/// 웹만 예외다 — "모바일 앱에서 보라"는 사실이고 사용자가 실제로 할 수 있는 행동이다.
String mapUnavailableMessage() {
  if (kIsWeb) {
    return '지도는 모바일 앱에서만 볼 수 있어요. 아래 목록으로 확인해 주세요.';
  }
  if (!kakaoMapAbiSupported) {
    debugPrint('지도 비활성: 카카오맵은 ARM 전용 — x86 에뮬레이터에서는 뜨지 않는다');
  } else if (AppConfig.kakaoNativeAppKey.isEmpty) {
    debugPrint('지도 비활성: KAKAO_NATIVE_APP_KEY 가 비어 있다 (--dart-define 확인)');
  } else {
    debugPrint('지도 비활성: 카카오맵 인증 실패 — 콘솔에 패키지명·키 해시가 등록됐는지 확인');
  }
  return '지도를 지금 불러올 수 없어요. 아래 목록으로 확인해 주세요.';
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
