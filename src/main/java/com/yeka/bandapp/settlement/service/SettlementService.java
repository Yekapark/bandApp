package com.yeka.bandapp.settlement.service;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.band.service.BandDirectoryService.MemberBrief;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.notification.event.NotificationEvents;
import com.yeka.bandapp.reservation.service.AttendanceService;
import com.yeka.bandapp.reservation.service.ReservationDirectoryService;
import com.yeka.bandapp.settlement.dto.CreateSettlementRequest;
import com.yeka.bandapp.settlement.dto.RecalculateSettlementRequest;
import com.yeka.bandapp.settlement.dto.SettlementResponse;
import com.yeka.bandapp.settlement.dto.SettlementShareResponse;
import com.yeka.bandapp.settlement.entity.Settlement;
import com.yeka.bandapp.settlement.entity.SettlementShare;
import com.yeka.bandapp.settlement.entity.SplitType;
import com.yeka.bandapp.settlement.repository.SettlementRepository;
import com.yeka.bandapp.settlement.repository.SettlementShareRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일정 정산(N빵). 일정 총비용을 {@link SplitType}에 따라 멤버별 {@link SettlementShare}로 나눈다.
 *
 * <p><b>핵심 규칙</b>
 * <ul>
 *   <li>일정당 정산은 하나({@code reservation_id} 유니크). 만든 뒤에는 {@link #recalculate}로만 바꾼다 —
 *       서버가 참석 응답 변화를 감지해 자동으로 다시 나누지 않는다(BUILD_PLAN Phase 7).</li>
 *   <li>몫 합계는 항상 총액과 일치한다. 나누어떨어지지 않는 나머지는 "밴드장 먼저 → 가입일 순"으로
 *       앞에서부터 1원씩 더한다({@link SettlementCalculator}). ATTENDEES_ONLY 이고 밴드장이 불참이면
 *       가장 먼저 가입한 참석자가 나머지를 진다.</li>
 *   <li>{@code ATTENDEES_ONLY}인데 참석자가 0명이면 정산을 만들지 않는다(409).</li>
 *   <li>생성·재계산은 일정 등록자 본인 또는 밴드장만. 납부 체크({@code paid})는 본인 몫만.</li>
 * </ul>
 *
 * <p>외부 HTTP 호출이 없으므로 각 명령은 하나의 일반 {@code @Transactional}로 처리한다.
 * 재계산은 정산 행에 비관적 락을 걸어 같은 일정에 대한 동시 재계산을 직렬화한다.
 */
@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementShareRepository shareRepository;
    private final BandAccessGuard accessGuard;
    private final BandDirectoryService bandDirectory;
    private final ReservationDirectoryService reservationDirectory;
    private final AttendanceService attendanceService;
    private final ApplicationEventPublisher eventPublisher;

    public SettlementService(SettlementRepository settlementRepository,
                             SettlementShareRepository shareRepository,
                             BandAccessGuard accessGuard,
                             BandDirectoryService bandDirectory,
                             ReservationDirectoryService reservationDirectory,
                             AttendanceService attendanceService,
                             ApplicationEventPublisher eventPublisher) {
        this.settlementRepository = settlementRepository;
        this.shareRepository = shareRepository;
        this.accessGuard = accessGuard;
        this.bandDirectory = bandDirectory;
        this.reservationDirectory = reservationDirectory;
        this.attendanceService = attendanceService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 정산 생성. 현재 밴드 멤버(EQUAL) 또는 참석자(ATTENDEES_ONLY) 기준으로 몫을 만든다.
     * 이미 정산이 있으면 409 {@code SETTLEMENT_ALREADY_EXISTS}, ATTENDEES_ONLY 인데 참석자가 없으면
     * 409 {@code SETTLEMENT_NO_ATTENDEES}(아무것도 저장하지 않는다).
     */
    @Transactional
    public SettlementResponse create(long bandId, long reservationId, long callerId,
                                     CreateSettlementRequest request) {
        requireManager(bandId, reservationId, callerId);

        List<MemberBrief> recipients = recipientsFor(bandId, reservationId, request.splitType());
        if (recipients.isEmpty()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_NO_ATTENDEES);
        }

        Settlement settlement = Settlement.create(reservationId, request.totalAmount(), request.splitType());
        try {
            settlementRepository.saveAndFlush(settlement);
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ALREADY_EXISTS);
        }

        Map<Long, Integer> amounts = SettlementCalculator.split(request.totalAmount(), userIds(recipients));
        List<SettlementShare> shares = amounts.entrySet().stream()
                .map(e -> SettlementShare.of(settlement.getId(), e.getKey(), e.getValue()))
                .toList();
        shareRepository.saveAll(shares);

        publishRequestedEvent(bandId, reservationId, settlement.getTotalAmount(), amounts.keySet(), callerId);
        return assemble(bandId, reservationId, settlement, shares);
    }

    /** 정산 현황 조회. 그 밴드 멤버만. */
    @Transactional(readOnly = true)
    public SettlementResponse get(long bandId, long reservationId, long callerId) {
        accessGuard.requireActiveMember(bandId, callerId);
        reservationDirectory.requesterOf(bandId, reservationId); // 타 밴드 일정이면 404
        Settlement settlement = requireSettlement(reservationId);
        return assemble(bandId, reservationId, settlement,
                shareRepository.findBySettlementId(settlement.getId()));
    }

    /**
     * 재계산. 넘어온 {@code totalAmount}/{@code splitType}이 있으면 갱신하고(없으면 유지), 현재
     * 밴드 멤버·참석자 기준으로 몫을 다시 만든다. 계속 대상인 멤버의 행은 금액만 새로 매기고
     * 납부 여부는 보존, 빠진 멤버의 행은 삭제, 새 멤버의 행은 미납으로 추가한다.
     */
    @Transactional
    public SettlementResponse recalculate(long bandId, long reservationId, long callerId,
                                          RecalculateSettlementRequest request) {
        requireManager(bandId, reservationId, callerId);

        Settlement settlement = settlementRepository.findByReservationIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        int total = request.totalAmount() != null ? request.totalAmount() : settlement.getTotalAmount();
        SplitType type = request.splitType() != null ? request.splitType() : settlement.getSplitType();

        List<MemberBrief> recipients = recipientsFor(bandId, reservationId, type);
        if (recipients.isEmpty()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_NO_ATTENDEES);
        }
        settlement.changeTerms(total, type);
        Map<Long, Integer> amounts = SettlementCalculator.split(total, userIds(recipients));

        List<SettlementShare> existing = shareRepository.findBySettlementId(settlement.getId());
        Map<Long, SettlementShare> byUser = existing.stream()
                .collect(Collectors.toMap(SettlementShare::getUserId, Function.identity()));

        shareRepository.deleteAll(existing.stream()
                .filter(s -> !amounts.containsKey(s.getUserId()))
                .toList());

        List<SettlementShare> result = new ArrayList<>(amounts.size());
        List<SettlementShare> added = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : amounts.entrySet()) {
            SettlementShare share = byUser.get(e.getKey());
            if (share != null) {
                share.reassign(e.getValue()); // paid/paidAt 보존
            } else {
                share = SettlementShare.of(settlement.getId(), e.getKey(), e.getValue());
                added.add(share);
            }
            result.add(share);
        }
        shareRepository.saveAll(added);

        publishRequestedEvent(bandId, reservationId, settlement.getTotalAmount(), amounts.keySet(), callerId);
        return assemble(bandId, reservationId, settlement, result);
    }

    /** 정산 생성·재계산 시 분담 대상자(요청자 제외)에게 "정산 요청" 알림. 실제 발송은 커밋 후. */
    private void publishRequestedEvent(long bandId, long reservationId, int totalAmount,
                                      java.util.Set<Long> shareUserIds, long callerId) {
        List<Long> recipients = shareUserIds.stream().filter(id -> id != callerId).toList();
        if (!recipients.isEmpty()) {
            eventPublisher.publishEvent(new NotificationEvents.SettlementRequested(
                    bandId, reservationId, totalAmount, recipients));
        }
    }

    /**
     * 본인 몫의 납부 상태 변경. {@code targetUserId}가 요청자 본인이 아니면 403
     * {@code NOT_SETTLEMENT_SHARE_OWNER}. 요청자가 분담 대상이 아니면 404 {@code SETTLEMENT_SHARE_NOT_FOUND}.
     *
     * <p>{@link #recalculate}와 같은 정산 행 비관적 락을 잡아 직렬화한다 — 재계산이 이 멤버의 몫을
     * 삭제/재산정하는 것과 납부 체크가 겹쳐 갱신이 유실되거나 사라진 몫이 응답에 실리는 레이스를 막는다.
     */
    @Transactional
    public SettlementResponse markPaid(long bandId, long reservationId, long targetUserId,
                                       long callerId, boolean paid) {
        accessGuard.requireActiveMember(bandId, callerId);
        if (targetUserId != callerId) {
            throw new BusinessException(ErrorCode.NOT_SETTLEMENT_SHARE_OWNER);
        }
        reservationDirectory.requesterOf(bandId, reservationId); // 타 밴드 일정이면 404
        Settlement settlement = settlementRepository.findByReservationIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementShare share = shareRepository
                .findBySettlementIdAndUserId(settlement.getId(), callerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_SHARE_NOT_FOUND));
        share.markPaid(paid, Instant.now()); // 더티 업데이트만(flush 없음)

        return assemble(bandId, reservationId, settlement,
                shareRepository.findBySettlementId(settlement.getId()));
    }

    // --- 내부 헬퍼 -----------------------------------------------------------

    /** 생성·재계산 권한 — 일정 등록자 본인 또는 밴드장. 타 밴드 일정이면 404(존재를 알리지 않음). */
    private void requireManager(long bandId, long reservationId, long callerId) {
        BandMember member = accessGuard.requireActiveMember(bandId, callerId);
        long requestedBy = reservationDirectory.requesterOf(bandId, reservationId);
        if (callerId != requestedBy && !member.isLeader()) {
            throw new BusinessException(ErrorCode.NOT_SETTLEMENT_MANAGER);
        }
    }

    private Settlement requireSettlement(long reservationId) {
        return settlementRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }

    /**
     * 분배 대상자를 우선순위 순으로. EQUAL 은 현재 활성 멤버 전원, ATTENDEES_ONLY 는 그중 참석자만.
     * 어느 쪽이든 밴드장을 맨 앞으로 옮기고 나머지는 가입 순을 유지한다 → 나머지를 밴드장이(없으면 최고참이) 진다.
     */
    private List<MemberBrief> recipientsFor(long bandId, long reservationId, SplitType type) {
        List<MemberBrief> active = bandDirectory.activeMembers(bandId); // 가입 순
        List<MemberBrief> pool = active;
        if (type == SplitType.ATTENDEES_ONLY) {
            Set<Long> attending = attendanceService.attendingUserIds(reservationId);
            pool = active.stream().filter(m -> attending.contains(m.userId())).toList();
        }
        return pool.stream()
                .sorted(Comparator.comparingInt(m -> "LEADER".equals(m.role()) ? 0 : 1))
                .toList();
    }

    private static List<Long> userIds(List<MemberBrief> members) {
        return members.stream().map(MemberBrief::userId).toList();
    }

    /** 몫에 표시용 이름·역할을 붙여 응답을 조립한다. 밴드를 떠난 과거 분담자는 이름을 따로 조회해 채운다. */
    private SettlementResponse assemble(long bandId, long reservationId, Settlement settlement,
                                        List<SettlementShare> shares) {
        Map<Long, MemberBrief> members = bandDirectory.activeMembers(bandId).stream()
                .collect(Collectors.toMap(MemberBrief::userId, Function.identity()));
        List<Long> unknownIds = shares.stream()
                .map(SettlementShare::getUserId)
                .filter(id -> !members.containsKey(id))
                .toList();
        Map<Long, String> fallbackNames = unknownIds.isEmpty()
                ? Map.of()
                : bandDirectory.displayNamesOf(unknownIds);

        List<SettlementShareResponse> shareResponses = shares.stream()
                .sorted(Comparator.comparingLong(SettlementShare::getUserId))
                .map(share -> {
                    MemberBrief brief = members.get(share.getUserId());
                    String name = brief != null
                            ? brief.name()
                            : fallbackNames.getOrDefault(share.getUserId(), "(알 수 없음)");
                    String role = brief != null ? brief.role() : "MEMBER";
                    return SettlementShareResponse.of(share, name, role);
                })
                .toList();

        return SettlementResponse.of(reservationId, settlement, shareResponses);
    }
}
