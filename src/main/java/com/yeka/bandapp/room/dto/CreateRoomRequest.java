package com.yeka.bandapp.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 합주실 등록 요청. 이름만 필수다 — 주소가 없어도(멤버 집 등) 등록할 수 있어야 한다.
 *
 * <p>{@code lat}/{@code lng}는 클라이언트가 장소 검색에서 고른 후보의 좌표다. 둘 다 있으면 서버는
 * 지오코딩을 건너뛰고 이 값을 그대로 저장한다 — 등록 폼 지도에서 확인한 위치와 저장되는 위치가
 * 어긋나지 않게 하기 위해서다. 직접 입력한 주소처럼 좌표가 없으면 기존대로 주소로 지오코딩한다.
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
        @Size(max = 500) String memo,

        @Schema(description = "위도(WGS84). 장소 검색에서 고른 좌표를 그대로 보낸다. 생략하면 서버가 주소로 지오코딩한다. "
                + "한국 범위(33.0~39.7) 밖이면 400.", example = "37.5559")
        @DecimalMin("33.0") @DecimalMax("39.7") Double lat,

        @Schema(description = "경도(WGS84). lat 과 짝으로만 쓰인다 — 한쪽만 보내면 무시하고 지오코딩한다. "
                + "한국 범위(124.0~132.0) 밖이면 400.", example = "126.9236")
        @DecimalMin("124.0") @DecimalMax("132.0") Double lng
) {
}
