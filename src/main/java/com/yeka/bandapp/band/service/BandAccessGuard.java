package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 밴드 소속·역할 검증을 한곳에 모은다. 모든 밴드 API 는 여기를 거쳐 타 밴드 데이터 접근을 차단한다.
 *
 * <p>밴드가 없든 요청자가 멤버가 아니든 똑같이 {@code NOT_BAND_MEMBER}(403)로 응답한다 —
 * 존재 여부를 비멤버에게 알리지 않기 위해서다.
 */
@Component
public class BandAccessGuard {

    private final BandMemberRepository bandMemberRepository;

    public BandAccessGuard(BandMemberRepository bandMemberRepository) {
        this.bandMemberRepository = bandMemberRepository;
    }

    public BandMember requireActiveMember(long bandId, long userId) {
        return bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_BAND_MEMBER));
    }

    public BandMember requireLeader(long bandId, long userId) {
        BandMember member = requireActiveMember(bandId, userId);
        if (!member.isLeader()) {
            throw new BusinessException(ErrorCode.NOT_BAND_LEADER);
        }
        return member;
    }
}
