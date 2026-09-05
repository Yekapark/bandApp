package com.yeka.bandapp.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 밴드의 정산 목록. 일정 상세를 하나씩 열어보지 않고 <b>내가 아직 안 낸 돈</b>을 한 화면에서
 * 보기 위한 것이다.
 *
 * <p>{@code myOutstandingTotal}은 이 목록에서 내 몫 중 미납인 것들의 합이다. 밴드 전체 합계는
 * 담지 않는다 — 필요해지면 그때 더한다.
 */
public record BandSettlementListResponse(
        @Schema(description = "정산 목록. 정산이 등록된 순서의 역순(최신 먼저).")
        List<Item> settlements,

        @Schema(description = "이 목록에서 내가 아직 안 낸 금액의 합.", example = "45000")
        int myOutstandingTotal,

        @Schema(description = "다음 페이지 커서(정산 id). null 이면 더 없음.", example = "12")
        Long nextCursor
) {

    /** 정산 한 건 + 그 안에서의 내 몫. */
    public record Item(
            @Schema(example = "7") long settlementId,
            @Schema(example = "12") long reservationId,
            @Schema(description = "합주 시작 시각.") Instant startAt,
            @Schema(description = "합주실 이름. 삭제된 합주실이면 null.", example = "그루브합주실 사당점")
            String roomName,
            @Schema(description = "정산 총액.", example = "90000") int totalAmount,
            @Schema(description = "참여 인원(몫 개수).", example = "3") int shareCount,
            @Schema(description = "납부 완료 인원.", example = "1") int paidCount,

            @Schema(description = "내 몫. 내가 이 정산에 포함되지 않았으면 null.", example = "30000")
            Integer myAmount,
            @Schema(description = "내가 납부했는지. 내 몫이 없으면 null.", example = "false")
            Boolean myPaid
    ) {
        /** 내가 아직 내야 할 금액(내 몫이 없거나 이미 냈으면 0). */
        public int myOutstanding() {
            return (myAmount != null && Boolean.FALSE.equals(myPaid)) ? myAmount : 0;
        }
    }

    public static BandSettlementListResponse of(List<Item> items, int size) {
        boolean hasMore = items.size() > size;
        List<Item> page = hasMore ? items.subList(0, size) : items;
        int outstanding = page.stream().mapToInt(Item::myOutstanding).sum();
        return new BandSettlementListResponse(
                page, outstanding,
                hasMore ? page.get(page.size() - 1).settlementId() : null);
    }
}
