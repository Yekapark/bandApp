import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'invite_models.dart';

final inviteRepositoryProvider = Provider<InviteRepository>((ref) {
  return InviteRepository(ref.watch(dioProvider));
});

class InviteRepository {
  InviteRepository(this._dio);

  final Dio _dio;

  /// 현재 활성 초대코드. 없으면(404 INVITE_NOT_FOUND) null. 그 외 오류는 던진다.
  Future<BandInvite?> current(int bandId) async {
    try {
      final res = await _dio.get<dynamic>('/bands/$bandId/invites/current');
      if (res.statusCode == 404) return null;
      return unwrap(
          res, (d) => BandInvite.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 발급/재발급. 기존 활성 코드는 즉시 무효화된다. 밴드장만.
  Future<BandInvite> issue({
    required int bandId,
    int? maxUses,
    int? ttlDays,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/bands/$bandId/invites',
        data: {
          if (maxUses != null) 'maxUses': maxUses,
          if (ttlDays != null) 'ttlDays': ttlDays,
        },
      );
      return unwrap(
          res, (d) => BandInvite.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 현재 활성 코드 무효화 (204, 멱등). 밴드장만.
  Future<void> revoke(int bandId) async {
    try {
      await _dio.delete<dynamic>('/bands/$bandId/invites/current');
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
