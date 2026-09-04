package com.yeka.bandapp.room.dto;

import com.yeka.bandapp.room.place.PlaceSuggestion;

import java.util.List;

/**
 * 합주실 주소 검색 결과. 카카오 로컬 검색 후보를 최대 5건 담는다. 검색 키가 없거나 결과가 없으면
 * {@code places}는 빈 목록이다({@code placeCount == 0}).
 */
public record PlaceSearchResponse(String query, int placeCount, List<PlaceItem> places) {

    public static PlaceSearchResponse of(String query, List<PlaceSuggestion> suggestions) {
        List<PlaceItem> items = suggestions.stream().map(PlaceItem::from).toList();
        return new PlaceSearchResponse(query, items.size(), items);
    }

    /** 후보 한 건. {@code lat}/{@code lng}는 좌표를 얻지 못했으면 {@code null}. */
    public record PlaceItem(
            String name,
            String roadAddress,
            String address,
            String category,
            String phone,
            Double lat,
            Double lng
    ) {
        static PlaceItem from(PlaceSuggestion s) {
            return new PlaceItem(
                    s.name(), s.roadAddress(), s.address(), s.category(), s.phone(), s.lat(), s.lng());
        }
    }
}
