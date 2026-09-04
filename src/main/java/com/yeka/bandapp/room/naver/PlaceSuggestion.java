package com.yeka.bandapp.room.naver;

/**
 * 장소 검색 결과 한 건. 합주실 등록 폼에서 "이 장소로 채우기"에 쓰인다.
 *
 * <p>{@code lat}/{@code lng}는 검색 API가 준 WGS84 좌표이며, 값이 없거나 한국 범위를 벗어나면
 * {@code null}이다(좌표는 부가 정보 — 없어도 이름·주소로 등록에 문제없다).
 */
public record PlaceSuggestion(
        String name,
        String roadAddress,
        String address,
        String category,
        String phone,
        Double lat,
        Double lng
) {
}
