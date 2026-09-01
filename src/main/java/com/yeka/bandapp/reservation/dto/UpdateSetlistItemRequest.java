package com.yeka.bandapp.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 셋리스트 곡 정보 수정(PUT 전체 교체) — 보내지 않은 선택 필드({@code artist}·{@code referenceUrl})는
 * 비워진다. 순서는 이 API 로 바꾸지 않는다(재정렬 API 사용).
 */
public record UpdateSetlistItemRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String artist,
        @Size(max = 2000) String referenceUrl
) {
}
