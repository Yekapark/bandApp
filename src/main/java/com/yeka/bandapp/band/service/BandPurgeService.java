package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.repository.BandInviteRepository;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.board.repository.BoardPostRepository;
import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
import com.yeka.bandapp.board.repository.ReportRepository;
import com.yeka.bandapp.notification.repository.NotificationDispatchRepository;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import com.yeka.bandapp.plan.repository.PlanCouponRedemptionRepository;
import com.yeka.bandapp.recurring.repository.RecurringRuleRepository;
import com.yeka.bandapp.reservation.repository.ReservationAttendanceRepository;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import com.yeka.bandapp.reservation.repository.SetlistItemRepository;
import com.yeka.bandapp.room.repository.RoomRepository;
import com.yeka.bandapp.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 밴드에 딸린 DB 행을 전부 지우는 쓰기 단위. 한 트랜잭션이라 하나라도 실패하면 전부 롤백된다 —
 * 반쯤 지워진 밴드를 남기지 않는다.
 *
 * <p>R2 객체 삭제는 여기가 아니라 {@link BandDeletionService} 가 <b>트랜잭션 밖에서 먼저</b> 끝낸다
 * ({@code PlanService} ↔ {@code PlanMutationService} 와 같은 분리). 그래서 이 메서드 안에는
 * 외부 I/O 가 없고 한 트랜잭션으로 묶는 것이 안전하다.
 *
 * <p><b>별도 빈인 이유</b>: 같은 클래스 안에서 호출하면 스프링 프록시를 거치지 않아
 * {@code @Transactional} 이 걸리지 않는다.
 *
 * <h2>삭제 순서 (FK 역순)</h2>
 * 놓치기 쉬운 세 곳:
 * <ul>
 *   <li>{@code settlement_shares} — 스키마에서 유일하게 {@code ON DELETE CASCADE} 가 걸려 있어
 *       {@code settlements} 만 지우면 따라 지워진다(V7).</li>
 *   <li>{@code reports} — {@code target_type}+{@code target_id} 다형 참조라 FK 가 없다.
 *       대상(게시글·첨부)을 지우기 <b>전에</b> 지워야 거슬러 올라갈 수 있다.</li>
 *   <li>{@code notification_dispatches} — {@code band_id} 에 FK 가 없다(V11 이 순수 BIGINT 로 추가).
 *       남기면 없어진 밴드의 알림이 사용자 피드에 계속 뜬다.</li>
 * </ul>
 *
 * <p>지우지 않는 것: {@code user_blocks}(사람↔사람 전역 차단이라 밴드와 무관),
 * {@code device_tokens}·{@code notification_settings}(사용자 단위), {@code users}.
 */
@Service
public class BandPurgeService {

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final BandInviteRepository bandInviteRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationAttendanceRepository attendanceRepository;
    private final SetlistItemRepository setlistItemRepository;
    private final SettlementRepository settlementRepository;
    private final RecurringRuleRepository recurringRuleRepository;
    private final BoardPostRepository boardPostRepository;
    private final MediaAttachmentRepository mediaRepository;
    private final ReportRepository reportRepository;
    private final NotificationDispatchRepository dispatchRepository;
    private final BandPlanRepository bandPlanRepository;
    private final PlanCouponRedemptionRepository couponRedemptionRepository;

    public BandPurgeService(BandRepository bandRepository,
                            BandMemberRepository bandMemberRepository,
                            BandInviteRepository bandInviteRepository,
                            RoomRepository roomRepository,
                            ReservationRepository reservationRepository,
                            ReservationAttendanceRepository attendanceRepository,
                            SetlistItemRepository setlistItemRepository,
                            SettlementRepository settlementRepository,
                            RecurringRuleRepository recurringRuleRepository,
                            BoardPostRepository boardPostRepository,
                            MediaAttachmentRepository mediaRepository,
                            ReportRepository reportRepository,
                            NotificationDispatchRepository dispatchRepository,
                            BandPlanRepository bandPlanRepository,
                            PlanCouponRedemptionRepository couponRedemptionRepository) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.bandInviteRepository = bandInviteRepository;
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.attendanceRepository = attendanceRepository;
        this.setlistItemRepository = setlistItemRepository;
        this.settlementRepository = settlementRepository;
        this.recurringRuleRepository = recurringRuleRepository;
        this.boardPostRepository = boardPostRepository;
        this.mediaRepository = mediaRepository;
        this.reportRepository = reportRepository;
        this.dispatchRepository = dispatchRepository;
        this.bandPlanRepository = bandPlanRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
    }

    @Transactional
    public void purge(long bandId) {
        settlementRepository.deleteByBandId(bandId);   // settlement_shares 는 cascade 로 따라온다
        attendanceRepository.deleteByBandId(bandId);
        setlistItemRepository.deleteByBandId(bandId);

        reportRepository.deleteByBandId(bandId);       // 대상(게시글·첨부)보다 먼저
        mediaRepository.deleteByBandId(bandId);
        boardPostRepository.deleteByBandId(bandId);

        reservationRepository.deleteByBandId(bandId);  // recurring_rules·rooms 를 참조하므로 먼저
        recurringRuleRepository.deleteByBandId(bandId);
        roomRepository.deleteByBandId(bandId);

        bandInviteRepository.deleteByBandId(bandId);
        couponRedemptionRepository.deleteByBandId(bandId);
        bandPlanRepository.deleteByBandId(bandId);
        bandMemberRepository.deleteByBandId(bandId);
        dispatchRepository.deleteByBandId(bandId);     // FK 가 없어 FK 를 훑으면 빠진다

        bandRepository.deleteById(bandId);
    }
}
