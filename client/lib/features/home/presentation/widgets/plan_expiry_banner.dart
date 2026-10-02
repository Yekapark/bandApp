import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../routing/app_router.dart';
import '../../../plan/data/plan_models.dart';

/// 프리미엄이 끝나간다는 것을 밴드 홈에서 알린다.
///
/// 푸시는 밴드장에게만 간다(요금제를 바꿀 수 있는 사람). 하지만 사진·영상이 사라지는 건 밴드원
/// 모두의 일이고, 푸시를 꺼둔 사람도 있다. 그래서 홈에 한 줄 띄운다.
///
/// **평소에는 아무것도 보여주지 않는다.** 만료 30일 안이고, 정말로 끝나는 구독(해지 예약·쿠폰)일 때만 나온다.
/// 자동 갱신 중인 Google Play 구독은 만료일에 갱신되므로 띄우지 않는다(LAUNCH_REVIEW B6).
/// 갱신 결제가 보류됐으면(`onHold`) 모두에게 차분한 안내를 띄우고 요금제 화면으로 보낸다 — 결제한 사람이
/// 밴드원일 수도 있고, 밴드장이 새로 결제하면 두 번 청구되므로 화면에서 Play 로 고치라고 안내한다.
class PlanExpiryBanner extends StatelessWidget {
  const PlanExpiryBanner({
    super.key,
    required this.plan,
    required this.isLeader,
  });

  final BandPlan? plan;
  final bool isLeader;

  /// 이 안에 들면 알린다. 서버 예고 알림(30·7·1일 전)과 같은 기준이다.
  static const _noticeDays = 30;

  /// 배너 문구. 띄우지 않을 상황이면 null. 테스트가 쓴다.
  @visibleForTesting
  static String? messageFor(BandPlan? plan, DateTime now) =>
      _stateAt(plan, now)?.message;

  @override
  Widget build(BuildContext context) {
    final state = _stateAt(plan, DateTime.now());
    if (state == null) return const SizedBox.shrink();

    return Container(
      margin: const EdgeInsets.only(bottom: 18),
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      decoration: BoxDecoration(
        color: state.urgent
            ? AppColors.danger.withValues(alpha: 0.12)
            : AppColors.primary.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: state.urgent
              ? AppColors.danger.withValues(alpha: 0.45)
              : AppColors.primary.withValues(alpha: 0.35),
        ),
      ),
      child: Row(
        children: [
          Icon(
              state.hold
                  ? Icons.credit_card
                  : state.urgent
                      ? Icons.warning_amber_rounded
                      : Icons.schedule,
              size: 18,
              color: state.urgent ? AppColors.danger : AppColors.primary),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              state.message,
              style: const TextStyle(
                  fontSize: 12, color: AppColors.textDim, height: 1.45),
            ),
          ),
          // 멤버는 알기만 하면 된다. 요금제를 바꾸는 건 밴드장만 할 수 있어서,
          // 버튼을 모두에게 보여주면 눌러도 막히는 길이 된다.
          if (isLeader || state.hold) ...[
            const SizedBox(width: 6),
            TextButton(
              onPressed: () => context.push(Routes.plan),
              style: TextButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                minimumSize: const Size(0, 32),
              ),
              child: Text(
                  state.hold
                      ? '확인'
                      : state.urgent
                          ? '다시 시작'
                          : '연장',
                  style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color:
                          state.urgent ? AppColors.danger : AppColors.primary)),
            ),
          ],
        ],
      ),
    );
  }

  /// 배너를 띄울 상황인지, 띄운다면 뭐라고 할지. 아니면 null.
  static _BannerState? _stateAt(BandPlan? plan, DateTime now) {
    if (plan == null) return null;

    if (plan.onHold) {
      return const _BannerState(
        message: '카드 결제가 실패해 프리미엄이 잠시 멈췄어요. 결제한 사람이 Google Play 에서 '
            '결제 수단을 고치면 다시 이어져요.',
        urgent: false,
        hold: true,
      );
    }

    // 자동 갱신 중이면 만료일에 Google 이 1년 더 청구하고 서버가 연장한다 — 끝나지 않는다.
    // 예전에는 이런 밴드의 모든 멤버에게 "끝나요, 사진·영상이 사라져요" 를 보여 줬다(B6).
    if (plan.autoRenewing) return null;

    if (plan.tier == 'PREMIUM') {
      final expiresAt = plan.expiresAt;
      if (expiresAt == null) return null;
      final daysLeft = expiresAt.difference(now).inDays;
      if (daysLeft > _noticeDays) return null;
      if (daysLeft < 0) return null; // 서버 배치가 곧 정리한다
      final left = daysLeft == 0 ? '오늘' : '$daysLeft일 뒤';
      return _BannerState(
        message: '프리미엄이 $left 끝나요. 끝나면 30일 뒤부터 사진·영상이 차례로 사라져요.',
        urgent: daysLeft <= 7,
      );
    }

    // FREE 는 배너를 띄우지 않는다.
    //
    // "방금 강등돼 유예 중"과 "원래 무료"를 앱이 구분할 수 없어서다. FREE 의 startedAt 은
    // 밴드를 만든 시각이기도 해서, 그걸로 판단하면 **새로 만든 밴드마다 "프리미엄이 끝났어요"**
    // 가 뜬다. 만료 순간은 서버가 보내는 PLAN_EXPIRED 푸시가 알린다.
    //
    // 유예 중인 것도 홈에 띄우려면 서버가 그 상태를 알려줘야 한다(요금제 응답에 유예 종료
    // 시각을 싣는 식). 지금은 만료 전 예고까지만 한다.
    return null;
  }
}

class _BannerState {
  const _BannerState(
      {required this.message, required this.urgent, this.hold = false});

  final String message;
  final bool urgent;

  /// 결제 보류 안내. 밴드원에게도 요금제 화면 버튼을 보인다.
  final bool hold;
}
