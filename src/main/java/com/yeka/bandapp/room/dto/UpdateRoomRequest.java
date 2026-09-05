package com.yeka.bandapp.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 합주실 수정 요청. {@code PUT}이므로 보내지 않은 필드는 비워지는 전체 교체다.
 * 주소가 실제로 바뀐 경우에만 좌표를 다시 구한다 — 요청에 좌표가 있으면 그 값을, 없으면 지오코딩.
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
        @Size(max = 500) String memo,

        @Schema(description = "위도(WGS84). 장소 검색에서 고른 좌표. 주소가 바뀌었는데 이 값이 없으면 서버가 지오코딩한다. "
                + "한국 범위(33.0~39.7) 밖이면 400.", example = "37.5559")
        @DecimalMin("33.0") @DecimalMax("39.7") Double lat,

        @Schema(description = "경도(WGS84). lat 과 짝으로만 쓰인다. 한국 범위(124.0~132.0) 밖이면 400.",
                example = "126.9236")
        @DecimalMin("124.0") @DecimalMax("132.0") Double lng
) {
}
