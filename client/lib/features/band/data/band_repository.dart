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
}
