package com.yeka.bandapp.notification.push;

import java.util.ArrayList;
import java.util.List;

/**
 * 토큰 목록을 FCM 멀티캐스트 한계(1회 500개)로 자르는 순수 함수. Docker 없이 단위 테스트한다.
 */
public final class TokenChunks {

    /** FCM {@code sendEachForMulticast} 한 번에 담을 수 있는 최대 토큰 수. */
    public static final int MAX_PER_MULTICAST = 500;

    private TokenChunks() {
    }

    public static List<List<String>> of(List<String> tokens, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += chunkSize) {
            chunks.add(List.copyOf(tokens.subList(i, Math.min(i + chunkSize, tokens.size()))));
        }
        return chunks;
    }
}
