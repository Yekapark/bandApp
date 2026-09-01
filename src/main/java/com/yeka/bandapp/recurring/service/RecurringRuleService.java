package com.yeka.bandapp.recurring.service;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.entity.ReservationPermission;
import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.recurring.dto.CreateRecurringRuleRequest;
import com.yeka.bandapp.recurring.dto.RecurringRuleDetailResponse;
import com.yeka.bandapp.recurring.dto.RecurringRuleListResponse;
import com.yeka.bandapp.recurring.dto.RecurringRuleResponse;
import com.yeka.bandapp.recurring.dto.RecurringRuleWriteResponse;
import com.yeka.bandapp.recurring.entity.RecurringRule;
import com.yeka.bandapp.recurring.repository.RecurringRuleRepository;
import com.yeka.bandapp.reservation.dto.OverlapWarning;
import com.yeka.bandapp.reservation.dto.ReservationResponse;
import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.service.OccurrenceSlot;
import com.yeka.bandapp.reservation.service.ReservationDirectoryService;
import com.yeka.bandapp.room.service.RoomDirectoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 정기 일정 규칙의 등록·조회·삭제와 회차 자동 생성.
 *
 * <p>회차는 이 서비스가 계산만 하고({@link OccurrenceGenerator}), 실제 저장·합주실 {@code usageCount}
 * 증감은 {@link ReservationDirectoryService} 창구에 맡긴다.
 *
 * <p>등록 권한 — 밴드의 {@link ReservationPermission}을 따르되 "규칙 단위 승인" 방침이라
 * {@code ANYONE}만 일반 멤버가 등록할 수 있고 나머지 두 모드는 밴드장 전용이다. 어느 경우든
 * 생성된 회차의 status 는 {@code CONFIRMED}로 시작한다(회차마다 다시 승인받지 않는다).
 *
 * <p>규칙 삭제는 소프트 삭제이며 <b>아직 시작하지 않은 회차만</b> 취소한다 — 과거 회차와 그에
 * 연결될 정산 기록은 그대로 남는다(Phase 5 완료 기준).
 */
@Service
public class RecurringRuleService {

    /** 등록 응답 겹침 경고 상한. {@code ReservationService}와 같은 값. */
    private static final int OVERLAP_WARNING_LIMIT = 20;

    /** 배치가 한 페이지에 훑는 규칙 수. */
    public static final int EXTEND_PAGE_SIZE = 200;

    private final RecurringRuleRepository ruleRepository;
    private final BandAccessGuard accessGuard;
    private final BandDirectoryService bandDirectory;
    private final RoomDirectoryService roomDirectory;
    private final ReservationDirectoryService reservationDirectory;
    private final RecurringProperties properties;

    public RecurringRuleService(RecurringRuleRepository ruleRepository, BandAccessGuard accessGuard,
                                BandDirectoryService bandDirectory, RoomDirectoryService roomDirectory,
                                ReservationDirectoryService reservationDirectory,
                                RecurringProperties properties) {
        this.ruleRepository = ruleRepository;
        this.accessGuard = accessGuard;
        this.bandDirectory = bandDirectory;
        this.roomDirectory = roomDirectory;
        this.reservationDirectory = reservationDirectory;
        this.properties = properties;
    }

    /**
     * 규칙을 등록하고 지평선(오늘 + {@code horizonWeeks})까지의 회차를 즉시 만든다.
     * 겹치는 일정이 있어도 <b>201로 성공</b>하며 그 목록이 {@code overlaps}에 실린다.
     */
    @Transactional
    public RecurringRuleWriteResponse create(long bandId, long userId, CreateRecurringRuleRequest request) {
        BandMember member = accessGuard.requireActiveMember(bandId, userId);
        requireRuleCreationAllowed(bandId, member);
        validate(request);
        roomDirectory.requireActiveRoom(bandId, request.roomId());

        RecurringRule rule = ruleRepository.save(RecurringRule.create(
                bandId, request.roomId(), request.frequency(), request.dayOfWeek(),
                request.startTime(), request.endTime(), request.startDate(), request.endDate(),
                request.cost(), trimToNull(request.note()), userId));

        List<OccurrenceSlot> slots = freshSlots(rule, null);
        reservationDirectory.createOccurrences(bandId, rule.getRoomId(), userId, rule.getId(),
                slots, rule.getCost(), rule.getNote());

        List<Reservation> occurrences = reservationDirectory.occurrencesSince(
                rule.getId(), displayWindowStart());
        List<Reservation> overlaps = reservationDirectory.overlapsAmong(
                bandId, rule.getId(), slots, OVERLAP_WARNING_LIMIT);

        Map<Long, String> roomNames = roomNamesFor(rule, occurrences, overlaps);
        return new RecurringRuleWriteResponse(
                RecurringRuleResponse.from(rule, roomNames.get(rule.getRoomId())),
                occurrences.size(),
                occurrences.stream().map(r -> ReservationResponse.from(r, roomNames.get(r.getRoomId()))).toList(),
                overlaps.stream().map(o -> OverlapWarning.from(o, roomNames.get(o.getRoomId()))).toList());
    }

    @Transactional(readOnly = true)
    public RecurringRuleListResponse list(long bandId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        List<RecurringRule> rules = ruleRepository.findByBandIdAndDeletedAtIsNullOrderByCreatedAtDesc(bandId);
        Map<Long, String> roomNames = roomDirectory.namesOf(
                rules.stream().map(RecurringRule::getRoomId).toList());
        List<RecurringRuleResponse> body = rules.stream()
                .map(r -> RecurringRuleResponse.from(r, roomNames.get(r.getRoomId())))
                .toList();
        return new RecurringRuleListResponse(bandId, body.size(), body);
    }

    @Transactional(readOnly = true)
    public RecurringRuleDetailResponse get(long bandId, long ruleId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        RecurringRule rule = activeRule(bandId, ruleId);
        List<Reservation> occurrences = reservationDirectory.occurrencesSince(
                rule.getId(), displayWindowStart());

        Set<Long> roomIds = new LinkedHashSet<>();
        roomIds.add(rule.getRoomId());
        occurrences.forEach(o -> roomIds.add(o.getRoomId()));
        Map<Long, String> roomNames = roomDirectory.namesOf(roomIds);

        return new RecurringRuleDetailResponse(
                RecurringRuleResponse.from(rule, roomNames.get(rule.getRoomId())),
                occurrences.size(),
                occurrences.stream().map(r -> ReservationResponse.from(r, roomNames.get(r.getRoomId()))).toList());
    }

    /**
     * 규칙 삭제. 등록자 본인 또는 밴드장만. 규칙에 {@code deletedAt}을 찍고, 아직 시작하지 않은
     * 회차만 {@code CANCELLED}로 바꾼다. 과거·진행 중인 회차는 그대로 둔다.
     *
     * <p>대상 규칙 행을 {@code FOR UPDATE}로 잠근다 — 같은 규칙에 대한 동시 DELETE(더블탭·재시도)를
     * 직렬화해, 미래 회차 취소와 그에 딸린 합주실 {@code usageCount} 감소가 정확히 한 번만 일어나게 한다.
     * 두 번째 요청은 락을 기다렸다가 이미 삭제된 것을 보고 404({@code RECURRING_RULE_NOT_FOUND}).
     */
    @Transactional
    public void delete(long bandId, long ruleId, long userId) {
        BandMember member = accessGuard.requireActiveMember(bandId, userId);
        RecurringRule rule = ruleRepository.findActiveByIdAndBandIdForUpdate(ruleId, bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECURRING_RULE_NOT_FOUND));
        if (!rule.isCreatedBy(userId) && !member.isLeader()) {
            throw new BusinessException(ErrorCode.NOT_RECURRING_RULE_OWNER);
        }
        Instant now = Instant.now();
        reservationDirectory.cancelFutureOccurrences(rule.getId(), now);
        rule.delete(now);
    }

    // --- 배치 연장 (RecurringExtensionJob 이 루프를 소유한다) ------------------

    /** 배치용: 활성 규칙 id 를 {@code afterId} 다음부터 최대 {@value #EXTEND_PAGE_SIZE}개. */
    @Transactional(readOnly = true)
    public List<Long> activeRuleIdsAfter(long afterId) {
        return ruleRepository.findActiveAfter(afterId, PageRequest.of(0, EXTEND_PAGE_SIZE))
                .stream().map(RecurringRule::getId).toList();
    }

    /**
     * 규칙 하나의 회차를 지평선까지 이어서 만든다. 이미 만든 마지막 회차 <b>다음</b>부터 계산하므로
     * 여러 번 호출해도 회차 수가 늘지 않는다(멱등). 규칙이 그 사이 삭제됐으면 0.
     *
     * <p>규칙 행을 {@code FOR UPDATE}로 잠근다 — 연장 도중 사용자가 같은 규칙을 삭제하면,
     * 소프트 삭제된 규칙에 새 {@code CONFIRMED} 회차가 붙어 고아가 되는 레이스를 막는다.
     *
     * @return 이번 호출에서 새로 만든 회차 수
     */
    @Transactional
    public int extendRule(long ruleId) {
        RecurringRule rule = ruleRepository.findByIdForUpdate(ruleId).orElse(null);
        if (rule == null || rule.isDeleted()) {
            return 0;
        }
        LocalDate exclusiveAfter = reservationDirectory.lastOccurrenceStartOf(ruleId)
                .map(last -> LocalDate.ofInstant(last, properties.zoneId()))
                .orElse(null);
        List<OccurrenceSlot> slots = freshSlots(rule, exclusiveAfter);
        return reservationDirectory.createOccurrences(
                rule.getBandId(), rule.getRoomId(), rule.getCreatedBy(), rule.getId(),
                slots, rule.getCost(), rule.getNote());
    }

    // --- 내부 헬퍼 ----------------------------------------------------------

    /**
     * 생성 대상 슬롯 계산. 회차는 <b>오늘 ± {@code horizonWeeks}</b> 구간에서만 만든다 —
     * {@code startDate}를 과거로 멀리 잡아도 한 요청에 수백 개 회차를 백필하지 않는다.
     * 배치({@code extendRule})는 이미 만든 마지막 회차 다음({@code exclusiveAfter})부터 앞으로만
     * 이어가므로 이 바닥에 걸리지 않는다.
     */
    private List<OccurrenceSlot> freshSlots(RecurringRule rule, LocalDate exclusiveAfter) {
        ZoneId zone = properties.zoneId();
        LocalDate today = LocalDate.now(zone);
        LocalDate horizonEnd = today.plusWeeks(properties.horizonWeeks());
        // 이 날짜(포함)보다 이전 회차는 만들지 않는다. occurrenceDates 의 exclusiveAfter 는
        // "이 날짜 다음부터"라, 바닥을 포함시키려면 하루 앞선 값을 넘긴다.
        LocalDate floorExclusive = today.minusWeeks(properties.horizonWeeks()).minusDays(1);
        LocalDate effectiveAfter = (exclusiveAfter == null || exclusiveAfter.isBefore(floorExclusive))
                ? floorExclusive : exclusiveAfter;

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, horizonEnd, effectiveAfter);
        if (dates.isEmpty()) {
            return List.of();
        }
        Set<Instant> existing = reservationDirectory.occurrenceStartsOf(rule.getId());
        return dates.stream()
                .map(d -> new OccurrenceSlot(
                        OccurrenceGenerator.toInstant(d, rule.getStartTime(), zone),
                        OccurrenceGenerator.toInstant(d, rule.getEndTime(), zone)))
                .filter(slot -> !existing.contains(slot.startAt()))
                .toList();
    }

    /** 규칙 상세·등록 응답에 실을 회차의 시작 하한(오늘 − {@code horizonWeeks}, 해당 지역 자정). */
    private Instant displayWindowStart() {
        ZoneId zone = properties.zoneId();
        return LocalDate.now(zone).minusWeeks(properties.horizonWeeks()).atStartOfDay(zone).toInstant();
    }

    private void requireRuleCreationAllowed(long bandId, BandMember member) {
        ReservationPermission permission = bandDirectory.reservationPermissionOf(bandId);
        if (permission != ReservationPermission.ANYONE && !member.isLeader()) {
            throw new BusinessException(ErrorCode.NOT_BAND_LEADER);
        }
    }

    private void validate(CreateRecurringRuleRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.INVALID_RECURRING_TIME);
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_RECURRING_DATE_RANGE);
        }
    }

    /** 타 밴드 규칙은 존재를 알리지 않고 {@code RECURRING_RULE_NOT_FOUND}. */
    private RecurringRule activeRule(long bandId, long ruleId) {
        return ruleRepository.findByIdAndBandIdAndDeletedAtIsNull(ruleId, bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECURRING_RULE_NOT_FOUND));
    }

    private Map<Long, String> roomNamesFor(RecurringRule rule, List<Reservation> occurrences,
                                           List<Reservation> overlaps) {
        Set<Long> roomIds = new LinkedHashSet<>();
        roomIds.add(rule.getRoomId());
        occurrences.forEach(o -> roomIds.add(o.getRoomId()));
        overlaps.forEach(o -> roomIds.add(o.getRoomId()));
        return roomDirectory.namesOf(roomIds);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
