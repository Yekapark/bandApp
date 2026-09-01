package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.dto.BandMemberListResponse;
import com.yeka.bandapp.band.dto.BandMemberResponse;
import com.yeka.bandapp.band.dto.BandResponse;
import com.yeka.bandapp.band.entity.Band;
import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.user.service.UserDirectoryService;
import com.yeka.bandapp.user.service.UserDirectoryService.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 멤버 목록·자발적 탈퇴·추방·밴드장 위임·계정 탈퇴 정리.
 */
@Service
public class BandMemberService {

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final BandAccessGuard accessGuard;
    private final UserDirectoryService userDirectory;

    public BandMemberService(BandRepository bandRepository, BandMemberRepository bandMemberRepository,
                             BandAccessGuard accessGuard, UserDirectoryService userDirectory) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.accessGuard = accessGuard;
        this.userDirectory = userDirectory;
    }

    @Transactional(readOnly = true)
    public BandMemberListResponse list(long bandId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        List<BandMember> members = bandMemberRepository.findByBandIdAndLeftAtIsNullOrderByJoinedAtAsc(bandId);
        Map<Long, UserSummary> byId = userDirectory
                .summariesOf(members.stream().map(BandMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(UserSummary::userId, Function.identity()));
        List<BandMemberResponse> rows = members.stream()
                .map(m -> BandMemberResponse.of(m, byId.get(m.getUserId())))
                .toList();
        return new BandMemberListResponse(bandId, rows.size(), rows);
    }

    /** 자발적 탈퇴. 밴드장은 위임 전에는 나갈 수 없다(밴드에 리더가 사라지는 것을 막는다). */
    @Transactional
    public void leave(long bandId, long userId) {
        BandMember me = accessGuard.requireActiveMember(bandId, userId);
        if (me.isLeader()) {
            throw new BusinessException(ErrorCode.LEADER_MUST_DELEGATE_BEFORE_LEAVING);
        }
        me.leave(Instant.now());
    }

    /** 밴드장의 멤버 추방. 자기 자신은 추방 대상이 될 수 없다. */
    @Transactional
    public void kick(long bandId, long leaderUserId, long targetUserId) {
        accessGuard.requireLeader(bandId, leaderUserId);
        if (leaderUserId == targetUserId) {
            throw new BusinessException(ErrorCode.CANNOT_KICK_SELF);
        }
        BandMember target = bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        target.leave(Instant.now());
    }

    /**
     * 밴드장 위임. 기존 LEADER 는 MEMBER 로 강등되고 대상이 LEADER 로 승격된다 — 한 트랜잭션.
     *
     * <p>강등을 먼저 flush 한 뒤 승격한다. 부분 유니크 인덱스 {@code ux_band_members_single_leader}가
     * "활성 LEADER 정확히 하나"를 강제하므로, 순서를 바꾸면 순간적으로 둘이 되어 제약에 걸린다.
     */
    @Transactional
    public BandResponse delegateLeadership(long bandId, long currentLeaderUserId, long newLeaderUserId) {
        BandMember current = accessGuard.requireLeader(bandId, currentLeaderUserId);
        if (currentLeaderUserId == newLeaderUserId) {
            throw new BusinessException(ErrorCode.CANNOT_DELEGATE_TO_SELF);
        }
        BandMember next = bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandId, newLeaderUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        current.demoteToMember();
        bandMemberRepository.saveAndFlush(current);
        next.promoteToLeader();
        bandMemberRepository.saveAndFlush(next);

        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND));
        band.handOverLeadership(newLeaderUserId);
        return BandResponse.from(band);
    }

    /**
     * 계정 탈퇴 정리. 탈퇴자의 활성 멤버십을 전부 종료한다.
     *
     * <p>탈퇴자가 밴드장인 밴드는 <b>가장 먼저 가입한 다른 활성 멤버</b>를 밴드장으로 자동 승격한다.
     * 다른 멤버가 없으면 그 밴드는 활성 멤버 0인 상태로 남는다({@code bands} 행은 유지 —
     * 활성 멤버가 없어 어떤 API 로도 접근되지 않으므로 사실상 소멸이다. 빈 밴드 정리는 이번 범위 밖).
     *
     * <p>밴드장 밴드에서는 {@code delegateLeadership} 과 같은 이유로 순서가 중요하다:
     * 탈퇴자를 먼저 {@code leave} + flush 해 {@code ux_band_members_single_leader} 슬롯을 비운 뒤 승격한다.
     *
     * <p>{@link com.yeka.bandapp.user.service.UserAccountService#withdraw} 의 트랜잭션 안에서 호출된다 —
     * 여기서 실패하면 탈퇴 전체가 롤백된다.
     */
    @Transactional
    public void handleAccountWithdrawal(long userId, Instant when) {
        for (BandMember me : bandMemberRepository.findByUserIdAndLeftAtIsNullOrderByJoinedAtAsc(userId)) {
            if (!me.isLeader()) {
                me.leave(when);
                continue;
            }
            long bandId = me.getBandId();
            BandMember successor = bandMemberRepository
                    .findByBandIdAndLeftAtIsNullOrderByJoinedAtAsc(bandId).stream()
                    .filter(bm -> !bm.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);

            me.leave(when);
            bandMemberRepository.saveAndFlush(me);
            if (successor != null) {
                successor.promoteToLeader();
                bandMemberRepository.saveAndFlush(successor);
                long newLeaderId = successor.getUserId();
                bandRepository.findById(bandId).ifPresent(band -> band.handOverLeadership(newLeaderId));
            }
        }
    }
}
