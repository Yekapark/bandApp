package com.yeka.bandapp.support;

import com.yeka.bandapp.notification.push.PushMessage;
import com.yeka.bandapp.notification.push.PushResult;
import com.yeka.bandapp.notification.push.PushSender;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 인메모리 FCM 스텁. 실제 발송 대신 호출을 기록한다. {@code FakeStorageClient}와 같은 스타일의
 * 프로그래머블 스파이 — 상태 심기({@link #markInvalid}), 실패 주입({@link #failNext}),
 * 검증용 노출({@link #sent()}·{@link #allTokens()}).
 *
 * <p>컨테이너 빈이라 테스트 간 상태가 남으므로 {@code @BeforeEach}에서 {@link #reset()}을 부른다.
 */
public class FakePushSender implements PushSender {

    /** 한 번의 {@link #send} 호출 기록. */
    public record Sent(PushMessage message, List<String> tokens) {
    }

    private final List<Sent> sent = new ArrayList<>();
    private final Set<String> invalidTokens = new HashSet<>();
    private boolean configured = true;
    private boolean failNext = false;

    public void reset() {
        sent.clear();
        invalidTokens.clear();
        configured = true;
        failNext = false;
    }

    /** {@link #isConfigured()}가 false 를 반환하게 한다(FCM 키 미설정 시나리오). */
    public void markNotConfigured() {
        this.configured = false;
    }

    /** 이 토큰을 다음 {@link #send}부터 무효로 응답한다(호출 측이 저장소에서 지우는지 검증). */
    public void markInvalid(String token) {
        this.invalidTokens.add(token);
    }

    /** 다음 {@link #send} 호출이 전송 실패로 터지게 한다. */
    public void failNext() {
        this.failNext = true;
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public int sentCount() {
        return sent.size();
    }

    /** 지금까지 발송된 모든 토큰(중복 포함). */
    public List<String> allTokens() {
        return sent.stream().flatMap(s -> s.tokens().stream()).toList();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public PushResult send(PushMessage message, List<String> tokens) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("FCM 전송 실패(테스트 주입)");
        }
        sent.add(new Sent(message, List.copyOf(tokens)));
        List<String> invalid = tokens.stream().filter(invalidTokens::contains).toList();
        return new PushResult(tokens.size() - invalid.size(), invalid);
    }
}
