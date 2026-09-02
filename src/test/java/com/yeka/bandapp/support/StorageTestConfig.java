package com.yeka.bandapp.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 게시판 미디어 테스트에서 {@code @Import(StorageTestConfig.class)}로 가져온다.
 * {@code @Primary}라 {@code StorageClient} 주입 지점에서 실제 {@code R2StorageClient}보다 우선한다.
 */
@TestConfiguration
public class StorageTestConfig {

    @Bean
    @Primary
    FakeStorageClient fakeStorageClient() {
        return new FakeStorageClient();
    }
}
