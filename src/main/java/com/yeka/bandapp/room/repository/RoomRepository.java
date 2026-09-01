package com.yeka.bandapp.room.repository;

import com.yeka.bandapp.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * 밴드의 활성 합주실 목록. {@code usageCount} 내림차순(Phase 3 완료 기준)이며,
     * 동률일 때 순서가 흔들리지 않도록 {@code id} 오름차순을 2차 정렬 키로 둔다.
     */
    List<Room> findByBandIdAndDeletedAtIsNullOrderByUsageCountDescIdAsc(Long bandId);

    Optional<Room> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByBandIdAndNameAndDeletedAtIsNull(Long bandId, String name);

    /**
     * 수정 가능한 컬럼(이름·주소·좌표·연락처·메모)만 대상으로 하는 부분 UPDATE.
     *
     * <p>전체 컬럼을 다시 쓰는 엔티티 {@code merge} 대신 이 쿼리를 쓰는 이유: {@code usage_count} 처럼
     * 다른 트랜잭션이 동시에 바꾸는 컬럼을 <b>덮어쓰지 않기</b> 위해서다({@code BandInviteRepository#tryConsume} 와 같은 계열).
     * {@code RoomService.update} 가 지오코딩(외부 HTTP)을 트랜잭션 밖에서 끝낸 뒤 확정된 좌표를 들고 호출한다.
     *
     * <p>{@code RoomService.update}가 트랜잭션 없이 호출하므로(지오코딩을 트랜잭션 밖에 두려고) 여기에
     * 직접 {@code @Transactional}을 단다 — modifying 쿼리는 트랜잭션이 필수다.
     *
     * @return 갱신된 행 수 (0 = 그 사이 삭제됨)
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Room r
               set r.name = :name, r.address = :address,
                   r.lat = :lat, r.lng = :lng,
                   r.phone = :phone, r.memo = :memo
             where r.id = :id and r.deletedAt is null
            """)
    int updateEditableFields(@Param("id") long id, @Param("name") String name, @Param("address") String address,
                             @Param("lat") Double lat, @Param("lng") Double lng,
                             @Param("phone") String phone, @Param("memo") String memo);

    /**
     * 일정 등록/합주실 변경 시 사용 횟수 +1. 엔티티 read-modify-write 는 동시 등록에서 갱신이 유실되므로
     * 원자 UPDATE 로 둔다({@link #updateEditableFields}와 같은 계열). 소프트 삭제된 방도 대상이다.
     *
     * <p>{@code updateEditableFields}와 달리 {@code clearAutomatically}를 쓰지 않는다 — 호출 측
     * ({@code ReservationService})은 같은 트랜잭션에서 {@code Reservation} 상태 전이를 dirty 로 들고 있고,
     * 영속성 컨텍스트를 비우면 아직 flush 되지 않은 그 변경이 사라지기 때문이다. 이 쿼리 뒤에 Room 을
     * 다시 읽지도 않으므로 비울 이유도 없다.
     *
     * @return 갱신된 행 수(대상 방이 없으면 0)
     */
    @Transactional
    @Modifying
    @Query("update Room r set r.usageCount = r.usageCount + 1 where r.id = :id")
    int increaseUsageCount(@Param("id") long id);

    /**
     * 일정 취소·거절, 또는 합주실 변경 시 이전 방의 사용 횟수 -1. 어떤 경로로도 0 밑으로 내려가지 않도록
     * {@code usageCount > 0} 조건을 건다(취소는 방이 소프트 삭제된 뒤에도 일어날 수 있다).
     *
     * @return 갱신된 행 수(대상이 없거나 이미 0이면 0)
     */
    @Transactional
    @Modifying
    @Query("update Room r set r.usageCount = r.usageCount - 1 where r.id = :id and r.usageCount > 0")
    int decreaseUsageCount(@Param("id") long id);
}
