package com.yeka.bandapp.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 합주실 등록 요청. 이름만 필수다 — 주소가 없어도(멤버 집 등) 등록할 수 있어야 한다.
 */
public record CreateRoomRequest(
        @Schema(description = "합주실 이름. 같은 밴드 안에서 중복 불가. 1~50자.", example = "Sound Box A")
        @NotBlank @Size(max = 50) String name,

        @Schema(description = "주소 원문. 있으면 좌표 변환을 시도하되 실패해도 등록은 성공한다. 최대 255자.",
                example = "서울 마포구 와우산로 1")
        @Size(max = 255) String address,

        @Schema(description = "연락처(전화). 최대 30자.", example = "02-000-0000")
        @Size(max = 30) String phone,

        @Schema(description = "메모(주차·장비·요금 등 자유 기재). 최대 500자.", example = "주차 2대, 드럼 있음")
        @Size(max = 500) String memo
) {
}
