package com.yeka.bandapp.support;

import com.yeka.bandapp.room.naver.PlaceSearchClient;
import com.yeka.bandapp.room.naver.PlaceSuggestion;

import java.util.ArrayList;
import java.util.List;

/**
 * 프로그래머블 지역검색 스텁. 실제 네이버 클라이언트처럼 <b>예외를 던지지 않고</b> 리스트로만 결과를 낸다.
 *
 * <p>{@link #callCount()}로 호출 횟수를 노출해 "검색어가 비면 외부 호출을 하지 않는다" 같은 규칙도 확인한다.
 */
public class FakePlaceSearchClient implements PlaceSearchClient {

    private List<PlaceSuggestion> next = new ArrayList<>();
    private int callCount = 0;

    public void reset() {
        next = new ArrayList<>();
        callCount = 0;
    }

    /** 다음 호출부터 이 결과들을 돌려준다. */
    public void willReturn(PlaceSuggestion... suggestions) {
        this.next = new ArrayList<>(List.of(suggestions));
    }

    public int callCount() {
        return callCount;
    }

    @Override
    public List<PlaceSuggestion> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        callCount++;
        return List.copyOf(next);
    }
}
