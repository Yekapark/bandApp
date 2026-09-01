package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.entity.Band;
import com.yeka.bandapp.band.entity.ReservationPermission;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인(일정 등)이 밴드 설정을 읽을 때 쓰는 창구. 도메인 간 참조는 저장소가 아니라 이 서비스를
 * 통한다(코딩 컨벤션). {@link com.yeka.bandapp.user.service.UserDirectoryService}와 같은 역할이다.
 *
 * <p>멤버십·역할 검증은 {@link BandAccessGuard}가 담당한다 — 여기서는 다루지 않는다.
 */
@Service
public class BandDirectoryService {

    private final BandRepository bandRepository;

    public BandDirectoryService(BandRepository bandRepository) {
        this.bandRepository = bandRepository;
    }

    /** 일정 등록 권한 모드. 등록 직후 status(CONFIRMED/PENDING)와 재승인 여부가 이 값으로 갈린다. */
    @Transactional(readOnly = true)
    public ReservationPermission reservationPermissionOf(long bandId) {
        return bandRepository.findById(bandId)
                .map(Band::getReservationPermission)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND));
    }
}
