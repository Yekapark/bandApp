import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'plan_models.dart';

final planRepositoryProvider = Provider<PlanRepository>((ref) {
  return PlanRepository(ref.watch(dioProvider));
});

/// 밴드 FREE/PREMIUM 요금제. 조회는 멤버, 전환(구독/해지/연장)은 밴드장만.
/// 실제 결제는 앱 밖(스토어) 몫이고 백엔드는 no-op 게이트웨이라 버튼만 있으면 전환된다.
class PlanRepository {
  PlanRepository(this._dio);

  final Dio _dio;

  Future<BandPlan> view(int bandId) => _get('/bands/$bandId/plan');
  Future<BandPlan> subscribe(int bandId) =>
      _post('/bands/$bandId/plan/subscribe');
  Future<BandPlan> cancel(int bandId) => _post('/bands/$bandId/plan/cancel');
  Future<BandPlan> renew(int bandId) => _post('/bands/$bandId/plan/renew');

  Future<BandPlan> _get(String path) async {
    try {
      final res = await _dio.get<dynamic>(path);
      return unwrap(res, (d) => BandPlan.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<BandPlan> _post(String path) async {
    try {
      final res = await _dio.post<dynamic>(path);
      return unwrap(res, (d) => BandPlan.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
