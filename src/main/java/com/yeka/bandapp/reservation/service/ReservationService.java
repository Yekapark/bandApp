package com.yeka.bandapp.reservation.service;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.entity.ReservationPermission;
import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.reservation.dto.CreateReservationRequest;
import com.yeka.bandapp.reservation.dto.OverlapWarning;
import com.yeka.bandapp.reservation.dto.ReservationListResponse;
import com.yeka.bandapp.reservation.dto.ReservationResponse;
import com.yeka.bandapp.reservation.dto.ReservationWriteResponse;
import com.yeka.bandapp.reservation.dto.UpdateReservationRequest;
import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.entity.ReservationStatus;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import com.yeka.bandapp.room.service.RoomDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 외부 HTTP 호출이 없으므로 각 명령은 하나의 일반 {@code @Transactional}로 처리한다(지오코딩이 있는
 * {@code RoomService}와 달리 트랜잭션을 쪼갤 이유가 없다).
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final BandAccessGuard accessGuard;
    private final BandDirectoryService bandDirectory;
    private final RoomDirectoryService roomDirectory;

    public ReservationService(ReservationRepository reservationRepository, BandAccessGuard accessGuard,
                              BandDirectoryService bandDirectory, RoomDirectoryService roomDirectory) {
        this.reservationRepository = reservationRepository;
        this.accessGuard = accessGuard;
        this.bandDirectory = bandDirectory;
        this.roomDirectory = roomDirectory;
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

        return writeResponse(saved, findOverlaps(bandId, saved));
    }

    /** 캘린더용 기간 조회. {@code from}/{@code to}는 필수이며 {@code to > from}이어야 한다. */
    @Transactional(readOnly = true)
    public ReservationListResponse list(long bandId, long userId, Instant from, Instant to, boolean includeInactive) {
        accessGuard.requireActiveMember(bandId, userId);
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간(from, to)이 필요합니다.");
        }
        if (!to.isAfter(from)) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_PERIOD);
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

    @Transactional(readOnly = true)
    public ReservationResponse get(long bandId, long reservationId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        Reservation r = reservation(bandId, reservationId);
        return ReservationResponse.from(r, roomName(r.getRoomId()));
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
        Reservation r = reservation(bandId, reservationId);
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
            roomDirectory.decreaseUsage(previousRoomId);
            roomDirectory.increaseUsage(request.roomId());
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
     */
    @Transactional
    public void cancel(long bandId, long reservationId, long userId) {
        BandMember member = accessGuard.requireActiveMember(bandId, userId);
        Reservation r = reservation(bandId, reservationId);
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

    private Reservation reservation(long bandId, long reservationId) {
        return reservationRepository.findByIdAndBandId(reservationId, bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private Reservation requirePending(long bandId, long reservationId) {
        Reservation r = reservation(bandId, reservationId);
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

    private List<Reservation> findOverlaps(long bandId, Reservation self) {
        return reservationRepository.findOverlapping(
                bandId, self.getStartAt(), self.getEndAt(), self.getId());
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
