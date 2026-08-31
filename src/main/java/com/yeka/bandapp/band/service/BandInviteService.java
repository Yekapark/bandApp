package com.yeka.bandapp.band.service;

import com.yeka.bandapp.band.DeeplinkProperties;
import com.yeka.bandapp.band.dto.BandResponse;
import com.yeka.bandapp.band.dto.InviteResponse;
import com.yeka.bandapp.band.dto.IssueInviteRequest;
import com.yeka.bandapp.band.entity.BandInvite;
import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.repository.BandInviteRepository;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * 초대코드 발급/재발급/무효화/현재 코드 조회, 그리고 코드로 밴드 참여.
 *
 * <p>재발급은 기존 활성 코드를 모두 revoked 처리한 뒤 새 코드를 만든다(밴드당 활성 코드 0~1개).
 * 참여 시도에는 계정·IP 기준 분당 레이트리밋을 건다.
 */
@Service
public class BandInviteService {

    private static final int DEFAULT_TTL_DAYS = 7;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final BandInviteRepository bandInviteRepository;
    private final BandAccessGuard accessGuard;
    private final InviteCodeGenerator codeGenerator;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final DeeplinkProperties deeplinkProperties;

    public BandInviteService(BandRepository bandRepository, BandMemberRepository bandMemberRepository,
                             BandInviteRepository bandInviteRepository, BandAccessGuard accessGuard,
                             InviteCodeGenerator codeGenerator, RedisRateLimiter rateLimiter,
                             RateLimitProperties rateLimitProperties, DeeplinkProperties deeplinkProperties) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.bandInviteRepository = bandInviteRepository;
        this.accessGuard = accessGuard;
        this.codeGenerator = codeGenerator;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.deeplinkProperties = deeplinkProperties;
    }

    /** 초대코드 발급/재발급. 밴드장만 가능. 기존 활성 코드는 revoked 된다. */
    @Transactional
    public InviteResponse issue(long bandId, long userId, IssueInviteRequest request) {
        accessGuard.requireLeader(bandId, userId);
        bandInviteRepository.revokeActiveByBandId(bandId);

        Instant now = Instant.now();
        Integer ttlDays = request == null ? null : request.ttlDays();
        Duration ttl = Duration.ofDays(ttlDays == null ? DEFAULT_TTL_DAYS : ttlDays);
        Integer maxUses = request == null ? null : request.maxUses();

        BandInvite invite = saveWithUniqueCode(bandId, userId, now, ttl, maxUses);
        return InviteResponse.of(invite, deeplinkProperties.inviteLink(invite.getCode()));
    }

    /** 현재 활성 초대코드. 밴드장만 조회 가능. 없으면 404. */
    @Transactional(readOnly = true)
    public InviteResponse current(long bandId, long userId) {
        accessGuard.requireLeader(bandId, userId);
        BandInvite invite = bandInviteRepository.findFirstByBandIdAndRevokedFalseOrderByCreatedAtDesc(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_NOT_FOUND));
        return InviteResponse.of(invite, deeplinkProperties.inviteLink(invite.getCode()));
    }

    /** 현재 활성 초대코드 무효화. 밴드장만 가능. 이미 없으면 조용히 통과(멱등). */
    @Transactional
    public void revokeCurrent(long bandId, long userId) {
        accessGuard.requireLeader(bandId, userId);
        bandInviteRepository.revokeActiveByBandId(bandId);
    }

    /**
     * 코드로 밴드 참여. 만료·무효화·소진을 각각 다른 사유로 거부한다.
     * 사용 횟수 증가는 저장소의 조건부 UPDATE 로 원자적으로 처리해 동시 참여에서 {@code maxUses}를 지킨다.
     */
    @Transactional
    public BandResponse join(long userId, String rawCode, String clientIp) {
        rateLimiter.check("invite-join:user", Long.toString(userId),
                rateLimitProperties.inviteJoinPerUserPerMin());
        rateLimiter.check("invite-join:ip", clientIp,
                rateLimitProperties.inviteJoinPerIpPerMin());

        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        BandInvite invite = bandInviteRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_NOT_FOUND));

        Instant now = Instant.now();
        if (invite.isRevoked()) {
            throw new BusinessException(ErrorCode.INVITE_REVOKED);
        }
        if (invite.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVITE_EXPIRED);
        }
        if (invite.isExhausted()) {
            throw new BusinessException(ErrorCode.INVITE_EXHAUSTED);
        }

        long bandId = invite.getBandId();
        if (bandMemberRepository.existsByBandIdAndUserIdAndLeftAtIsNull(bandId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_BAND_MEMBER);
        }
        if (bandInviteRepository.tryConsume(invite.getId(), now) == 0) {
            // 사전 판정과 UPDATE 사이의 경합에서 졌다(동시 참여로 소진 / 그 사이 무효화·만료).
            throw new BusinessException(ErrorCode.INVITE_EXHAUSTED);
        }
        try {
            bandMemberRepository.saveAndFlush(BandMember.asMember(bandId, userId, now));
        } catch (DataIntegrityViolationException e) {
            // 같은 사용자의 동시 참여 — ux_band_members_active 가 최종 방어선. 사용 횟수 증가는 롤백된다.
            throw new BusinessException(ErrorCode.ALREADY_BAND_MEMBER);
        }
        return BandResponse.from(bandRepository.findById(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND)));
    }

    private BandInvite saveWithUniqueCode(long bandId, long userId, Instant now, Duration ttl, Integer maxUses) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (bandInviteRepository.existsByCode(code)) {
                continue;
            }
            try {
                return bandInviteRepository.saveAndFlush(
                        BandInvite.issue(bandId, code, userId, now, ttl, maxUses));
            } catch (DataIntegrityViolationException e) {
                // ux_band_invites_code 경합 — 재시도.
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "초대코드 생성에 반복 실패했습니다.");
    }
}
