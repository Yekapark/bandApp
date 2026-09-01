package com.yeka.bandapp.reservation.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 셋리스트 재정렬. {@code itemIds}는 그 일정의 <b>모든</b> 셋리스트 항목 id 를 원하는 순서대로 나열한
 * 것이어야 한다(빠지거나 남거나 중복이면 400). 이 순서대로 {@code orderNo}가 1..N 으로 다시 매겨진다.
 */
public record ReorderSetlistRequest(
        @NotEmpty List<Long> itemIds
) {
}
