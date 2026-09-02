package com.yeka.bandapp.reservation.service;

import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.entity.ReservationStatus;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import com.yeka.bandapp.room.service.RoomDirectoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 다른 도메인(정기 일정 등)이 일정 회차를 만들거나 되돌릴 때 쓰는 창구. 정기 도메인이
 * {@code ReservationRepository}를 직접 만지지 않게 해, "일정 도메인이 {@code Reservation}과
 * 합주실 {@code usageCount}를 관리한다"는 Phase 4의 불변조건을 유지한다
 * ({@link RoomDirectoryService}와 같은 역할).
 */
@Service
public class ReservationDirectoryService {

    private static final Set<ReservationStatus> ACTIVE =
            EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;
    private final RoomDirectoryService roomDirectory;
    private final BandDirectoryService bandDirectory;
    private final AttendanceService attendanceService;

    public ReservationDirectoryService(ReservationRepository reservationRepository,
                                       RoomDirectoryService roomDirectory,
                                       BandDirectoryService bandDirectory,
                                       AttendanceService attendanceService) {
        this.reservationRepository = reservationRepository;
        this.roomDirectory = roomDirectory;
        this.bandDirectory = bandDirectory;
        this.attendanceService = attendanceService;
    }

    /**
     * 정기 규칙이 만든 회차들을 저장하고 합주실 {@code usageCount}를 한 번에 올리며, 각 회차에
     * 그 시점 활성 밴드 멤버 전원의 {@code PENDING} 참석 행을 만든다(BUILD_PLAN Phase 6 — 회차도
     * 단발 일정과 동일). 호출 측이 이미 있는 슬롯을 걸러 넘긴다는 전제이며,
     * {@code ux_reservations_rule_slot}은 동시 실행 경합에 대한 DB 레벨 안전장치로만 남는다
     * (그 경우 트랜잭션이 롤백되고 다음 실행이 메운다).
     *
     * @return 실제로 저장한 회차 수
     */
    @Transactional
    public int createOccurrences(long bandId, long roomId, long createdBy, long ruleId,
                                 List<OccurrenceSlot> slots, Integer cost, String note) {
        if (slots.isEmpty()) {
            return 0;
        }
        List<Reservation> rows = slots.stream()
                .map(s -> Reservation.ofRecurringRule(bandId, roomId, createdBy, ruleId,
                        s.startAt(), s.endAt(), cost, note))
                .toList();
        reservationRepository.saveAll(rows);
        roomDirectory.increaseUsageBy(roomId, rows.size());
        attendanceService.createPendingFor(
                rows.stream().map(Reservation::getId).toList(),
                bandDirectory.activeMemberUserIds(bandId));
        return rows.size();
    }

    /**
     * 알림 도메인(Phase 9)이 리마인더·참석 독촉을 보낼 대상 일정을 훑을 때 쓴다 — 반열림 구간
     * {@code (from, to]}에 시작하는 확정 일정. 알림 패키지가 {@code Reservation} 엔티티를 몰라도 되도록
     * 작은 레코드로 준다.
     */
    @Transactional(readOnly = true)
    public List<UpcomingReservation> upcomingConfirmed(Instant from, Instant to, int limit) {
        return reservationRepository.findUpcomingConfirmed(from, to, PageRequest.of(0, limit)).stream()
                .map(r -> new UpcomingReservation(r.getId(), r.getBandId(), r.getStartAt()))
                .toList();
    }

    /** 알림 배치가 훑는 "곧 시작하는 확정 일정" 한 건의 요약. */
    public record UpcomingReservation(long reservationId, long bandId, Instant startAt) {
    }

    /**
     * 정산 도메인(Phase 7)이 일정의 등록자를 확인할 때 쓴다 — 정산 생성·재계산은 일정 등록자 본인 또는
     * 밴드장만 할 수 있다. 경로의 {@code bandId}와 대조해 타 밴드 일정이면 존재를 알리지 않고
     * {@code RESERVATION_NOT_FOUND}(404).
     */
    @Transactional(readOnly = true)
    public long requesterOf(long bandId, long reservationId) {
        return reservationRepository.findByIdAndBandId(reservationId, bandId)
                .map(Reservation::getRequestedBy)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    /** 규칙이 이미 만든 회차 시작 시각(상태 무관). 재생성 시 중복 슬롯을 걸러내는 데 쓴다. */
    @Transactional(readOnly = true)
    public Set<Instant> occurrenceStartsOf(long ruleId) {
        return Set.copyOf(reservationRepository.findOccurrenceStarts(ruleId));
    }

    /** 규칙의 마지막(가장 늦은) 회차 시작 시각. 배치가 "그 다음부터" 이어 만든다. */
    @Transactional(readOnly = true)
    public java.util.Optional<Instant> lastOccurrenceStartOf(long ruleId) {
        return reservationRepository.findFirstByRecurringRuleIdOrderByStartAtDesc(ruleId)
                .map(Reservation::getStartAt);
    }

    /**
     * 규칙 상세·등록 응답용 — {@code from} 이후 회차만(취소분 포함, start_at 오름차순). 오래된 회차는
     * 응답에서 빠지며 캘린더 API 로 조회한다(Phase 4 §8.1 #3과 같은 취지 — 응답 크기 상한).
     */
    @Transactional(readOnly = true)
    public List<Reservation> occurrencesSince(long ruleId, Instant from) {
        return reservationRepository
                .findByRecurringRuleIdAndStartAtGreaterThanEqualOrderByStartAtAsc(ruleId, from);
    }

    /**
     * 규칙 삭제 시: 아직 시작하지 않은 살아 있는 회차만 {@code CANCELLED}로 바꾸고, 취소된 만큼
     * 합주실 {@code usageCount}를 되돌린다. 과거·진행 중인 회차는 건드리지 않는다.
     *
     * <p>회차가 개별 수정으로 다른 합주실을 가리킬 수 있으므로 방별로 집계해 감소시키되,
     * 방 id 오름차순으로 실행해 {@code ReservationService.shiftUsage}와 같은 AB-BA 교착을 피한다.
     *
     * @return 취소한 회차 총수
     */
    @Transactional
    public int cancelFutureOccurrences(long ruleId, Instant from) {
        List<Reservation> future = reservationRepository
                .findByRecurringRuleIdAndStartAtGreaterThanEqualAndStatusIn(ruleId, from, ACTIVE);
        Map<Long, Integer> cancelledPerRoom = new TreeMap<>();
        for (Reservation r : future) {
            if (r.cancel()) {
                cancelledPerRoom.merge(r.getRoomId(), 1, Integer::sum);
            }
        }
        cancelledPerRoom.forEach(roomDirectory::decreaseUsageBy);
        return cancelledPerRoom.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 정기 규칙 등록 응답에 실을 겹침 경고. 각 회차 슬롯과 겹치는 기존 일정을 모아 id 로 중복을 제거하고
     * {@code limit}건까지만 준다. 저장 여부와는 무관한 <b>경고</b>다.
     */
    @Transactional(readOnly = true)
    public List<Reservation> overlapsAmong(long bandId, long excludeRuleId, List<OccurrenceSlot> slots,
                                           int limit) {
        Map<Long, Reservation> byId = new LinkedHashMap<>();
        for (OccurrenceSlot slot : slots) {
            if (byId.size() >= limit) {
                break;
            }
            List<Reservation> hits = reservationRepository.findOverlappingExcludingRule(
                    bandId, slot.startAt(), slot.endAt(), excludeRuleId, PageRequest.of(0, limit));
            for (Reservation r : hits) {
                byId.putIfAbsent(r.getId(), r);
            }
        }
        return byId.values().stream().limit(limit).collect(Collectors.toList());
    }
}
