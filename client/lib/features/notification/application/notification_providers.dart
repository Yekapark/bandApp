import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/notification_models.dart';
import '../data/notification_repository.dart';

/// 내 알림 설정(디바이스·토큰 아님 — 계정 단위). 로그인 토큰이 주인을 정한다.
final notificationSettingProvider =
    FutureProvider<NotificationSetting>((ref) async {
  return ref.watch(notificationRepositoryProvider).get();
});
