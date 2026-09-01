package com.yeka.bandapp.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 합주실 수정 요청. {@code PUT}이므로 보내지 않은 필드는 비워지는 전체 교체다.
 * 주소가 실제로 바뀐 경우에만 지오코딩을 다시 시도한다.
 */
public record UpdateRoomRequest(
        @Schema(description = "합주실 이름. 1~50자.", example = "Sound Box A (renamed)")
        @NotBlank @Size(max = 50) String name,

        @Schema(description = "주소 원문. 값이 바뀌면 좌표를 다시 계산한다. 최대 255자.",
                example = "서울 마포구 와우산로 1")
        @Size(max = 255) String address,

        @Schema(description = "연락처. 최대 30자.", example = "02-000-0000")
        @Size(max = 30) String phone,

        @Schema(description = "메모. 최대 500자.", example = "주차 2대")
        @Size(max = 500) String memo
) {
}
