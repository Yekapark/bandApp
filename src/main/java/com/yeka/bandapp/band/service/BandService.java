package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.dto.BandResponse;
import com.yeka.bandapp.band.dto.CreateBandRequest;
import com.yeka.bandapp.band.dto.MyBandListResponse;
import com.yeka.bandapp.band.dto.MyBandListResponse.MyBandResponse;
import com.yeka.bandapp.band.dto.UpdateBandSettingsRequest;
import com.yeka.bandapp.band.entity.Band;
import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 밴드 생성·조회·설정 변경. 멤버십 조작은 {@link BandMemberService}, 초대는 {@link BandInviteService}.
 */
@Service
public class BandService {

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final BandAccessGuard accessGuard;

    public BandService(BandRepository bandRepository, BandMemberRepository bandMemberRepository,
                       BandAccessGuard accessGuard) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.accessGuard = accessGuard;
    }

    /** 밴드 생성. 생성자가 곧바로 활성 LEADER 멤버가 된다. */
    @Transactional
    public BandResponse create(long userId, CreateBandRequest request) {
        Instant now = Instant.now();
        Band band = bandRepository.save(Band.create(request.name().trim(), userId));
        bandMemberRepository.save(BandMember.asLeader(band.getId(), userId, now));
        return BandResponse.from(band);
    }

    @Transactional(readOnly = true)
    public BandResponse get(long bandId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        return BandResponse.from(band(bandId));
    }

    /** 내가 활성 멤버로 속한 밴드 목록. 가입순. 탈퇴한 밴드는 빠진다. */
    @Transactional(readOnly = true)
    public MyBandListResponse listMine(long userId) {
        List<BandMember> memberships =
                bandMemberRepository.findByUserIdAndLeftAtIsNullOrderByJoinedAtAsc(userId);
        List<Long> bandIds = memberships.stream().map(BandMember::getBandId).toList();
        Map<Long, Band> bands = bandRepository.findAllById(bandIds).stream()
                .collect(Collectors.toMap(Band::getId, Function.identity()));

        List<MyBandResponse> rows = memberships.stream()
                .map(m -> MyBandResponse.of(
                        bands.get(m.getBandId()),
                        m,
                        bandMemberRepository.countByBandIdAndLeftAtIsNull(m.getBandId())))
                .toList();
        return new MyBandListResponse(rows.size(), rows);
    }

    /** 일정 등록 권한 모드 변경. 밴드장만 가능(그 외 403). */
    @Transactional
    public BandResponse updateSettings(long bandId, long userId, UpdateBandSettingsRequest request) {
        accessGuard.requireLeader(bandId, userId);
        Band band = band(bandId);
        band.changeReservationPermission(request.reservationPermission());
        return BandResponse.from(band);
    }

    private Band band(long bandId) {
        return bandRepository.findById(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND));
    }
}
