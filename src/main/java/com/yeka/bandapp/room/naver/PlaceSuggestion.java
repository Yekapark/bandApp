package com.yeka.bandapp.room.naver;

/**
 * 네이버 지역검색 결과 한 건. 합주실 등록 폼에서 "이 장소로 채우기"에 쓰인다.
 *
 * <p>{@code lat}/{@code lng}는 네이버가 준 좌표를 WGS84로 변환한 값이며, 변환이 애매하면 {@code null}
 * 이다(좌표는 부가 정보 — 없어도 이름·주소로 등록에 문제없다).
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
