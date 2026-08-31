package com.yeka.bandapp.band.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
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

/**
 * 밴드. 생성자는 자동으로 LEADER 가 된다({@link com.yeka.bandapp.band.entity.BandMember} 활성 LEADER 행).
 *
 * <p>{@code leaderId}는 현재 밴드장의 캐시다 — 위임 시 {@code BandMember} 역할 교체와 함께 갱신한다.
 */
@Entity
@Table(name = "bands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Band extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "leader_id", nullable = false)
    private Long leaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_permission", nullable = false, length = 20)
    private ReservationPermission reservationPermission;

    private Band(String name, Long leaderId) {
        this.name = name;
        this.leaderId = leaderId;
        this.reservationPermission = ReservationPermission.LEADER_ONLY;
    }

    public static Band create(String name, long leaderId) {
        return new Band(name, leaderId);
    }

    public void changeReservationPermission(ReservationPermission permission) {
        this.reservationPermission = permission;
    }

    /** 밴드장 위임. 호출 측이 {@code BandMember} 역할 교체까지 한 트랜잭션에서 처리한다. */
    public void handOverLeadership(long newLeaderId) {
        this.leaderId = newLeaderId;
    }
}
