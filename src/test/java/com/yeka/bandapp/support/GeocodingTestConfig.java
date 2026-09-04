package com.yeka.bandapp.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 합주실 테스트에서 {@code @Import(GeocodingTestConfig.class)}로 가져온다.
 * {@code @Primary}라 실제 {@code NaverGeocodingClient}/{@code KakaoLocalSearchClient}보다 우선한다.
 */
@TestConfiguration
public class GeocodingTestConfig {

    @Bean
    @Primary
    FakeGeocodingClient fakeGeocodingClient() {
        return new FakeGeocodingClient();
    }

    @Bean
    @Primary
    FakePlaceSearchClient fakePlaceSearchClient() {
        return new FakePlaceSearchClient();
    }
}
