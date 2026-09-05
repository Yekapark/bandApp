import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'band_models.dart';

final bandRepositoryProvider = Provider<BandRepository>((ref) {
  return BandRepository(ref.watch(dioProvider));
});

class BandRepository {
  BandRepository(this._dio);

  final Dio _dio;

  Future<List<MyBand>> myBands() async {
    try {
      final res = await _dio.get<dynamic>('/bands');
      return unwrap(res, (d) {
        final list = (d! as Map<String, dynamic>)['bands'] as List<dynamic>;
        return list
            .map((e) => MyBand.fromJson(e as Map<String, dynamic>))
            .toList(growable: false);
      });
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<Band> createBand(String name) async {
    try {
      final res = await _dio.post<dynamic>('/bands', data: {'name': name});
      return unwrap(res, (d) => Band.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 밴드 기본 정보(일정 등록 권한 모드 포함).
  Future<Band> band(int bandId) async {
    try {
      final res = await _dio.get<dynamic>('/bands/$bandId');
      return unwrap(res, (d) => Band.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<Band> joinBand(String code) async {
    try {
      final res = await _dio.post<dynamic>('/bands/join', data: {'code': code});
      return unwrap(res, (d) => Band.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<List<BandMember>> members(int bandId) async {
    try {
      final res = await _dio.get<dynamic>('/bands/$bandId/members');
      return unwrap(res, (d) {
        final list = (d! as Map<String, dynamic>)['members'] as List<dynamic>;
        return list
            .map((e) => BandMember.fromJson(e as Map<String, dynamic>))
            .toList(growable: false);
      });
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 일정 등록 권한 모드 변경 (밴드장 전용).
  /// [permission]: LEADER_ONLY | ANYONE | APPROVAL_REQUIRED.
  Future<Band> updateSettings({
    required int bandId,
    required String permission,
  }) async {
    try {
      final res = await _dio.put<dynamic>(
        '/bands/$bandId/settings',
        data: {'reservationPermission': permission},
      );
      return unwrap(res, (d) => Band.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 밴드장 위임 (현재 밴드장만). 위임하면 나는 MEMBER 로 강등된다.
  Future<Band> delegateLeadership({
    required int bandId,
    required int newLeaderUserId,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/leader',
        data: {'newLeaderUserId': newLeaderUserId},
      );
      return unwrap(res, (d) => Band.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 멤버 추방 (밴드장 전용).
  Future<void> kickMember({
    required int bandId,
    required int targetUserId,
  }) async {
    try {
      await _dio.delete<dynamic>('/bands/$bandId/members/$targetUserId');
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 밴드 나가기. 밴드장은 먼저 위임해야 한다(409 LEADER_MUST_DELEGATE_BEFORE_LEAVING).
  Future<void> leaveBand(int bandId) async {
    try {
      await _dio.post<dynamic>('/bands/$bandId/members/leave');
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 밴드 삭제. 밴드장만이고 **되돌릴 수 없다** — 일정·정산·게시글·사진/영상까지 전부 지워진다.
  /// [confirmName] 이 실제 밴드 이름과 다르면 400 BAND_NAME_MISMATCH.
  ///
  /// 확인 본문이 필요해서 DELETE 가 아니라 POST 다(본문 있는 DELETE 는 지원이 고르지 않다).
  Future<void> deleteBand(int bandId, String confirmName) async {
    try {
      await _dio.post<dynamic>('/bands/$bandId/delete',
          data: {'confirmName': confirmName});
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
