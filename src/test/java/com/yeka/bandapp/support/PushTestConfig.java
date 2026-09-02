package com.yeka.bandapp.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 알림 테스트에서 {@code @Import(PushTestConfig.class)}로 가져온다. {@code @Primary}라
 * {@code PushSender} 주입 지점에서 실제 {@code FcmPushSender}보다 우선한다({@code StorageTestConfig} 선례).
 */
@TestConfiguration
public class PushTestConfig {

    @Bean
    @Primary
    FakePushSender fakePushSender() {
        return new FakePushSender();
    }
}
