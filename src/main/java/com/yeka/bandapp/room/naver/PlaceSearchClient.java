package com.yeka.bandapp.room.naver;

import java.util.List;

/**
 * 장소(합주실) 이름·주소 검색 창구. 실제 호출은 {@link KakaoLocalSearchClient}, 테스트는 가짜 구현.
 * 이 인터페이스가 장소 검색 연동의 유일한 경계다.
 *
 * <p>{@link GeocodingClient}와 마찬가지로 <b>예외를 던지지 않는다.</b> 키 미설정, 4xx/5xx, 타임아웃,
 * 결과 0건을 모두 <b>빈 리스트</b>로 같게 취급한다. 검색은 편의 기능이므로 실패해도 사용자는 주소를
 * 직접 입력해 등록할 수 있어야 한다.
 */
public interface PlaceSearchClient {

    /**
     * 질의어로 장소를 검색한다.
     *
     * @param query 검색어(합주실 이름 등). {@code null}·공백이면 빈 리스트
     * @return 최대 5건. 어떤 이유로든 얻지 못하면 빈 리스트
     */
    List<PlaceSuggestion> search(String query);
}
