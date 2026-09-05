import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_client.dart';
import 'notification_models.dart';

final notificationRepositoryProvider = Provider<NotificationRepository>((ref) {
  return NotificationRepository(ref.watch(dioProvider));
});

class NotificationRepository {
  NotificationRepository(this._dio);

  final Dio _dio;

  static const _feed = '/notifications';
  static const _settings = '/notifications/settings';
  static const _deviceTokens = '/notifications/device-tokens';

  /// 받은 알림 목록(최신순). [cursor] 는 직전 페이지의 nextCursor.
  ///
  /// 알림 목록 기능 이전에 발송된 알림은 서버에 문구가 남아 있지 않아 빠진다.
  Future<NotificationPage> feed({
    required int bandId,
    int? cursor,
    int size = 20,
  }) async {
    try {
      final res = await _dio.get<dynamic>(
        _feed,
        queryParameters: {
          'bandId': bandId,
          'size': size,
          if (cursor != null) 'cursor': cursor,
        },
      );
      return unwrap(
        res,
        (d) => NotificationPage.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// FCM 디바이스 토큰 등록/갱신 (upsert). [platform]: IOS | ANDROID | WEB.
  Future<void> registerDeviceToken({
    required String token,
    required String platform,
  }) async {
    try {
      await _dio.post<dynamic>(
        _deviceTokens,
        data: {'token': token, 'platform': platform},
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 디바이스 토큰 해제 (로그아웃 시).
  Future<void> unregisterDeviceToken(String token) async {
    try {
      await _dio.delete<dynamic>(
        _deviceTokens,
        queryParameters: {'token': token},
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// 내 알림 설정. 서버는 없으면 기본값(pushEnabled=true, reminderOffsets=[60])을 만들어 준다.
  Future<NotificationSetting> get() async {
    try {
      final res = await _dio.get<dynamic>(_settings);
      return unwrap(
        res,
        (d) => NotificationSetting.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }

  /// PUT 전체 교체. 빈 [reminderOffsets] 는 "리마인더 없음".
  Future<NotificationSetting> update({
    required bool pushEnabled,
    required List<int> reminderOffsets,
  }) async {
    try {
      final res = await _dio.put<dynamic>(
        _settings,
        data: {
          'pushEnabled': pushEnabled,
          'reminderOffsets': reminderOffsets,
        },
      );
      return unwrap(
        res,
        (d) => NotificationSetting.fromJson(d! as Map<String, dynamic>),
      );
    } on DioException catch (e) {
      throw ApiException.fromDio(e);
    }
  }
}
