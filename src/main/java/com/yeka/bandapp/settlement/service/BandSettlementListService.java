package com.yeka.bandapp.settlement.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.room.entity.Room;
import com.yeka.bandapp.room.repository.RoomRepository;
import com.yeka.bandapp.settlement.dto.BandSettlementListResponse;
import com.yeka.bandapp.settlement.entity.SettlementShare;
import com.yeka.bandapp.settlement.repository.BandSettlementRow;
import com.yeka.bandapp.settlement.repository.SettlementRepository;
import com.yeka.bandapp.settlement.repository.SettlementShareRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 밴드 정산 목록. 일정 상세를 하나씩 열어보지 않고 <b>내가 아직 안 낸 돈</b>을 한눈에 보기 위한
 * 읽기 전용 화면용 서비스다. 정산 생성·재계산·납부 체크는 {@link SettlementService} 가 맡는다.
 *
 * <p>정산·몫·합주실을 각각 <b>한 번씩</b>만 조회한다(정산 건마다 다시 읽지 않는다).
 */
@Service
public class BandSettlementListService {

    private static final int MAX_SIZE = 50;
    private static final int DEFAULT_SIZE = 20;

    private final SettlementRepository settlementRepository;
    private final SettlementShareRepository shareRepository;
    private final RoomRepository roomRepository;
    private final BandAccessGuard accessGuard;

    public BandSettlementListService(SettlementRepository settlementRepository,
                                     SettlementShareRepository shareRepository,
                                     RoomRepository roomRepository,
                                     BandAccessGuard accessGuard) {
        this.settlementRepository = settlementRepository;
        this.shareRepository = shareRepository;
        this.roomRepository = roomRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public BandSettlementListResponse list(long bandId, long userId, Long cursor, Integer size) {
        accessGuard.requireActiveMember(bandId, userId);
        int limit = clamp(size);

        // 다음 페이지가 있는지 알아야 하므로 한 건 더 읽는다.
        List<BandSettlementRow> rows = settlementRepository
                .findBandFeed(bandId, cursor, PageRequest.of(0, limit + 1));
        if (rows.isEmpty()) {
            return BandSettlementListResponse.of(List.of(), limit);
        }

        List<Long> settlementIds = rows.stream().map(BandSettlementRow::settlementId).toList();
        Map<Long, List<SettlementShare>> sharesBySettlement =
                shareRepository.findBySettlementIdIn(settlementIds).stream()
                        .collect(Collectors.groupingBy(SettlementShare::getSettlementId));

        Map<Long, String> roomNames = roomNames(rows);

        List<BandSettlementListResponse.Item> items = rows.stream()
                .map(row -> toItem(row, sharesBySettlement.getOrDefault(row.settlementId(), List.of()),
                        roomNames, userId))
                .toList();
        return BandSettlementListResponse.of(items, limit);
    }

    /** 합주실 이름. 삭제된 합주실은 목록에 없고, 그 줄은 이름이 {@code null} 이 된다. */
    private Map<Long, String> roomNames(List<BandSettlementRow> rows) {
        List<Long> roomIds = rows.stream()
                .map(BandSettlementRow::roomId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        return roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, Room::getName));
    }

    private static BandSettlementListResponse.Item toItem(BandSettlementRow row,
                                                          List<SettlementShare> shares,
                                                          Map<Long, String> roomNames,
                                                          long userId) {
        SettlementShare mine = shares.stream()
                .filter(s -> Objects.equals(s.getUserId(), userId))
                .findFirst()
                .orElse(null);
        int paidCount = (int) shares.stream().filter(SettlementShare::isPaid).count();

        return new BandSettlementListResponse.Item(
                row.settlementId(),
                row.reservationId(),
                row.startAt(),
                row.roomId() == null ? null : roomNames.get(row.roomId()),
                row.totalAmount(),
                shares.size(),
                paidCount,
                mine == null ? null : mine.getAmount(),
                mine == null ? null : mine.isPaid());
    }

    private static int clamp(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
