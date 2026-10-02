package com.yeka.bandapp.board.storage;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * presigned PUT 에 Content-Length 가 서명돼야 한다 — 빠지면 같은 URL 로 신고보다 훨씬 큰 파일을 올리거나,
 * 완료 확인 뒤 URL 만료 전에 덮어쓸 수 있다(저장 비용). 서명은 오프라인이라 가짜 키로 네트워크 없이 확인한다.
 */
class R2StorageClientPresignTest {

    @Test
    void presigned_put_signs_content_type_and_content_length() {
        R2StorageClient client = new R2StorageClient(new R2Properties(
                "acct", "bucket", "AKIDEXAMPLE", "secret", null, null, null, null, null));
        try {
            String url = URLDecoder.decode(client.presignPut("bands/1/posts/2/x", "image/jpeg", 1234,
                    Duration.ofMinutes(15)).toString(), StandardCharsets.UTF_8);

            assertThat(url).containsPattern("X-Amz-SignedHeaders=[^&]*content-length");
            assertThat(url).containsPattern("X-Amz-SignedHeaders=[^&]*content-type");
        } finally {
            client.close();
        }
    }
}
