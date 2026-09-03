import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'auth_models.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(ref.watch(dioProvider));
});

/// `/api/v1/auth/**` + `/api/v1/users/me` 호출. 전부 무인증 or 토큰 자동 부착.
class AuthRepository {
  AuthRepository(this._dio);

  final Dio _dio;

  Future<AuthResult> signup({
    required String email,
    required String password,
    required String name,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/auth/signup',
        data: {'email': email, 'password': password, 'name': name},
      );
      return unwrap(res, (d) => AuthResult.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<AuthResult> login({
    required String email,
    required String password,
  }) async {
    try {
      final res = await _dio.post<dynamic>(
        '/auth/login',
        data: {'email': email, 'password': password},
      );
      return unwrap(res, (d) => AuthResult.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<AuthResult> kakao({required String kakaoAccessToken}) async {
    try {
      final res = await _dio.post<dynamic>(
        '/auth/kakao',
        data: {'accessToken': kakaoAccessToken},
      );
      return unwrap(res, (d) => AuthResult.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  Future<void> logout({required String refreshToken}) async {
    try {
      await _dio.post<dynamic>('/auth/logout', data: {'refreshToken': refreshToken});
    } on DioException catch (_) {
      // 로그아웃은 멱등 — 실패해도 로컬 토큰은 지운다.
    }
  }

  Future<AppUser> me() async {
    try {
      final res = await _dio.get<dynamic>('/users/me');
      return unwrap(res, (d) => AppUser.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
