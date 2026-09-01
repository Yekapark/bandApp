package com.yeka.bandapp.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 합주실 수정 요청. {@code PUT}이므로 보내지 않은 필드는 비워지는 전체 교체다.
 * 주소가 실제로 바뀐 경우에만 지오코딩을 다시 시도한다.
 */
public record UpdateRoomRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 255) String address,
        @Size(max = 30) String phone,
        @Size(max = 500) String memo
) {
}
