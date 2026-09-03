import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'recurring_models.dart';

final recurringRepositoryProvider = Provider<RecurringRepository>((ref) {
  return RecurringRepository(ref.watch(dioProvider));
});

class RecurringRepository {
  RecurringRepository(this._dio);

  final Dio _dio;

  /// 밴드의 활성 정기 규칙 목록 (최신 등록순).
  Future<List<RecurringRule>> list(int bandId) async {
    try {
      final res = await _dio.get<dynamic>('/bands/$bandId/recurring-rules');
      return unwrap(res, (d) {
        final list = (d! as Map<String, dynamic>)['rules'] as List<dynamic>;
        return list
            .map((e) => RecurringRule.fromJson(e as Map<String, dynamic>))
            .toList(growable: false);
      });
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 규칙 등록. 겹치는 회차가 있어도 성공(201) — 결과의 overlaps 로 안내한다.
  Future<RecurringWriteResult> create({
    required int bandId,
    required int roomId,
    required RecurringFrequency frequency,
    required String dayOfWeek,
    required String startTime,
    required String endTime,
    required String startDate,
    String? endDate,
    int? cost,
    String? note,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/recurring-rules',
        data: {
          'roomId': roomId,
          'frequency': recurringFrequencyWire(frequency),
          'dayOfWeek': dayOfWeek,
          'startTime': startTime,
          'endTime': endTime,
          'startDate': startDate,
          if (endDate != null && endDate.isNotEmpty) 'endDate': endDate,
          if (cost != null) 'cost': cost,
          if (note != null && note.isNotEmpty) 'note': note,
        },
      );
      return unwrap(
        res,
        (d) => RecurringWriteResult.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 규칙 삭제 — 아직 시작하지 않은 회차만 취소된다(과거 회차는 유지).
  Future<void> delete({required int bandId, required int ruleId}) async {
    try {
      await _dio.delete<dynamic>('/bands/$bandId/recurring-rules/$ruleId');
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
