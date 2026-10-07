import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../auth/application/auth_controller.dart';
import '../../plan/data/iap_service.dart';

/// 계정 — 내 정보 표시와 회원 탈퇴.
class AccountScreen extends ConsumerStatefulWidget {
  const AccountScreen({super.key});

  @override
  ConsumerState<AccountScreen> createState() => _AccountScreenState();
}

class _AccountScreenState extends ConsumerState<AccountScreen> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(authControllerProvider).user;
    final isEmail = user?.socialProvider == null;

    return Scaffold(
      appBar: AppBar(
        title: const Text('계정',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 40),
        children: [
          _InfoRow(label: '이름', value: user?.name ?? '-'),
          _InfoRow(
            label: '로그인 방식',
            // 서버 값(KAKAO)을 그대로 보이지 않는다.
            value: isEmail
                ? '이메일'
                : (user?.socialProvider == 'KAKAO' ? '카카오' : '소셜 로그인'),
          ),
          if (user?.email != null) _InfoRow(label: '이메일', value: user!.email!),
          const SizedBox(height: 36),
          const Text('회원 탈퇴',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          const Text(
            '탈퇴하면 계정이 즉시 삭제되고 소속 밴드에서 자동으로 나갑니다. '
            '내가 밴드장인 밴드는 가장 먼저 가입한 멤버에게 밴드장이 넘어가요. 되돌릴 수 없어요.',
            style:
                TextStyle(fontSize: 12, color: AppColors.textDim, height: 1.6),
          ),
          const SizedBox(height: 10),
          // 결제한 사람이 탈퇴하면 서버가 Play 에 자동 결제 해지를 "요청"한다(B13) — 실패하면 재시도하고, 결제자
          // 기록이 없는 옛 구매는 대상이 아니라 해지를 장담하지 않는다(QA-R01). 이미 낸 기간은 환불되지 않는다.
          const Text(
            '내가 Google Play 로 결제한 프리미엄 구독은 탈퇴하면 다음 자동 결제를 멈추도록 Google Play 에 '
            '요청해요. 이미 낸 기간은 끝까지 쓸 수 있고 환불되지 않아요. 혹시 계속 결제되면 '
            'Google Play › 구독에서 직접 해지해 주세요.',
            style:
                TextStyle(fontSize: 12, color: AppColors.textDim, height: 1.6),
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: _openManageSubscriptions,
              style: TextButton.styleFrom(padding: EdgeInsets.zero),
              child: const Text('Google Play 에서 구독 관리'),
            ),
          ),
          const SizedBox(height: 16),
          OutlinedButton(
            onPressed: _busy ? null : () => _withdraw(isEmail),
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(50),
              side: const BorderSide(color: AppColors.danger),
              foregroundColor: AppColors.danger,
            ),
            child: _busy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: AppColors.danger),
                  )
                : const Text('회원 탈퇴'),
          ),
        ],
      ),
    );
  }

  Future<void> _withdraw(bool isEmail) async {
    final controller = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        // 작은 화면에서 키보드가 열리면 내용이 창 밖으로 넘친다(QA-R24).
        scrollable: true,
        title: const Text('정말 탈퇴할까요?', style: TextStyle(fontSize: 16)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '이 작업은 되돌릴 수 없어요.',
              style: TextStyle(fontSize: 12.5, color: AppColors.textDim),
            ),
            const SizedBox(height: 8),
            const Text(
              '내가 결제한 구독은 다음 자동 결제를 멈추도록 Google Play 에 요청해요. 남은 기간은 환불되지 않아요.',
              style: TextStyle(
                  fontSize: 12.5, color: AppColors.textDim, height: 1.5),
            ),
            if (isEmail) ...[
              const SizedBox(height: 14),
              TextField(
                controller: controller,
                obscureText: true,
                autofocus: true,
                decoration: const InputDecoration(hintText: '현재 비밀번호'),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('탈퇴', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    if (isEmail && controller.text.isEmpty) {
      _toast('비밀번호를 입력해 주세요.');
      return;
    }

    setState(() => _busy = true);
    try {
      await ref.read(authControllerProvider.notifier).withdraw(
            password: isEmail ? controller.text : null,
          );
      // redirect 가 로그인 화면으로 보낸다.
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('탈퇴하지 못했어요.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openManageSubscriptions() async {
    final ok = await IapService.openManageSubscriptions();
    if (!ok) {
      _toast('Play 스토어를 열지 못했어요. Play 스토어 › 결제 및 구독 › 구독에서 확인해 주세요.');
    }
  }

  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(msg)));
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Row(
        children: [
          SizedBox(
            width: 90,
            child: Text(label,
                style: const TextStyle(fontSize: 12, color: AppColors.textDim)),
          ),
          Expanded(
            child: Text(value,
                style: const TextStyle(
                    fontSize: 13.5, fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}
