import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';

import '../../../core/system/system_settings.dart';
import '../../../core/theme/app_colors.dart';

/// 휴대폰에서 이 앱의 알림이 꺼져 있으면 보여 주는 안내와 "알림 켜기" 버튼.
///
/// 앱 안의 "푸시 알림" 스위치가 켜져 있어도 휴대폰(Android 13+)에서 알림 권한을 안 줬으면 아무것도 안 뜬다.
/// 테스터가 "처음에 권한을 안 물어봐서 휴대폰 설정에 가서 켜야 했다" 고 했다(2026-10-01). 권한 창을 다시 띄우고,
/// 이미 두 번 거절해 창이 안 뜨면 휴대폰의 앱 알림 설정을 바로 연다. 설정에서 돌아오면 다시 확인한다.
class OsPermissionCard extends StatefulWidget {
  const OsPermissionCard({super.key});

  @override
  State<OsPermissionCard> createState() => _OsPermissionCardState();
}

class _OsPermissionCardState extends State<OsPermissionCard>
    with WidgetsBindingObserver {
  bool _denied = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _check();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _check();
  }

  Future<void> _check() async {
    if (Firebase.apps.isEmpty) return; // 푸시 설정이 없는 빌드
    try {
      final s = await FirebaseMessaging.instance.getNotificationSettings();
      if (!mounted) return;
      setState(() => _denied = s.authorizationStatus == AuthorizationStatus.denied ||
          s.authorizationStatus == AuthorizationStatus.notDetermined);
    } catch (_) {
      // 확인 못 하면 안내를 띄우지 않는다.
    }
  }

  Future<void> _turnOn() async {
    try {
      final s = await FirebaseMessaging.instance.requestPermission();
      if (s.authorizationStatus == AuthorizationStatus.authorized) {
        await _check();
        return;
      }
    } catch (_) {}
    // 권한 창이 더 뜨지 않는 경우(두 번 거절) — 휴대폰 설정으로.
    await SystemSettings.openNotificationSettings();
  }

  @override
  Widget build(BuildContext context) {
    if (!_denied) return const SizedBox.shrink();
    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.danger.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(13),
        border: Border.all(color: AppColors.danger.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('휴대폰에서 밴듈 알림이 꺼져 있어요',
              style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          const Text(
            '아래 설정을 켜도 휴대폰에서 허용하지 않으면 알림이 오지 않아요.',
            style: TextStyle(fontSize: 11.5, color: AppColors.textDim, height: 1.5),
          ),
          const SizedBox(height: 10),
          OutlinedButton(
            onPressed: _turnOn,
            child: const Text('알림 켜기'),
          ),
        ],
      ),
    );
  }
}
