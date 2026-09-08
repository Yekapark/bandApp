import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'plan_models.dart';

final planRepositoryProvider = Provider<PlanRepository>((ref) {
  return PlanRepository(ref.watch(dioProvider));
});

/// 밴드 FREE/PREMIUM 요금제. 조회는 멤버, 결제 검증·쿠폰은 밴드장만.
///
/// 구독은 스토어(Play Billing)에서 산다 — 클라이언트가 결제를 끝내고 받은 구매 토큰을
/// [verifyGooglePurchase] 로 보내면 서버가 스토어에 확인하고 PREMIUM 으로 올린다.
/// 해지·연장은 사용자가 Play 스토어에서 하고, 서버는 RTDN 웹훅으로 받는다 — 앱에 해지/연장 API 는 없다.
class PlanRepository {
  PlanRepository(this._dio);

  final Dio _dio;

  Future<BandPlan> view(int bandId) => _get('/bands/$bandId/plan');

  /// Play 결제로 받은 구매 토큰을 서버에 검증받아 PREMIUM 으로 전환한다.
  Future<BandPlan> verifyGooglePurchase(int bandId, String purchaseToken) =>
      _post('/bands/$bandId/plan/google/verify',
          body: {'purchaseToken': purchaseToken});

  /// 맛보기 쿠폰 사용. 이미 PREMIUM 이면 남은 기간에 더해진다.
  /// 발급은 운영자가 직접 하고 앱에는 사용 화면만 있다.
  Future<BandPlan> redeemCoupon(int bandId, String code) =>
      _post('/bands/$bandId/plan/coupons/redeem', body: {'code': code});

  Future<BandPlan> _get(String path) async {
    try {
      final res = await _dio.get<dynamic>(path);
      return unwrap(res, (d) => BandPlan.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<BandPlan> _post(String path, {Map<String, dynamic>? body}) async {
    try {
      final res = await _dio.post<dynamic>(path, data: body);
      return unwrap(res, (d) => BandPlan.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
