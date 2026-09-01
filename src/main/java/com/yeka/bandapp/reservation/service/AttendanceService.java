package com.yeka.bandapp.reservation.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.band.service.BandDirectoryService.MemberBrief;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.reservation.dto.AttendanceBoardResponse;
import com.yeka.bandapp.reservation.dto.AttendanceEntryResponse;
import com.yeka.bandapp.reservation.entity.AttendanceStatus;
import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.entity.ReservationAttendance;
import com.yeka.bandapp.reservation.repository.ReservationAttendanceRepository;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일정 참석 체크(RSVP). 일정이 만들어질 때 그 시점의 활성 밴드 멤버 전원에 대해 {@code PENDING} 행을
 * 만들고({@link #createPendingFor}), 이후 각자 본인 상태만 바꾼다({@link #respond}).
 *
 * <p><b>완료 기준</b> — 일정 생성 이후 밴드에 합류한 멤버도 응답할 수 있어야 한다. 그 멤버는 초기 행이
 * 없으므로 {@link #respond}가 행을 만들어 upsert 한다. 그래서 참석 현황·집계({@link #boardFor})도
 * 저장된 행이 아니라 <b>현재</b> 활성 멤버 목록을 기준으로 계산한다(그 사이 탈퇴한 멤버는 빠진다).
 *
 * <p>타인의 참석 상태는 바꿀 수 없다 — {@link #respond}는 대상과 요청자가 다르면 403.
 */
@Service
public class AttendanceService {

    private final ReservationAttendanceRepository attendanceRepository;
    private final ReservationRepository reservationRepository;
    private final BandAccessGuard accessGuard;
    private final BandDirectoryService bandDirectory;

    public AttendanceService(ReservationAttendanceRepository attendanceRepository,
                             ReservationRepository reservationRepository,
                             BandAccessGuard accessGuard,
                             BandDirectoryService bandDirectory) {
        this.attendanceRepository = attendanceRepository;
        this.reservationRepository = reservationRepository;
        this.accessGuard = accessGuard;
        this.bandDirectory = bandDirectory;
    }

    /**
     * 일정 생성 직후 호출 — 그 시점 활성 멤버 전원에 대해 {@code PENDING} 참석 행을 만든다.
     * {@link com.yeka.bandapp.reservation.service.ReservationService#create}의 트랜잭션 안에서 돈다.
     */
    @Transactional
    public void createPendingFor(long reservationId, Collection<Long> memberUserIds) {
        createPendingFor(List.of(reservationId), memberUserIds);
    }

    /**
     * 정기 일정 회차(Phase 5) 여러 건 × 멤버 전원의 {@code PENDING} 참석 행을 한 번에 만든다.
     * {@link com.yeka.bandapp.reservation.service.ReservationDirectoryService#createOccurrences}의
     * 트랜잭션 안에서 돈다 — 회차 생성과 원자적이다. 넘어온 회차는 방금 만들어진 것이라 기존 참석 행이 없다.
     */
    @Transactional
    public void createPendingFor(Collection<Long> reservationIds, Collection<Long> memberUserIds) {
        if (reservationIds.isEmpty() || memberUserIds.isEmpty()) {
            return;
        }
        List<ReservationAttendance> rows = new ArrayList<>(reservationIds.size() * memberUserIds.size());
        for (Long reservationId : reservationIds) {
            for (Long userId : memberUserIds) {
                rows.add(ReservationAttendance.pending(reservationId, userId));
            }
        }
        attendanceRepository.saveAll(rows);
    }

    /**
     * 본인 참석 상태 변경. 대상({@code targetUserId})이 요청자({@code callerUserId})와 다르면 403.
     * 요청자는 그 밴드의 활성 멤버여야 하며(뒤늦게 합류했어도 OK), 취소·거절된 일정에는 응답할 수 없다(409).
     * 행이 없으면(뒤늦게 합류) 만들어서 갱신한다.
     */
    @Transactional
    public AttendanceBoardResponse respond(long bandId, long reservationId, long targetUserId,
                                           long callerUserId, AttendanceStatus status) {
        accessGuard.requireActiveMember(bandId, callerUserId);
        if (targetUserId != callerUserId) {
            throw new BusinessException(ErrorCode.NOT_ATTENDANCE_OWNER);
        }
        Reservation reservation = requireReservation(bandId, reservationId);
        if (!reservation.isActive()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_EDITABLE);
        }
        upsert(reservationId, callerUserId, status);
        return boardFor(bandId, reservationId);
    }

    /** 일정 참석 현황(GET). 그 밴드 멤버만 볼 수 있다. */
    @Transactional(readOnly = true)
    public AttendanceBoardResponse getBoard(long bandId, long reservationId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        requireReservation(bandId, reservationId);
        return boardFor(bandId, reservationId);
    }

    /**
     * 참석 현황 조립 — <b>현재</b> 활성 멤버 전원 + 각자의 저장된 응답(없으면 PENDING). 접근 검증은
     * 호출 측 책임이다({@link com.yeka.bandapp.reservation.service.ReservationService#get}이 일정 상세에
     * 끼울 때 재검증하지 않도록).
     */
    @Transactional(readOnly = true)
    public AttendanceBoardResponse boardFor(long bandId, long reservationId) {
        List<MemberBrief> members = bandDirectory.activeMembers(bandId);
        Map<Long, ReservationAttendance> byUser = attendanceRepository.findByReservationId(reservationId).stream()
                .collect(Collectors.toMap(ReservationAttendance::getUserId, Function.identity(), (a, b) -> a));

        List<AttendanceEntryResponse> entries = new ArrayList<>(members.size());
        int attending = 0;
        for (MemberBrief m : members) {
            ReservationAttendance row = byUser.get(m.userId());
            AttendanceStatus status = row != null ? row.getStatus() : AttendanceStatus.PENDING;
            Instant respondedAt = row != null ? row.getRespondedAt() : null;
            if (status == AttendanceStatus.ATTENDING) {
                attending++;
            }
            entries.add(new AttendanceEntryResponse(m.userId(), m.name(), m.role(), status, respondedAt));
        }
        return new AttendanceBoardResponse(reservationId, attending, members.size(), entries);
    }

    // --- 내부 헬퍼 -----------------------------------------------------------

    /**
     * 본인 참석 행 upsert.
     *
     * <p>행이 이미 있으면 더티 업데이트만 하고(커밋 시 flush) 끝낸다 — 명시적 flush 를 하지 않아
     * 예외가 날 여지가 없다.
     *
     * <p>행이 없으면(일정 생성 이후 합류한 멤버) INSERT 한다. 같은 멤버의 <b>동시 최초 응답</b>(더블탭)이
     * 경합하면 {@code (reservation_id, user_id)} 유니크 제약에 걸려 진 쪽은
     * {@code DataIntegrityViolationException}을 받는다. 이때 같은 트랜잭션에서 복구를 시도하지 않고
     * ({@code flush} 실패로 트랜잭션이 이미 rollback-only) 도메인 예외(409)로 변환한다 —
     * 클라이언트가 다시 시도하면 그때는 행이 있어 갱신 경로로 처리된다(CLAUDE.md 규칙).
     */
    private void upsert(long reservationId, long userId, AttendanceStatus status) {
        ReservationAttendance existing = attendanceRepository
                .findByReservationIdAndUserId(reservationId, userId)
                .orElse(null);
        if (existing != null) {
            existing.respond(status, Instant.now());
            return;
        }
        ReservationAttendance row = ReservationAttendance.pending(reservationId, userId);
        row.respond(status, Instant.now());
        try {
            attendanceRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException race) {
            throw new BusinessException(ErrorCode.ATTENDANCE_UPDATE_CONFLICT);
        }
    }

    /** 타 밴드의 일정은 존재를 알리지 않고 {@code RESERVATION_NOT_FOUND}. */
    private Reservation requireReservation(long bandId, long reservationId) {
        return reservationRepository.findByIdAndBandId(reservationId, bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }
}
