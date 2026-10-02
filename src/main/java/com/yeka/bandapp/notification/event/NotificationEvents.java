package com.yeka.bandapp.notification.event;

import java.time.Instant;
import java.util.List;

/**
 * 다른 도메인이 알림을 요청할 때 발행하는 이벤트들(DTO 성격). {@code ReservationService}·
 * {@code SettlementService}는 {@code ApplicationEventPublisher}로 이 레코드를 던지기만 하고,
 * {@link NotificationEventListener}가 <b>트랜잭션 커밋 후</b> 받아 발송한다 — 발송(FCM HTTP)이
 * 서비스의 트랜잭션·비관적 락 안에서 일어나지 않도록(CLAUDE.md 규칙).
 *
 * <p>수신자 목록은 이벤트를 발행하는 쪽이 이미 갖고 있으므로(멤버 조회 등) 이벤트에 실어 보낸다.
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /** 새 일정이 바로 확정됨(LEADER_ONLY / ANYONE). 등록자를 뺀 밴드 멤버 전원에게. */
    public record ReservationCreated(long bandId, long reservationId, Instant startAt,
                                     List<Long> recipientUserIds) {
    }

    /** 승인 대기 일정이 생김(APPROVAL_REQUIRED, 또는 확정 일정의 시간·장소 변경으로 재승인). 밴드장에게. */
    public record ReservationApprovalRequested(long bandId, long reservationId, Instant startAt,
                                               List<Long> recipientUserIds) {
    }

    /** 밴드장이 승인/거절함. 일정 등록자에게. */
    public record ReservationDecided(long bandId, long reservationId, Instant startAt,
                                     long requesterUserId, boolean approved) {
    }

    /**
     * 일정 시작 시각이 바뀜. 알림을 보내지는 않는다 — 이미 보낸 리마인더 기록을 지워 새 시각 기준으로 다시 나가게 한다.
     * 예전에는 시각을 바꿔도 "이 일정의 6시간 전 알림은 보냈다" 기록이 남아 바뀐 시각의 알림이 오지 않았다(테스터 의견 2026-10-01).
     */
    public record ReservationRescheduled(long reservationId) {
    }

    /**
     * 확정 일정의 시간·합주실이 바뀜. 수정자를 뺀 멤버에게. {@code variant} 는 바뀐 값(시각·합주실)에서 만든 수 —
     * 같은 일정을 여러 번 고쳐도 고칠 때마다 알림이 간다(발송 이력의 멱등 키가 {@code variant} 를 포함).
     */
    public record ReservationChanged(long bandId, long reservationId, Instant startAt, int variant,
                                     List<Long> recipientUserIds) {
    }

    /** 정기 규칙이 삭제돼 미래 회차 {@code cancelledCount}개가 취소됨. 삭제자를 뺀 멤버에게 한 번. */
    public record RecurringRuleCancelled(long bandId, long ruleId, int cancelledCount,
                                         List<Long> recipientUserIds) {
    }

    /** 일정이 취소됨. 취소자를 뺀 밴드 멤버 전원에게. */
    public record ReservationCancelled(long bandId, long reservationId, Instant startAt,
                                       List<Long> recipientUserIds) {
    }

    /** 정산이 생성/재계산됨. 분담 대상자에게. */
    public record SettlementRequested(long bandId, long reservationId, int totalAmount,
                                      List<Long> recipientUserIds) {
    }

    /**
     * 재계산으로 내 몫이 바뀌어 납부 체크가 풀림. 그 멤버에게 바뀐 금액을 알린다(재계산한 사람 제외).
     * 이 멤버들은 같은 재계산의 {@link SettlementRequested} 수신자에서 빠진다 — 알림이 두 개 겹치지 않게.
     */
    public record SettlementShareChanged(long bandId, long reservationId, List<ShareChange> changes) {
    }

    /** 한 멤버의 몫 변화. */
    public record ShareChange(long userId, int before, int after) {
    }

    /**
     * PREMIUM 구독기간이 끝나 FREE 로 내려감. 밴드장에게.
     *
     * <p>{@code graceDays} 뒤 첨부 사진·영상이 사라지므로 이 알림이 사실상 마지막 경고다.
     */
    public record PlanExpired(long bandId, int graceDays) {
    }
}
