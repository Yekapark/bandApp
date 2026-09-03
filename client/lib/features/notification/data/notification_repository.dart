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

  static const _settings = '/notifications/settings';

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
