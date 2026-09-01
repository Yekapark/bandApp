package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.entity.Band;
import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.entity.ReservationPermission;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.user.service.UserDirectoryService;
import com.yeka.bandapp.user.service.UserDirectoryService.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 다른 도메인(일정 등)이 밴드 설정·멤버를 읽을 때 쓰는 창구. 도메인 간 참조는 저장소가 아니라 이 서비스를
 * 통한다(코딩 컨벤션). {@link com.yeka.bandapp.user.service.UserDirectoryService}와 같은 역할이다.
 *
 * <p>멤버십·역할 검증은 {@link BandAccessGuard}가 담당한다 — 여기서는 다루지 않는다.
 */
@Service
public class BandDirectoryService {

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final UserDirectoryService userDirectory;

    public BandDirectoryService(BandRepository bandRepository, BandMemberRepository bandMemberRepository,
                                UserDirectoryService userDirectory) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.userDirectory = userDirectory;
    }

    /** 일정 등록 권한 모드. 등록 직후 status(CONFIRMED/PENDING)와 재승인 여부가 이 값으로 갈린다. */
    @Transactional(readOnly = true)
    public ReservationPermission reservationPermissionOf(long bandId) {
        return bandRepository.findById(bandId)
                .map(Band::getReservationPermission)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND));
    }

    /**
     * 밴드의 현재 활성 멤버 요약(가입 순). 일정 생성 시 참석 행을 만들거나, 일정 상세의 참석 현황·집계
     * ("참석 N / 전체 M")를 만드는 데 쓴다 — 그래서 <b>지금</b>의 멤버 기준이다(그 사이 합류/탈퇴 반영).
     */
    @Transactional(readOnly = true)
    public List<MemberBrief> activeMembers(long bandId) {
        List<BandMember> members = bandMemberRepository.findByBandIdAndLeftAtIsNullOrderByJoinedAtAsc(bandId);
        Map<Long, UserSummary> byId = userDirectory
                .summariesOf(members.stream().map(BandMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(UserSummary::userId, Function.identity()));
        return members.stream()
                .map(m -> {
                    UserSummary summary = byId.get(m.getUserId());
                    String name = summary != null ? summary.name() : "탈퇴한 사용자";
                    return new MemberBrief(m.getUserId(), name, m.getRole().name());
                })
                .toList();
    }

    /** 활성 멤버 한 명의 표시용 요약. */
    public record MemberBrief(long userId, String name, String role) {
    }
}
