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

  /// 일정 + 참석 현황 + 셋리스트.
  Future<ReservationDetail> detail({
    required int bandId,
    required int reservationId,
  }) async {
    try {
      final res = await _dio.get<dynamic>(
        '/bands/$bandId/reservations/$reservationId',
      );
      return unwrap(
        res,
        (d) => ReservationDetail.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 일정 등록. 겹침이 있어도 성공(201) — 결과의 overlaps 로 안내한다.
  Future<ReservationWriteResult> create({
    required int bandId,
    required int roomId,
    required DateTime startAt,
    required DateTime endAt,
    int? cost,
    String? note,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/reservations',
        data: {
          'roomId': roomId,
          'startAt': startAt.toUtc().toIso8601String(),
          'endAt': endAt.toUtc().toIso8601String(),
          if (cost != null) 'cost': cost,
          if (note != null && note.isNotEmpty) 'note': note,
        },
      );
      return unwrap(
        res,
        (d) => ReservationWriteResult.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 일정 수정 (PUT 전체 교체). 등록자 본인 또는 밴드장. 겹침이 있어도 성공(200).
  Future<ReservationWriteResult> update({
    required int bandId,
    required int reservationId,
    required int roomId,
    required DateTime startAt,
    required DateTime endAt,
    int? cost,
    String? note,
  }) async {
    try {
      final res = await _dio.put<dynamic>(
        '/bands/$bandId/reservations/$reservationId',
        data: {
          'roomId': roomId,
          'startAt': startAt.toUtc().toIso8601String(),
          'endAt': endAt.toUtc().toIso8601String(),
          if (cost != null) 'cost': cost,
          if (note != null && note.isNotEmpty) 'note': note,
        },
      );
      return unwrap(
        res,
        (d) => ReservationWriteResult.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 승인 대기 일정을 확정으로 (밴드장 전용, APPROVAL_REQUIRED 밴드).
  Future<void> approve({
    required int bandId,
    required int reservationId,
  }) async {
    try {
      await _dio.post<dynamic>(
        '/bands/$bandId/reservations/$reservationId/approve',
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 승인 대기 일정을 거절로 (밴드장 전용). 합주실 usageCount 를 되돌린다.
  Future<void> reject({
    required int bandId,
    required int reservationId,
  }) async {
    try {
      await _dio.post<dynamic>(
        '/bands/$bandId/reservations/$reservationId/reject',
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 일정 취소 (등록자 본인 또는 밴드장).
  Future<void> cancel({
    required int bandId,
    required int reservationId,
  }) async {
    try {
      await _dio.delete<dynamic>(
        '/bands/$bandId/reservations/$reservationId',
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 내 참석 상태 변경. 변경 후 전체 참석 현황을 돌려준다.
  Future<AttendanceBoard> respondAttendance({
    required int bandId,
    required int reservationId,
    required int userId,
    required AttendanceStatus status,
  }) async {
    try {
      final res = await _dio.put<dynamic>(
        '/bands/$bandId/reservations/$reservationId/attendances/$userId',
        data: {'status': attendanceStatusWire(status)},
      );
      return unwrap(
        res,
        (d) => AttendanceBoard.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 셋리스트에 곡 추가 (맨 뒤). 밴드 멤버 누구나.
  Future<SetlistItem> addSetlistItem({
    required int bandId,
    required int reservationId,
    required String title,
    String? artist,
    String? referenceUrl,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/reservations/$reservationId/setlist',
        data: {
          'title': title,
          if (artist != null && artist.isNotEmpty) 'artist': artist,
          if (referenceUrl != null && referenceUrl.isNotEmpty)
            'referenceUrl': referenceUrl,
        },
      );
      return unwrap(
        res,
        (d) => SetlistItem.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 셋리스트 곡 정보 수정 (PUT 전체 교체 — 순서는 안 바뀜).
  Future<SetlistItem> updateSetlistItem({
    required int bandId,
    required int reservationId,
    required int itemId,
    required String title,
    String? artist,
    String? referenceUrl,
  }) async {
    try {
      final res = await _dio.put<dynamic>(
        '/bands/$bandId/reservations/$reservationId/setlist/$itemId',
        data: {
          'title': title,
          if (artist != null && artist.isNotEmpty) 'artist': artist,
          if (referenceUrl != null && referenceUrl.isNotEmpty)
            'referenceUrl': referenceUrl,
        },
      );
      return unwrap(
        res,
        (d) => SetlistItem.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 셋리스트 재정렬. [itemIds] 는 그 일정의 **모든** 항목 id 를 원하는 순서로 나열해야 한다.
  Future<void> reorderSetlist({
    required int bandId,
    required int reservationId,
    required List<int> itemIds,
  }) async {
    try {
      await _dio.put<dynamic>(
        '/bands/$bandId/reservations/$reservationId/setlist/reorder',
        data: {'itemIds': itemIds},
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 셋리스트 곡 삭제.
  Future<void> deleteSetlistItem({
    required int bandId,
    required int reservationId,
    required int itemId,
  }) async {
    try {
      await _dio.delete<dynamic>(
        '/bands/$bandId/reservations/$reservationId/setlist/$itemId',
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
