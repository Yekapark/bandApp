package com.yeka.bandapp.reservation.dto;

import java.util.List;

/**
 * 일정 등록/수정 응답. {@code overlaps}가 비어 있지 않아도 요청은 <b>성공</b>이다(201/200) — 겹침은
 * 경고이지 거부 사유가 아니다. 클라이언트는 이 목록을 사용자에게 보여 주고 저장 여부는 이미 끝난
 * 상태에서 "그래도 이대로 두겠다 / 고치겠다"를 선택하게 하면 된다.
 */
public record ReservationWriteResponse(
        ReservationResponse reservation,
        List<OverlapWarning> overlaps
) {
}
