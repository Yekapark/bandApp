package com.yeka.bandapp.band.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 밴드 참여 이력. 탈퇴/추방은 {@code leftAt} 기록(소프트 삭제)이고, 재가입은 새 행이다.
 * 시간 필드가 {@code joinedAt}이라 {@code BaseTimeEntity}(= {@code createdAt})는 상속하지 않는다.
 *
 * <p>DB 부분 유니크 인덱스가 두 불변식을 강제한다:
 * <ul>
 *   <li>{@code (band_id, user_id) WHERE left_at IS NULL} — 한 밴드에 활성 멤버십 하나</li>
 *   <li>{@code (band_id) WHERE left_at IS NULL AND role = 'LEADER'} — 밴드당 활성 LEADER 하나</li>
 * </ul>
 */
@Entity
@Table(name = "band_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BandMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "band_id", nullable = false)
    private Long bandId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BandMemberRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    private BandMember(Long bandId, Long userId, BandMemberRole role, Instant joinedAt) {
        this.bandId = bandId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public static BandMember asLeader(long bandId, long userId, Instant now) {
        return new BandMember(bandId, userId, BandMemberRole.LEADER, now);
    }

    public static BandMember asMember(long bandId, long userId, Instant now) {
        return new BandMember(bandId, userId, BandMemberRole.MEMBER, now);
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public boolean isLeader() {
        return role == BandMemberRole.LEADER;
    }

    /** 탈퇴/추방. 이미 나간 멤버면 시각을 덮어쓰지 않는다. */
    public void leave(Instant when) {
        if (leftAt == null) {
            this.leftAt = when;
        }
    }

    public void promoteToLeader() {
        this.role = BandMemberRole.LEADER;
    }

    public void demoteToMember() {
        this.role = BandMemberRole.MEMBER;
    }
}
