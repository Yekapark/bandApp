package com.yeka.bandapp.reservation.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.reservation.dto.CreateSetlistItemRequest;
import com.yeka.bandapp.reservation.dto.ReorderSetlistRequest;
import com.yeka.bandapp.reservation.dto.SetlistItemResponse;
import com.yeka.bandapp.reservation.dto.SetlistResponse;
import com.yeka.bandapp.reservation.dto.UpdateSetlistItemRequest;
import com.yeka.bandapp.reservation.entity.SetlistItem;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import com.yeka.bandapp.reservation.repository.SetlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일정별 셋리스트 CRUD — 곡명·아티스트·참고 링크·순서(BUILD_PLAN Phase 6). 밴드 멤버 누구나 편집할 수
 * 있다(등록자 제한 없음). 순서는 추가 시 맨 뒤에 붙고, {@link #reorder}로만 바꾼다.
 */
@Service
public class SetlistService {

    private final SetlistItemRepository setlistRepository;
    private final ReservationRepository reservationRepository;
    private final BandAccessGuard accessGuard;

    public SetlistService(SetlistItemRepository setlistRepository,
                          ReservationRepository reservationRepository,
                          BandAccessGuard accessGuard) {
        this.setlistRepository = setlistRepository;
        this.reservationRepository = reservationRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public SetlistResponse list(long bandId, long reservationId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        requireReservation(bandId, reservationId);
        return itemsFor(reservationId);
    }

    /** 내부용 — 접근 검증은 호출 측 책임(일정 상세 응답에 끼울 때). */
    @Transactional(readOnly = true)
    public SetlistResponse itemsFor(long reservationId) {
        List<SetlistItemResponse> items = setlistRepository
                .findByReservationIdOrderByOrderNoAscIdAsc(reservationId).stream()
                .map(SetlistItemResponse::from)
                .toList();
        return new SetlistResponse(reservationId, items.size(), items);
    }

    @Transactional
    public SetlistItemResponse add(long bandId, long reservationId, long userId,
                                   CreateSetlistItemRequest request) {
        accessGuard.requireActiveMember(bandId, userId);
        requireReservation(bandId, reservationId);
        int nextOrder = setlistRepository.maxOrderNo(reservationId) + 1;
        SetlistItem saved = setlistRepository.save(SetlistItem.create(
                reservationId, request.title().trim(),
                trimToNull(request.artist()), trimToNull(request.referenceUrl()), nextOrder));
        return SetlistItemResponse.from(saved);
    }

    @Transactional
    public SetlistItemResponse update(long bandId, long reservationId, long itemId, long userId,
                                      UpdateSetlistItemRequest request) {
        accessGuard.requireActiveMember(bandId, userId);
        requireReservation(bandId, reservationId);
        SetlistItem item = requireItem(reservationId, itemId);
        item.edit(request.title().trim(), trimToNull(request.artist()), trimToNull(request.referenceUrl()));
        return SetlistItemResponse.from(item);
    }

    @Transactional
    public void delete(long bandId, long reservationId, long itemId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        requireReservation(bandId, reservationId);
        setlistRepository.delete(requireItem(reservationId, itemId));
    }

    /**
     * 재정렬 — {@code itemIds}는 그 일정의 모든 셋리스트 항목을 원하는 순서대로 나열한 것이어야 한다.
     * 빠지거나 남거나 중복이면 400. 그 순서대로 {@code orderNo}가 1..N 으로 다시 매겨진다.
     */
    @Transactional
    public SetlistResponse reorder(long bandId, long reservationId, long userId, ReorderSetlistRequest request) {
        accessGuard.requireActiveMember(bandId, userId);
        requireReservation(bandId, reservationId);

        List<SetlistItem> current = setlistRepository.findByReservationIdOrderByOrderNoAscIdAsc(reservationId);
        Map<Long, SetlistItem> byId = current.stream()
                .collect(Collectors.toMap(SetlistItem::getId, Function.identity()));
        List<Long> requested = request.itemIds();
        if (requested.size() != current.size() || !byId.keySet().equals(new HashSet<>(requested))) {
            throw new BusinessException(ErrorCode.SETLIST_REORDER_MISMATCH);
        }
        int order = 1;
        for (Long id : requested) {
            byId.get(id).moveTo(order++);
        }
        return itemsFor(reservationId);
    }

    // --- 내부 헬퍼 -----------------------------------------------------------

    /** 타 밴드의 일정은 존재를 알리지 않고 {@code RESERVATION_NOT_FOUND}. */
    private void requireReservation(long bandId, long reservationId) {
        if (reservationRepository.findByIdAndBandId(reservationId, bandId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
    }

    private SetlistItem requireItem(long reservationId, long itemId) {
        return setlistRepository.findByIdAndReservationId(itemId, reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETLIST_ITEM_NOT_FOUND));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
