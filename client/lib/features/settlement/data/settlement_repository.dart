import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'settlement_models.dart';

final settlementRepositoryProvider = Provider<SettlementRepository>((ref) {
  return SettlementRepository(ref.watch(dioProvider));
});

class SettlementRepository {
  SettlementRepository(this._dio);

  final Dio _dio;

  String _base(int bandId, int reservationId) =>
      '/bands/$bandId/reservations/$reservationId/settlement';

  /// 정산 현황. 아직 정산이 없으면 null (404 SETTLEMENT_NOT_FOUND).
  Future<Settlement?> get({
    required int bandId,
    required int reservationId,
  }) async {
    try {
      final res = await _dio.get<dynamic>(_base(bandId, reservationId));
      return unwrap(
        res,
        (d) => Settlement.fromJson(d! as Map<String, dynamic>),
      );
    } on ApiException catch (e) {
      if (e.statusCode == 404) return null;
      rethrow;
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 정산 생성. 등록자 본인 또는 밴드장만.
  Future<Settlement> create({
    required int bandId,
    required int reservationId,
    required int totalAmount,
    required SplitType splitType,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        _base(bandId, reservationId),
        data: {
          'totalAmount': totalAmount,
          'splitType': splitTypeWire(splitType),
        },
      );
      return unwrap(
        res,
        (d) => Settlement.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 재계산 — 현재 멤버·참석자 기준으로 몫을 다시 만든다. 값 생략 시 기존 유지.
  Future<Settlement> recalculate({
    required int bandId,
    required int reservationId,
    int? totalAmount,
    SplitType? splitType,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '${_base(bandId, reservationId)}/recalculate',
        data: {
          if (totalAmount != null) 'totalAmount': totalAmount,
          if (splitType != null) 'splitType': splitTypeWire(splitType),
        },
      );
      return unwrap(
        res,
        (d) => Settlement.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 내 몫 납부 상태 변경. 변경 후 전체 현황을 돌려준다.
  Future<Settlement> markPaid({
    required int bandId,
    required int reservationId,
    required int userId,
    required bool paid,
  }) async {
    try {
      final res = await _dio.put<dynamic>(
        '${_base(bandId, reservationId)}/shares/$userId',
        data: {'paid': paid},
      );
      return unwrap(
        res,
        (d) => Settlement.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
