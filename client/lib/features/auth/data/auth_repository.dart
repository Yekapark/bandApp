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
      return unwrap(
          res, (d) => AuthResult.fromJson(d! as Map<String, dynamic>));
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
      return unwrap(
          res, (d) => AuthResult.fromJson(d! as Map<String, dynamic>));
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
      return unwrap(
          res, (d) => AuthResult.fromJson(d! as Map<String, dynamic>));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 비밀번호 재설정 인증번호 발송. 없는 이메일·소셜 계정이어도 성공(204)으로 온다 —
  /// 계정 존재 여부를 드러내지 않는다.
  Future<void> requestPasswordReset({required String email}) async {
    try {
      ensureSuccess(await _dio.post<dynamic>(
        '/auth/password-reset/request',
        data: {'email': email},
      ));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 인증번호와 새 비밀번호로 재설정. 번호가 틀리거나 만료면
  /// 400 `PASSWORD_RESET_CODE_INVALID`. 성공하면 그 계정의 모든 기기 세션이 로그아웃된다.
  Future<void> confirmPasswordReset({
    required String email,
    required String code,
    required String newPassword,
  }) async {
    try {
      ensureSuccess(await _dio.post<dynamic>(
        '/auth/password-reset/confirm',
        data: {'email': email, 'code': code, 'newPassword': newPassword},
      ));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 세션 정리. [deviceToken] 을 주면 서버가 이 기기의 푸시 토큰도 지운다 — 로그아웃한 폰에 이전 계정 알림이
  /// 가지 않게(U1). 인증이 필요 없는 요청이라 토큰을 지운 뒤에도, 세션이 만료된 뒤에도 통한다.
  Future<void> logout({required String refreshToken, String? deviceToken}) async {
    try {
      await _dio.post<dynamic>('/auth/logout', data: {
        'refreshToken': refreshToken,
        if (deviceToken != null) 'deviceToken': deviceToken,
      });
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

  /// 회원 탈퇴. 이메일 계정은 [password] 재확인 필요, 소셜 계정은 null 로 호출한다.
  /// 탈퇴 즉시 기존 토큰이 막히고 소속 밴드에서 자동 탈퇴한다.
  Future<void> withdraw({String? password}) async {
    try {
      ensureSuccess(await _dio.post<dynamic>(
        '/users/me/withdraw',
        data: password == null ? <String, dynamic>{} : {'password': password},
      ));
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
