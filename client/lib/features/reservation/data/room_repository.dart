import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'place_models.dart';
import 'room_models.dart';

final roomRepositoryProvider = Provider<RoomRepository>((ref) {
  return RoomRepository(ref.watch(dioProvider));
});

class RoomRepository {
  RoomRepository(this._dio);

  final Dio _dio;

  /// usageCount 내림차순.
  Future<List<Room>> list(int bandId) async {
    try {
      final res = await _dio.get<dynamic>('/bands/$bandId/rooms');
      return unwrap(res, (d) {
        final list = (d! as Map<String, dynamic>)['rooms'] as List<dynamic>;
        return list
            .map((e) => Room.fromJson(e as Map<String, dynamic>))
            .toList(growable: false);
      });
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 카카오 로컬 검색으로 합주실 이름·주소 후보를 최대 5건(좌표 포함). 서버에 검색 키가 없으면 빈 목록.
  Future<List<PlaceSuggestion>> searchPlaces({
    required int bandId,
    required String query,
  }) async {
    try {
      final res = await _dio.get<dynamic>(
        '/bands/$bandId/rooms/search',
        queryParameters: {'query': query},
      );
      return unwrap(res, (d) {
        final list = (d! as Map<String, dynamic>)['places'] as List<dynamic>;
        return list
            .map((e) => PlaceSuggestion.fromJson(e as Map<String, dynamic>))
            .toList(growable: false);
      });
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// [lat]/[lng] 를 주면(검색에서 고른 후보의 좌표) 서버가 지오코딩 없이 그대로 저장한다.
  /// 직접 입력한 주소처럼 좌표가 없으면 서버가 주소로 지오코딩한다.
  Future<Room> create({
    required int bandId,
    required String name,
    String? address,
    String? phone,
    String? memo,
    double? lat,
    double? lng,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/rooms',
        data: {
          'name': name,
          if (address != null && address.isNotEmpty) 'address': address,
          if (phone != null && phone.isNotEmpty) 'phone': phone,
          if (memo != null && memo.isNotEmpty) 'memo': memo,
          if (lat != null && lng != null) ...{'lat': lat, 'lng': lng},
        },
      );
      return unwrap(res, (d) => Room.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 합주실 수정 (PUT 전체 교체).
  ///
  /// [lat]/[lng] 를 주면 주소 변경 여부와 무관하게 그 좌표로 덮어쓴다 — 좌표 없이 저장됐던 방을
  /// 검색으로 다시 골라 채울 수 있다. 안 주면 주소가 실제로 바뀐 경우에만 서버가 다시 계산한다.
  Future<Room> update({
    required int bandId,
    required int roomId,
    required String name,
    String? address,
    String? phone,
    String? memo,
    double? lat,
    double? lng,
  }) async {
    try {
      final res = await _dio.put<dynamic>(
        '/bands/$bandId/rooms/$roomId',
        data: {
          'name': name,
          if (address != null && address.isNotEmpty) 'address': address,
          if (phone != null && phone.isNotEmpty) 'phone': phone,
          if (memo != null && memo.isNotEmpty) 'memo': memo,
          if (lat != null && lng != null) ...{'lat': lat, 'lng': lng},
        },
      );
      return unwrap(res, (d) => Room.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 합주실 삭제(soft). 이미 등록된 일정에는 영향 없다(roomName 은 응답에 계속 채워짐).
  Future<void> delete({required int bandId, required int roomId}) async {
    try {
      await _dio.delete<dynamic>('/bands/$bandId/rooms/$roomId');
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
