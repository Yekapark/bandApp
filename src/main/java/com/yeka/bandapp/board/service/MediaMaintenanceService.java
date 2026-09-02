package com.yeka.bandapp.board.service;

import com.yeka.bandapp.board.entity.MediaAttachment;
import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
import com.yeka.bandapp.board.storage.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 미디어 정리 배치 로직 — 보관기한 만료 미디어 삭제(배치 1)와 콜백 없는 고아 PENDING 정리(배치 2).
 * 스케줄러({@code MediaExpirationJob}/{@code OrphanMediaCleanupJob})가 이 서비스를 호출한다.
 *
 * <p><b>{@code @Transactional} 없음</b> — R2 HTTP(delete)가 트랜잭션 안에서 커넥션을 붙잡지 않도록
 * ({@code MediaAttachmentService}와 같은 이유). DB 쓰기는 저장소의 조건부 UPDATE/DELETE(각자 짧은
 * 트랜잭션)로만 한다. 잡을 감싸는 트랜잭션이 없고 건별로 처리하므로, 한 건의 실패가 나머지를 롤백하지 않는다.
 *
 * <p><b>완료 기준(BUILD_PLAN Phase 9)</b> — R2 삭제 실패 시에도 트랜잭션이 깨지지 않고 재시도 가능해야 한다.
 * 만료 배치는 <b>R2 삭제 → 그다음 DB EXPIRED</b> 순서다. 삭제가 실패하면 행이 READY 로 남아 <b>다음 실행이
 * 자동 재시도</b>한다. 반대 순서(먼저 EXPIRED)라면 R2 객체가 영원히 고아가 된다.
 */
@Service
public class MediaMaintenanceService {

    /** 한 페이지 크기. */
    public static final int PAGE_SIZE = 200;
    /** 무한 루프 방지 상한(정상적으로는 진행이 멈추면 먼저 빠져나온다). */
    private static final int MAX_BATCHES = 100;

    private static final Logger log = LoggerFactory.getLogger(MediaMaintenanceService.class);

    private final MediaAttachmentRepository mediaRepository;
    private final StorageClient storage;

    public MediaMaintenanceService(MediaAttachmentRepository mediaRepository, StorageClient storage) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
    }

    /**
     * 배치 1 — 보관기한이 지난 READY 미디어를 R2 에서 지우고 EXPIRED 로 돌린다.
     *
     * @return EXPIRED 로 전이한 건수. (R2 삭제에 실패한 건은 READY 로 남으며 다음 실행이 재시도한다.)
     */
    public int expireOverdue(Instant now) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<MediaAttachment> page = mediaRepository.findExpiredReady(now, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }
            int done = 0;
            for (MediaAttachment media : page) {
                try {
                    storage.delete(media.getStorageKey());          // 멱등 — 객체가 없어도 성공
                    if (mediaRepository.markExpired(media.getId()) > 0) {
                        done++;
                    }
                } catch (RuntimeException e) {
                    log.warn("만료 미디어 R2 삭제 실패 id={} key={} — 다음 실행에서 재시도한다",
                            media.getId(), media.getStorageKey(), e);
                }
            }
            total += done;
            if (done == 0 || page.size() < PAGE_SIZE) {
                break;   // 진행이 없거나(전부 삭제 실패) 마지막 페이지
            }
        }
        if (total > 0) {
            log.info("보관기한 만료 미디어 정리 expired={}", total);
        }
        return total;
    }

    /**
     * 배치 2 — 콜백이 오지 않아 {@code threshold}보다 오래 PENDING 인 고아 첨부를 지운다.
     * R2 객체는 best-effort 로 지우고(대개 애초에 업로드가 안 됐다), DB 행은 삭제한다.
     *
     * @return 지운 PENDING 행 수
     */
    public int cleanupOrphans(Instant threshold) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<MediaAttachment> page = mediaRepository.findStalePending(threshold, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }
            int done = 0;
            for (MediaAttachment media : page) {
                try {
                    storage.delete(media.getStorageKey());
                } catch (RuntimeException e) {
                    log.warn("고아 PENDING R2 삭제 실패 id={} key={}", media.getId(), media.getStorageKey(), e);
                }
                if (mediaRepository.deletePending(media.getId()) > 0) {
                    done++;
                }
            }
            total += done;
            if (done == 0 || page.size() < PAGE_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("고아 PENDING 미디어 정리 removed={}", total);
        }
        return total;
    }
}
