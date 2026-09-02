package com.yeka.bandapp.board.service;

import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 다른 도메인(요금제)이 밴드의 첨부 미디어 보관기한을 재계산할 때 쓰는 창구. 도메인 간 참조는 저장소가
 * 아니라 이 서비스를 통한다(코딩 컨벤션) — 요금제 서비스는 {@code MediaAttachmentRepository} 를 직접
 * 만지지 않는다. {@link com.yeka.bandapp.room.service.RoomDirectoryService} 와 같은 역할이다.
 *
 * <p>메서드에 트랜잭션을 걸지 않는다 — 저장소의 {@code @Modifying} 쿼리가 호출자(요금제 변경)의
 * 트랜잭션에 {@code REQUIRED} 로 합류해, 티어 플립과 보관기한 재계산이 한 트랜잭션으로 커밋된다.
 */
@Service
public class MediaDirectoryService {

    private final MediaAttachmentRepository mediaRepository;

    public MediaDirectoryService(MediaAttachmentRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    /** 업그레이드(→PREMIUM): 밴드의 READY 미디어를 무제한 보관으로. 바뀐 행 수를 돌려준다. */
    public int extendRetentionForBand(long bandId) {
        return mediaRepository.clearExpiryForBandReadyMedia(bandId);
    }

    /** 다운그레이드(→FREE): 밴드의 READY 미디어 만료 시각을 유예 종료로 덮어쓴다. 바뀐 행 수를 돌려준다. */
    public int applyGracePeriodForBand(long bandId, Instant graceUntil) {
        return mediaRepository.setExpiryForBandReadyMedia(bandId, graceUntil);
    }
}
