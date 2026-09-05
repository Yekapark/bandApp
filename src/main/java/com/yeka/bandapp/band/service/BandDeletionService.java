package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.entity.Band;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.board.service.StorageKeys;
import com.yeka.bandapp.board.storage.StorageClient;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private static final Logger log = LoggerFactory.getLogger(BandDeletionService.class);

    private final BandAccessGuard accessGuard;
    private final BandRepository bandRepository;
    private final BandPurgeService bandPurgeService;
    private final StorageClient storage;

    public BandDeletionService(BandAccessGuard accessGuard, BandRepository bandRepository,
                               BandPurgeService bandPurgeService, StorageClient storage) {
        this.accessGuard = accessGuard;
        this.bandRepository = bandRepository;
        this.bandPurgeService = bandPurgeService;
        this.storage = storage;
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
        if (confirmName == null || !band.getName().equals(confirmName.trim())) {
            throw new BusinessException(ErrorCode.BAND_NAME_MISMATCH);
        }

        // R2 먼저. 실패하면 여기서 502 로 끝나고 DB 는 그대로다 — 다시 시도할 수 있다.
        int objects = storage.deleteByPrefix(StorageKeys.bandPrefix(bandId));

        bandPurgeService.purge(bandId);
        log.info("밴드 삭제 완료 bandId={} r2Objects={}", bandId, objects);
    }
}
