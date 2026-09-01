package com.yeka.bandapp.room.naver;

import java.util.Optional;

/**
 * 주소 → 좌표 변환 창구. 실제 호출은 {@link NaverGeocodingClient}, 테스트는 가짜 구현으로 대체한다.
 * 이 인터페이스가 지도 API 연동의 유일한 경계이며, 나머지 코드는 네이버를 모른다.
 *
 * <p><b>이 창구는 예외를 던지지 않는다.</b> 카카오 연동({@code KakaoClient})과 다른 점이다.
 * 지오코딩 실패로 합주실 등록이 막히면 안 된다는 것이 Phase 3 완료 기준이므로, API 키 미설정,
 * 4xx/5xx, 타임아웃, 검색 결과 0건을 모두 {@link Optional#empty()}로 같게 취급한다.
 * 호출자는 "좌표를 못 얻었다" 하나만 처리하면 된다.
 */
public interface GeocodingClient {

    /**
     * 주소 문자열을 좌표로 바꾼다.
     *
     * @param address 사용자가 입력한 주소 원문. {@code null}·공백이면 바로 {@link Optional#empty()}
     * @return 변환된 좌표. 어떤 이유로든 얻지 못하면 {@link Optional#empty()}
     */
    Optional<Coordinates> geocode(String address);
}
