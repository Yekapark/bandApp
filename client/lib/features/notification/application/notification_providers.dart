import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/notification_models.dart';
import '../data/notification_repository.dart';
import '../data/notification_seen_storage.dart';

/// 내 알림 설정(디바이스·토큰 아님 — 계정 단위). 로그인 토큰이 주인을 정한다.
final notificationSettingProvider =
    FutureProvider<NotificationSetting>((ref) async {
  return ref.watch(notificationRepositoryProvider).get();
});

/// 알림 목록 첫 페이지. 배지 계산과 화면이 함께 쓴다.
///
/// 무한 스크롤은 화면이 직접 커서로 이어 붙인다 — 목록이 길어질 일이 드물어
/// 별도 페이징 notifier 를 두지 않았다.
final notificationFeedProvider =
    FutureProvider.family<NotificationPage, int>((ref, bandId) async {
  return ref.watch(notificationRepositoryProvider).feed(bandId: bandId);
});

/// 안 읽은 알림 수 — 기기에 저장된 "마지막 확인 시각" 이후에 온 것만 센다.
/// 첫 페이지(기본 20건)만 보므로 그보다 많으면 20 에서 멈춘다.
final unreadNotificationCountProvider =
    FutureProvider.family<int, int>((ref, bandId) async {
  final page = await ref.watch(notificationFeedProvider(bandId).future);
  final seen = await ref.watch(notificationSeenStorageProvider).lastSeen(bandId);
  if (seen == null) return page.items.length;
  return page.items.where((n) => n.sentAt.isAfter(seen)).length;
});
