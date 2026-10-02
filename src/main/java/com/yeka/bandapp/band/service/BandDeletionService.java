package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.entity.Band;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.board.service.StorageKeys;
import com.yeka.bandapp.board.storage.StorageClient;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 밴드 삭제 — 밴드와 그 안의 모든 데이터를 되돌릴 수 없게 지운다. 밴드장만.
 *
 * <p><b>{@code @Transactional} 없음</b> — R2 삭제(외부 HTTP)가 트랜잭션 안에서 커넥션을 붙잡지
 * 않도록(CLAUDE.md 규칙). DB 삭제는 {@link BandPurgeService} 의 짧은 트랜잭션에 맡긴다
 * ({@code PlanService} ↔ {@code PlanMutationService} 와 같은 분리).
 *
 * <p><b>R2 를 먼저, DB 를 나중에</b> 지운다. 반대로 하면 객체 키를 잃어버려 R2 에 영구 고아가
 * 남는다({@code MediaMaintenanceService} 가 같은 이유로 같은 순서를 쓴다). R2 삭제가 실패하면
 * 502 로 중단하고 DB 는 건드리지 않는다 — 접두사 삭제는 멱등이라 다시 누르면 된다.
 *
 * <p>R2 는 키를 하나씩이 아니라 {@code bands/{bandId}/} 접두사로 한 번에 지운다. DB 를 훑는 것보다
 * 정확하다 — {@code media_attachments} 가 이미 추적을 놓친 객체까지 함께 사라진다.
 */
@Service
public class BandDeletionService {

    /** prod-check 가 grep 하는 표시. 바꾸면 deploy/prod-check.sh 도 같이 바꾼다. */
    public static final String PURGE_FAILURE_MARKER = "MEMBERLESS_BAND_PURGE_FAILED";

    private static final Logger log = LoggerFactory.getLogger(BandDeletionService.class);

    private final BandAccessGuard accessGuard;
    private final BandRepository bandRepository;
    private final BandPurgeService bandPurgeService;
    private final StorageClient storage;
    private final BandPlanRepository bandPlanRepository;
    private final BandMemberRepository bandMemberRepository;
    private final StoreBillingGateway billingGateway;

    public BandDeletionService(BandAccessGuard accessGuard, BandRepository bandRepository,
                               BandPurgeService bandPurgeService, StorageClient storage,
                               BandPlanRepository bandPlanRepository, BandMemberRepository bandMemberRepository,
                               StoreBillingGateway billingGateway) {
        this.accessGuard = accessGuard;
        this.bandRepository = bandRepository;
        this.bandPurgeService = bandPurgeService;
        this.storage = storage;
        this.bandPlanRepository = bandPlanRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.billingGateway = billingGateway;
    }

    /**
     * 활성 멤버가 한 명도 없는 밴드를 지운다 — 혼자 남은 밴드장이 회원 탈퇴한 경우(LAUNCH_REVIEW L7). 아무도 볼 수 없는
     * 글·사진·일정이 영구 보관되면 개인정보처리방침("회원 탈퇴 또는 밴드 삭제 시까지")과 어긋난다.
     *
     * <p>{@link #delete} 와 같은 순서(트랜잭션 밖에서 R2 먼저 → DB)지만 B5(자동 갱신 구독이면 409)로 막지 않는다. 쓸 사람이
     * 없으니 기다릴 이유가 없고, 막으면 영원히 남는다. 대신 <b>지우기 전에 스토어 구독을 직접 해지</b>한다 — 요금제 행을 지우면
     * 구매 토큰을 잃어 아무도 해지할 수 없게 되므로, 해지가 확인돼야만(예외 없이 돌아와야만) 지운다. 해지는 멱등이라
     * 이미 해지·만료된 구독이어도 안전하다(게이트웨이 계약, B14).
     *
     * <p>어느 단계든 실패하면 예외가 그대로 나가고 DB 는 그대로다 — {@link #purgeMemberlessBands} 가 매시 다시 한다.
     *
     * @return 지웠으면 true, 그새 멤버가 있거나 이미 없는 밴드면 false
     */
    public boolean purgeIfMemberless(long bandId) {
        if (bandMemberRepository.countByBandIdAndLeftAtIsNull(bandId) > 0) {
            return false;
        }
        bandPlanRepository.findByBandId(bandId)
                .filter(p -> p.getStore() != null && p.getPurchaseToken() != null)
                .ifPresent(p -> billingGateway.cancelRenewal(p.getStore(), p.getPurchaseToken()));
        int objects = storage.deleteByPrefix(StorageKeys.bandPrefix(bandId));
        boolean purged = bandPurgeService.purgeIfMemberless(bandId);
        if (purged) {
            log.info("빈 밴드 삭제 완료 bandId={} r2Objects={}", bandId, objects);
        }
        return purged;
    }

    /**
     * 멤버 0명 밴드를 찾아 지운다. 탈퇴 직후 삭제({@link BandMemberService#handleAccountWithdrawal})가 실패한 것(R2 장애 등)과
     * 이 기능 전에 이미 생긴 빈 밴드를 함께 정리한다. 정상이면 대상이 없어 쿼리 한 번으로 끝난다.
     * 실패할 때마다 {@value #PURGE_FAILURE_MARKER} 를 ERROR 로 남긴다(deploy/prod-check.sh 가 센다).
     * ponytail: 단일 서버라 동시 실행 잠금이 없다 — 탈퇴 직후 삭제와 겹쳐도 R2 접두사 삭제·DB 삭제 모두 멱등이다.
     */
    @Scheduled(cron = "${app.band.memberless-purge-cron:0 40 * * * *}")
    public void purgeMemberlessBands() {
        List<Long> bandIds = bandRepository.findMemberlessBandIds();
        if (bandIds.isEmpty()) {
            return;
        }
        int purged = 0;
        for (long bandId : bandIds) {
            try {
                if (purgeIfMemberless(bandId)) {
                    purged++;
                }
            } catch (RuntimeException e) {
                log.error("{} bandId={} cause={} — 매시 다시 시도한다", PURGE_FAILURE_MARKER, bandId, e.toString());
            }
        }
        log.info("빈 밴드 정리 대상={} 삭제={} 남음={}", bandIds.size(), purged, bandIds.size() - purged);
    }

    /**
     * 밴드를 삭제한다. 되돌릴 수 없다.
     *
     * @param confirmName 사용자가 입력한 밴드 이름. 실제 이름과 다르면 400 —
     *                    파괴적이고 되돌릴 수 없는 동작이라 오입력을 여기서 끊는다
     *                    (계정 탈퇴가 비밀번호를 요구하는 것과 같은 계열의 방어).
     */
    public void delete(long bandId, long userId, String confirmName) {
        accessGuard.requireLeader(bandId, userId);
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND));
        if (confirmName == null || !band.getName().strip().equals(confirmName.strip())) {
            throw new BusinessException(ErrorCode.BAND_NAME_MISMATCH);
        }
        // 자동 갱신 중인 Play 구독이 있으면 막는다. 지워도 Google 은 매년 계속 청구하는데, 갱신 알림이
        // 찾을 밴드가 없어 버려진다 — 사용자는 없는 밴드에 돈을 낸다(LAUNCH_REVIEW B5). 해지 예약한 뒤에는
        // 남은 기간을 포기하고 지울 수 있다. 앱은 삭제 창에서 먼저 안내하고, 이건 마지막 방어선이다.
        if (bandPlanRepository.findByBandId(bandId)
                .map(BandPlan::isAutoRenewingStoreSubscription).orElse(false)) {
            throw new BusinessException(ErrorCode.BAND_HAS_ACTIVE_SUBSCRIPTION);
        }

        // R2 먼저. 실패하면 여기서 502 로 끝나고 DB 는 그대로다 — 다시 시도할 수 있다.
        int objects = storage.deleteByPrefix(StorageKeys.bandPrefix(bandId));

        bandPurgeService.purge(bandId);
        log.info("밴드 삭제 완료 bandId={} r2Objects={}", bandId, objects);
    }
}
