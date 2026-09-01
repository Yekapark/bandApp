package com.yeka.bandapp.room.naver;

/**
 * WGS84 좌표.
 *
 * @param lat 위도 (네이버 지오코딩 응답의 {@code y})
 * @param lng 경도 (네이버 지오코딩 응답의 {@code x})
 */
public record Coordinates(double lat, double lng) {
}
