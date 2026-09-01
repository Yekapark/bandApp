package com.yeka.bandapp.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 셋리스트에 곡 추가. 새 곡은 목록 맨 뒤에 붙는다(순서는 재정렬 API 로 바꾼다). */
public record CreateSetlistItemRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String artist,
        @Size(max = 2000) String referenceUrl
) {
}
