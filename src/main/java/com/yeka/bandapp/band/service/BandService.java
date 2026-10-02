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
import com.yeka.bandapp.plan.service.PlanProvisioningService;
import com.yeka.bandapp.user.service.UserDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 밴드 생성·조회·설정 변경. 멤버십 조작은 {@link BandMemberService}, 초대는 {@link BandInviteService}.
 */
@Service
public class BandService {

    /** 보이지 않는 문자 — 공백·구분자(Z)·제어·서식(폭 0 포함, C)·한글 채움 문자·점자 빈칸. */
    private static final Pattern INVISIBLE = Pattern.compile("[\\s\\p{Z}\\p{C}\\u115F\\u1160\\u2800\\u3164\\uFFA0]");

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final BandAccessGuard accessGuard;
    private final PlanProvisioningService planProvisioningService;
    private final UserDirectoryService userDirectory;

    public BandService(BandRepository bandRepository, BandMemberRepository bandMemberRepository,
                       BandAccessGuard accessGuard, PlanProvisioningService planProvisioningService,
                       UserDirectoryService userDirectory) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.accessGuard = accessGuard;
        this.planProvisioningService = planProvisioningService;
        this.userDirectory = userDirectory;
    }

    /** 밴드 생성. 생성자가 곧바로 활성 LEADER 멤버가 된다. */
    @Transactional
    public BandResponse create(long userId, CreateBandRequest request) {
        userDirectory.lockActiveUser(userId);
        Instant now = Instant.now();
        Band band = bandRepository.save(Band.create(requireVisibleName(request.name()), userId));
        bandMemberRepository.save(BandMember.asLeader(band.getId(), userId, now));
        planProvisioningService.createDefaultPlan(band.getId(), now);
        return BandResponse.from(band);
    }

    /**
     * 공백·줄바꿈·제어문자·폭 0 문자·한글 채움 문자만으로 된 이름은 거부한다 — 목록·알림에서 빈칸으로 보인다(결정 #11).
     */
    static String requireVisibleName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (INVISIBLE.matcher(name).replaceAll("").isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "밴드 이름을 입력해 주세요");
        }
        return name;
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
        Band band = accessGuard.lockBand(bandId);
        accessGuard.requireLeader(bandId, userId);
        band.changeReservationPermission(request.reservationPermission());
        return BandResponse.from(band);
    }

    private Band band(long bandId) {
        return bandRepository.findById(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND));
    }
}
