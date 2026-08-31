package com.yeka.bandapp.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 카카오 연동 테스트에서 {@code @Import(KakaoTestConfig.class)}로 가져온다.
 * {@code @Primary}라 {@code KakaoClient} 주입 지점에서 실제 {@code KakaoApiClient}보다 우선한다.
 */
@TestConfiguration
public class KakaoTestConfig {

    @Bean
    @Primary
    FakeKakaoClient fakeKakaoClient() {
        return new FakeKakaoClient();
    }
}
