package com.yeka.bandapp.band.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * 밴드 초대코드. 재발급 시 기존 활성 코드를 revoked 처리하므로 밴드당 활성 코드는 보통 0~1개다.
 *
 * <p>참여 요청의 사용 횟수 증가는 이 엔티티의 setter 가 아니라 저장소의 조건부 UPDATE
 * ({@code BandInviteRepository#tryConsume})로 처리한다 — 동시 참여에서 {@code maxUses}를
 * 정확히 지키기 위해서다. 여기 메서드들은 참여 거부 사유를 분기하는 읽기 판정에만 쓴다.
 */
@Entity
@Table(name = "band_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BandInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "band_id", nullable = false)
    private Long bandId;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** {@code null} 이면 사용 횟수 무제한. */
    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private BandInvite(Long bandId, String code, Long createdBy, Instant createdAt,
                       Instant expiresAt, Integer maxUses) {
        this.bandId = bandId;
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
        this.usedCount = 0;
        this.revoked = false;
    }

    public static BandInvite issue(long bandId, String code, long createdBy,
                                   Instant now, Duration ttl, Integer maxUses) {
        return new BandInvite(bandId, code, createdBy, now, now.plus(ttl), maxUses);
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isExhausted() {
        return maxUses != null && usedCount >= maxUses;
    }
}
