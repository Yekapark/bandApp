import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'reservation_models.dart';

final reservationRepositoryProvider = Provider<ReservationRepository>((ref) {
  return ReservationRepository(ref.watch(dioProvider));
});

class ReservationRepository {
  ReservationRepository(this._dio);

  final Dio _dio;

  /// [from]~[to] 구간과 겹치는 일정을 startAt 오름차순으로.
  Future<List<Reservation>> list({
    required int bandId,
    required DateTime from,
    required DateTime to,
    bool includeInactive = false,
  }) async {
    try {
      final res = await _dio.get<dynamic>(
        '/bands/$bandId/reservations',
        queryParameters: {
          'from': from.toUtc().toIso8601String(),
          'to': to.toUtc().toIso8601String(),
          'includeInactive': includeInactive,
        },
      );
      return unwrap(res, (d) {
        final list =
            (d! as Map<String, dynamic>)['reservations'] as List<dynamic>;
        return list
            .map((e) => Reservation.fromJson(e as Map<String, dynamic>))
            .toList(growable: false);
      });
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
