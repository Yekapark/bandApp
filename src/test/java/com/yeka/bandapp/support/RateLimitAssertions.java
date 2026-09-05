package com.yeka.bandapp.support;

import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 레이트리밋이 실제로 걸리는지 확인하는 공용 단언.
 *
 * <p><b>왜 필요한가</b> — {@code RedisRateLimiter} 는 {@code epochSecond / 60} 기준
 * <b>1분 고정 윈도우</b>다. 그래서 "상한 + 1 번째 요청이 429" 를 그대로 단언하거나 상한보다
 * 조금만 많이 던지는 테스트는, 루프가 분 경계를 넘는 순간 깨진다 — 카운터가 중간에 리셋돼
 * 어느 윈도우도 상한을 못 넘고 429 가 한 번도 안 난다. 실제로 이 저장소에서
 * {@code ReportIntegrationTest}, {@code MediaUploadIntegrationTest},
 * {@code AuthRateLimitIntegrationTest} 가 각각 이걸로 깨졌다.
 *
 * <p><b>해결</b> — 상한의 <b>2배 + 2회</b>를 던진다. 경계가 어디로 갈리든 한쪽 윈도우가
 * {@code ceil(N/2) > limit} 이라 429 가 산수로 보장된다. {@code sleep} 도 재시도도 없다.
 *
 * <p>Redis 는 매 테스트 전에 비워지므로({@link DatabaseCleaner}) 카운터는 항상 0 에서 시작한다.
 * 남는 변수는 분 경계 하나뿐이고, 그걸 위 산수가 덮는다.
 */
public final class RateLimitAssertions {

    private RateLimitAssertions() {
    }

    /**
     * {@code request} 를 상한의 2배 + 2회 호출하고, 그중 하나 이상이 429 인지 확인한다.
     *
     * @param limitPerMinute {@code IntegrationTestSupport} 가 그 버킷에 설정한 분당 상한
     * @param request        한 번 호출하고 HTTP 상태 코드를 돌려주는 람다
     * @return 실제로 429 가 난 횟수 (예산이 소진됐음을 이어서 단언하고 싶을 때 쓴다)
     */
    public static int assertRateLimited(int limitPerMinute, IntSupplier request) {
        int attempts = 2 * limitPerMinute + 2;
        int hits = 0;
        for (int i = 0; i < attempts; i++) {
            if (request.getAsInt() == 429) {
                hits++;
            }
        }
        assertThat(hits)
                .as("분당 상한 %d 인데 %d 번 시도해도 429 가 없다 — 레이트리밋이 안 걸린다",
                        limitPerMinute, attempts)
                .isPositive();
        return hits;
    }
}
