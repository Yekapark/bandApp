package com.yeka.bandapp.support;

import com.yeka.bandapp.room.naver.Coordinates;
import com.yeka.bandapp.room.naver.GeocodingClient;

import java.util.Optional;

/**
 * 프로그래머블 지오코딩 스텁. 실제 네이버 클라이언트처럼 <b>예외를 던지지 않고</b>
 * {@link Optional}로만 결과를 낸다 — "지오코딩 실패해도 등록은 성공"을 테스트가 검증할 수 있게 한다.
 *
 * <p>{@link #callCount()}로 호출 횟수를 노출해, "주소가 안 바뀌면 재호출하지 않는다" 같은 규칙도 확인한다.
 */
public class FakeGeocodingClient implements GeocodingClient {

    private Coordinates next = new Coordinates(37.5665, 126.9780); // 기본값: 서울시청
    private boolean willReturnEmpty = false;
    private int callCount = 0;

    public void reset() {
        next = new Coordinates(37.5665, 126.9780);
        willReturnEmpty = false;
        callCount = 0;
    }

    /** 다음 호출부터 이 좌표를 돌려준다. */
    public void willReturn(double lat, double lng) {
        this.next = new Coordinates(lat, lng);
        this.willReturnEmpty = false;
    }

    /** 다음 호출부터 좌표를 찾지 못한 것으로 처리한다(키 미설정·통신 실패·결과 0건과 동일). */
    public void willReturnNothing() {
        this.willReturnEmpty = true;
    }

    public int callCount() {
        return callCount;
    }

    @Override
    public Optional<Coordinates> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        callCount++;
        return willReturnEmpty ? Optional.empty() : Optional.of(next);
    }
}
