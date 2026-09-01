package com.yeka.bandapp.reservation.service;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.entity.ReservationPermission;
import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.reservation.dto.CreateReservationRequest;
import com.yeka.bandapp.reservation.dto.OverlapWarning;
import com.yeka.bandapp.reservation.dto.ReservationDetailResponse;
import com.yeka.bandapp.reservation.dto.ReservationListResponse;
import com.yeka.bandapp.reservation.dto.ReservationResponse;
import com.yeka.bandapp.reservation.dto.ReservationWriteResponse;
import com.yeka.bandapp.reservation.dto.UpdateReservationRequest;
import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.entity.ReservationStatus;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import com.yeka.bandapp.room.service.RoomDirectoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 일정 등록·승인·수정·취소·조회.
 *
 * <p>핵심 원칙 — <b>시간대 겹침을 이유로 어떤 요청도 거부하지 않는다</b>(BUILD_PLAN 2장 2번).
 * {@code create}/{@code update}는 겹치는 기존 일정을 찾아 응답의 {@code overlaps}에 담을 뿐,
 * 저장은 정상적으로 끝낸다.
 *
 * <p>등록 직후 status 는 밴드의 {@link ReservationPermission}으로 갈린다:
 * {@code LEADER_ONLY}(밴드장만, 확정) · {@code ANYONE}(누구나, 확정) · {@code APPROVAL_REQUIRED}(누구나 신청, 대기).
 *
 * <p>합주실 {@code usageCount}는 이 서비스가 관리한다: 등록 시 +1, 취소·거절 시 -1,
 * 수정으로 합주실이 바뀌면 이전 방 -1 / 새 방 +1. 증감은 {@link RoomDirectoryService}의 원자 UPDATE 로만 한다.
 * 상태를 바꾸는 명령(승인·거절·수정·취소)은 대상 일정 행에 비관적 락을 걸어(같은 일정에 대한 동시 요청을
 * 직렬화) 상태 전이와 그에 딸린 {@code usageCount} 증감이 정확히 한 번만 일어나게 한다.
 * 외부 HTTP 호출이 없으므로 각 명령은 하나의 일반 {@code @Transactional}로 처리한다.
 */
@Service
public class ReservationService {

    /** 겹침 경고에 담는 최대 건수. 넓은 구간을 잡은 일정이 응답을 무한정 키우지 못하게 한다. */
    private static final int OVERLAP_WARNING_LIMIT = 20;

    /** 캘린더 조회가 한 번에 볼 수 있는 최대 기간. 1년 + 여유. */
    private static final long MAX_CALENDAR_RANGE_DAYS = 400;

    private final ReservationRepository reservationRepository;
    private final BandAccessGuard accessGuard;
    private final BandDirectoryService bandDirectory;
    private final RoomDirectoryService roomDirectory;
    private final AttendanceService attendanceService;
    private final SetlistService setlistService;

    public ReservationService(ReservationRepository reservationRepository, BandAccessGuard accessGuard,
                              BandDirectoryService bandDirectory, RoomDirectoryService roomDirectory,
                              AttendanceService attendanceService, SetlistService setlistService) {
        this.reservationRepository = reservationRepository;
        this.accessGuard = accessGuard;
        this.bandDirectory = bandDirectory;
        this.roomDirectory = roomDirectory;
        this.attendanceService = attendanceService;
        this.setlistService = setlistService;
    }

    /**
     * 일정 등록. 밴드 권한 모드에 따라 초기 status 를 정하고, 합주실을 검증한 뒤 저장한다.
     * 겹치는 일정이 있어도 <b>201로 성공</b>하며 그 목록이 {@code overlaps}에 실린다.
     */
    @Transactional
    public ReservationWriteResponse create(long bandId, long userId, CreateReservationRequest request) {
        BandMember member = accessGuard.requireActiveMember(bandId, userId);
        validatePeriod(request.startAt(), request.endAt());
        roomDirectory.requireActiveRoom(bandId, request.roomId());

        ReservationStatus initialStatus = initialStatusFor(bandId, member);

        Reservation saved = reservationRepository.save(Reservation.create(
                bandId, request.roomId(), userId, initialStatus,
                request.startAt(), request.endAt(), request.cost(), trimToNull(request.note())));
        roomDirectory.increaseUsage(request.roomId());
        // 일정 생성 시 그 시점의 활성 밴드 멤버 전원을 PENDING 참석으로 만든다(BUILD_PLAN Phase 6).
        attendanceService.createPendingFor(saved.getId(), bandDirectory.activeMembers(bandId));

        return writeResponse(saved, findOverlaps(bandId, saved));
    }

    /** 캘린더용 기간 조회. {@code from}/{@code to}는 필수이며 {@code to > from}, 최대 {@value #MAX_CALENDAR_RANGE_DAYS}일이다. */
    @Transactional(readOnly = true)
    public ReservationListResponse list(long bandId, long userId, Instant from, Instant to, boolean includeInactive) {
        accessGuard.requireActiveMember(bandId, userId);
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간(from, to)이 필요합니다.");
        }
        if (!to.isAfter(from)) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_PERIOD);
        }
        if (Duration.between(from, to).toDays() > MAX_CALENDAR_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.RESERVATION_RANGE_TOO_WIDE);
        }

        Collection<ReservationStatus> statuses = includeInactive
                ? EnumSet.allOf(ReservationStatus.class)
                : EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
        List<Reservation> rows = reservationRepository
                .findByBandIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                        bandId, statuses, to, from);

        Map<Long, String> roomNames = roomDirectory.namesOf(
                rows.stream().map(Reservation::getRoomId).toList());
        List<ReservationResponse> reservations = rows.stream()
                .map(r -> ReservationResponse.from(r, roomNames.get(r.getRoomId())))
                .toList();
        return new ReservationListResponse(bandId, reservations.size(), reservations);
    }

    /**
     * 일정 상세. 일정 자체에 더해 참석 현황·집계("참석 N / 전체 M")와 셋리스트를 함께 싣는다
     * (BUILD_PLAN Phase 6). 멤버십은 여기서 한 번만 확인하고, 참석/셋리스트 조립은 재검증하지 않는다.
     */
    @Transactional(readOnly = true)
    public ReservationDetailResponse get(long bandId, long reservationId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        Reservation r = reservation(bandId, reservationId);
        return ReservationDetailResponse.of(
                ReservationResponse.from(r, roomName(r.getRoomId())),
                attendanceService.boardFor(bandId, reservationId),
                setlistService.itemsFor(reservationId));
    }

    /**
     * 일정 수정(PUT 전체 교체). 등록자 본인 또는 밴드장만. 취소·거절된 일정은 수정할 수 없다(409).
     *
     * <p>{@code APPROVAL_REQUIRED} 밴드에서 <b>확정된</b> 일정의 시간 또는 합주실이 바뀌면 다시
     * 승인 대기({@code PENDING})로 돌아간다. 비고·비용만 바뀐 경우는 확정 상태를 유지한다.
     */
    @Transactional
    public ReservationWriteResponse update(long bandId, long reservationId, long userId,
                                           UpdateReservationRequest request) {
        BandMember member = accessGuard.requireActiveMember(bandId, userId);
        Reservation r = lockedReservation(bandId, reservationId);
        requireOwnerOrLeader(r, member, userId);
        if (!r.isActive()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_EDITABLE);
        }
        validatePeriod(request.startAt(), request.endAt());

        long previousRoomId = r.getRoomId();
        boolean roomChanged = !request.roomId().equals(previousRoomId);
        boolean timeChanged = !request.startAt().equals(r.getStartAt())
                || !request.endAt().equals(r.getEndAt());
        boolean wasConfirmed = r.getStatus() == ReservationStatus.CONFIRMED;

        if (roomChanged) {
            roomDirectory.requireActiveRoom(bandId, request.roomId());
        }

        r.reschedule(request.roomId(), request.startAt(), request.endAt());
        r.changeDetails(request.cost(), trimToNull(request.note()));

        if (roomChanged) {
            shiftUsage(previousRoomId, request.roomId());
        }
        if (wasConfirmed && (roomChanged || timeChanged)
                && bandDirectory.reservationPermissionOf(bandId) == ReservationPermission.APPROVAL_REQUIRED) {
            r.revertToPending();
        }

        return writeResponse(r, findOverlaps(bandId, r));
    }

    /** 승인 대기 → 확정. 밴드장만. 대기 상태가 아니면 409. */
    @Transactional
    public ReservationResponse approve(long bandId, long reservationId, long userId) {
        accessGuard.requireLeader(bandId, userId);
        Reservation r = requirePending(bandId, reservationId);
        r.approve();
        return ReservationResponse.from(r, roomName(r.getRoomId()));
    }

    /** 승인 대기 → 거절. 밴드장만. 등록 시 올렸던 합주실 사용 횟수를 되돌린다. */
    @Transactional
    public ReservationResponse reject(long bandId, long reservationId, long userId) {
        accessGuard.requireLeader(bandId, userId);
        Reservation r = requirePending(bandId, reservationId);
        r.reject();
        roomDirectory.decreaseUsage(r.getRoomId());
        return ReservationResponse.from(r, roomName(r.getRoomId()));
    }

    /**
     * 일정 취소. 등록자 본인 또는 밴드장만. 행은 남고 status 만 {@code CANCELLED}가 된다.
     * 이미 취소된 일정에 다시 호출해도 멱등하게 아무 일도 일어나지 않는다(사용 횟수가 두 번 깎이지 않도록).
     * 이미 거절된 일정은 취소 대상이 아니다(409).
     *
     * <p>대상 행에 비관적 락을 걸어, 같은 일정을 향한 동시 취소(더블탭 등)에서도 감소가 한 번만 반영된다.
     */
    @Transactional
    public void cancel(long bandId, long reservationId, long userId) {
        BandMember member = accessGuard.requireActiveMember(bandId, userId);
        Reservation r = lockedReservation(bandId, reservationId);
        requireOwnerOrLeader(r, member, userId);
        if (r.getStatus() == ReservationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_EDITABLE);
        }
        if (r.cancel()) {
            roomDirectory.decreaseUsage(r.getRoomId());
        }
    }

    // --- 내부 헬퍼 -------------------------------------------------------------

    private ReservationStatus initialStatusFor(long bandId, BandMember member) {
        ReservationPermission permission = bandDirectory.reservationPermissionOf(bandId);
        return switch (permission) {
            case LEADER_ONLY -> {
                if (!member.isLeader()) {
                    throw new BusinessException(ErrorCode.NOT_BAND_LEADER);
                }
                yield ReservationStatus.CONFIRMED;
            }
            case ANYONE -> ReservationStatus.CONFIRMED;
            case APPROVAL_REQUIRED -> ReservationStatus.PENDING;
        };
    }

    /** 읽기 전용 조회(상세·목록). 타 밴드 일정은 존재를 알리지 않고 {@code RESERVATION_NOT_FOUND}. */
    private Reservation reservation(long bandId, long reservationId) {
        return reservationRepository.findByIdAndBandId(reservationId, bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    /** 상태를 바꾸는 명령용 — 행에 {@code FOR UPDATE} 락을 걸어 같은 일정에 대한 동시 전이를 직렬화한다. */
    private Reservation lockedReservation(long bandId, long reservationId) {
        return reservationRepository.findByIdAndBandIdForUpdate(reservationId, bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private Reservation requirePending(long bandId, long reservationId) {
        Reservation r = lockedReservation(bandId, reservationId);
        if (r.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_PENDING);
        }
        return r;
    }

    private void requireOwnerOrLeader(Reservation r, BandMember member, long userId) {
        if (!r.isRequestedBy(userId) && !member.isLeader()) {
            throw new BusinessException(ErrorCode.NOT_RESERVATION_OWNER);
        }
    }

    private void validatePeriod(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_PERIOD);
        }
    }

    /**
     * 합주실 이동 시 사용 횟수 이전 방 -1 / 새 방 +1. 두 UPDATE 를 <b>방 id 오름차순</b>으로 실행해,
     * 두 요청이 같은 두 방을 서로 반대로 옮길 때 생길 수 있는 교착(AB-BA)을 피한다.
     */
    private void shiftUsage(long previousRoomId, long newRoomId) {
        if (previousRoomId < newRoomId) {
            roomDirectory.decreaseUsage(previousRoomId);
            roomDirectory.increaseUsage(newRoomId);
        } else {
            roomDirectory.increaseUsage(newRoomId);
            roomDirectory.decreaseUsage(previousRoomId);
        }
    }

    private List<Reservation> findOverlaps(long bandId, Reservation self) {
        return reservationRepository.findOverlapping(
                bandId, self.getStartAt(), self.getEndAt(), self.getId(),
                PageRequest.of(0, OVERLAP_WARNING_LIMIT));
    }

    /** 일정 + 겹침 목록을 합주실 이름까지 채워 응답으로 만든다. 이름은 한 번의 조회로 모아 해결한다. */
    private ReservationWriteResponse writeResponse(Reservation reservation, List<Reservation> overlaps) {
        Set<Long> roomIds = new LinkedHashSet<>();
        roomIds.add(reservation.getRoomId());
        overlaps.forEach(o -> roomIds.add(o.getRoomId()));
        Map<Long, String> roomNames = roomDirectory.namesOf(roomIds);

        List<OverlapWarning> warnings = overlaps.stream()
                .map(o -> OverlapWarning.from(o, roomNames.get(o.getRoomId())))
                .toList();
        return new ReservationWriteResponse(
                ReservationResponse.from(reservation, roomNames.get(reservation.getRoomId())),
                warnings);
    }

    private String roomName(long roomId) {
        return roomDirectory.namesOf(List.of(roomId)).get(roomId);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
