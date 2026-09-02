package com.yeka.bandapp.board;

import com.yeka.bandapp.board.storage.StorageClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 완료 기준 ① — 백엔드를 경유하는 파일 스트림이 코드상 존재하지 않는다.
 * Docker 불필요. 소스 전체를 훑어 멀티파트 업로드/스트림 응답 마커가 없음을, 그리고 저장소 경계
 * 인터페이스에 바이트를 나르는 메서드가 없음을 단언한다.
 */
class NoFileStreamArchitectureTest {

    /** 이 문자열이 소스에 있으면 파일이 서버를 거쳐 오간다는 뜻이다. */
    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "MultipartFile",
            "@RequestPart",
            "multipart/form-data",
            "MULTIPART_FORM_DATA",
            "StreamingResponseBody",
            "InputStreamResource",
            "ServletOutputStream");

    @Test
    void no_source_file_streams_files_through_the_backend() {
        Path root = Paths.get("src", "main", "java");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String source;
                try {
                    source = Files.readString(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                for (String marker : FORBIDDEN_MARKERS) {
                    if (source.contains(marker)) {
                        offenders.add(p + " → " + marker);
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(offenders)
                .as("파일 스트림 마커가 발견됨 — 미디어는 presigned URL 로만 오가야 한다")
                .isEmpty();
    }

    @Test
    void storage_client_boundary_carries_no_bytes() {
        for (Method method : StorageClient.class.getDeclaredMethods()) {
            assertThat(carriesBytes(method.getReturnType()))
                    .as("StorageClient.%s 반환 타입이 바이트를 나른다", method.getName())
                    .isFalse();
            for (Class<?> param : method.getParameterTypes()) {
                assertThat(carriesBytes(param))
                        .as("StorageClient.%s 파라미터가 바이트를 나른다", method.getName())
                        .isFalse();
            }
        }
    }

    private static boolean carriesBytes(Class<?> type) {
        return type == byte[].class
                || InputStream.class.isAssignableFrom(type)
                || java.io.File.class.isAssignableFrom(type)
                || org.springframework.core.io.Resource.class.isAssignableFrom(type);
    }
}
