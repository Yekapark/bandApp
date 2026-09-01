package com.yeka.bandapp.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 합주실 등록 요청. 이름만 필수다 — 주소가 없어도(멤버 집 등) 등록할 수 있어야 한다.
 *
 * @param name    합주실 이름. 같은 밴드 안에서 중복 불가
 * @param address 주소 원문. 있으면 지오코딩을 시도하되, 실패해도 등록은 성공한다
 * @param phone   연락처 (외부 예약은 앱 밖에서 하므로 전화번호가 실질적인 예약 수단이다)
 * @param memo    메모 (주차, 장비, 요금 등 자유 기재)
 */
public record CreateRoomRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 255) String address,
        @Size(max = 30) String phone,
        @Size(max = 500) String memo
) {
}
