package com.yeka.bandapp.room.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import com.yeka.bandapp.room.naver.Coordinates;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 밴드가 등록해 둔 합주 장소. 밴드별 독립 레코드이며(같은 물리적 합주실이라도 밴드마다 별개),
 * 예약 가능 자원이 아니라 주소록에 가깝다 — 가용시간·예약 상태를 갖지 않는다.
 *
 * <p>{@code lat}/{@code lng}는 지오코딩 결과이며 <b>없을 수 있다</b>. 지오코딩 실패가 등록을 막으면
 * 안 된다는 것이 Phase 3 완료 기준이라, 좌표는 항상 부가 정보로 다룬다.
 *
 * <p>삭제는 {@code deletedAt}을 찍는 소프트 삭제다. Phase 4 이후의 과거 일정이 이미 삭제된
 * 합주실의 이름·주소를 계속 참조할 수 있어야 하기 때문이다.
 */
@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "band_id", nullable = false)
    private Long bandId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String address;

    /** 위도(WGS84). 지오코딩 미설정·실패 시 {@code null}. */
    private Double lat;

    /** 경도(WGS84). 지오코딩 미설정·실패 시 {@code null}. */
    private Double lng;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String memo;

    /** 이 합주실로 등록된 일정 수. Phase 4 일정 등록에서 증가한다. 목록 정렬 기준. */
    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Room(Long bandId, Long createdBy, String name, String address, String phone, String memo) {
        this.bandId = bandId;
        this.createdBy = createdBy;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.memo = memo;
        this.usageCount = 0;
    }

    public static Room create(long bandId, long createdBy, String name, String address,
                              String phone, String memo) {
        return new Room(bandId, createdBy, name, address, phone, memo);
    }

    /** 이름·주소·연락처·메모 교체. 좌표는 여기서 건드리지 않는다({@link #applyCoordinates} 참조). */
    public void update(String name, String address, String phone, String memo) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.memo = memo;
    }

    /** 지오코딩 성공 시에만 호출한다. 실패했다고 기존 좌표를 지우지는 않는다. */
    public void applyCoordinates(Coordinates coordinates) {
        this.lat = coordinates.lat();
        this.lng = coordinates.lng();
    }

    /** 주소가 지워지거나 새 주소의 지오코딩이 실패했을 때, 옛 주소의 좌표가 남지 않게 비운다. */
    public void clearCoordinates() {
        this.lat = null;
        this.lng = null;
    }

    /** Phase 4 일정 등록에서 사용. */
    public void increaseUsage() {
        this.usageCount++;
    }

    public void delete(Instant when) {
        if (deletedAt == null) {
            this.deletedAt = when;
        }
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean belongsTo(long bandId) {
        return this.bandId != null && this.bandId == bandId;
    }
}
