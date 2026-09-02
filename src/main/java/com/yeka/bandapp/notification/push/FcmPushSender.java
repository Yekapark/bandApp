package com.yeka.bandapp.notification.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link PushSender}의 FCM 구현.
 *
 * <p>자격증명({@code app.fcm.*})이 없으면 {@code firebaseMessaging}을 만들지 않고 조용히 뜬다 —
 * {@link #send}는 아무 일도 하지 않고 {@link #isConfigured()}가 {@code false}다({@code R2StorageClient} 선례).
 * 알림은 부가 기능이라 미설정이 다른 기능을 깨서는 안 되므로 예외를 던지지 않는다.
 *
 * <p>발송은 {@code sendEachForMulticast}(1회 최대 {@value TokenChunks#MAX_PER_MULTICAST}개)로 청크 처리하고,
 * FCM 이 무효라고 응답한 토큰({@code UNREGISTERED}/{@code INVALID_ARGUMENT})을 {@link PushResult#invalidTokens()}에
 * 담아 돌려준다 — 저장소 정리는 호출 측이 한다.
 */
@Component
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String APP_NAME = "bandapp-fcm";

    private final FirebaseApp firebaseApp;
    private final FirebaseMessaging firebaseMessaging;

    public FcmPushSender(FcmProperties properties) {
        if (!properties.isConfigured()) {
            this.firebaseApp = null;
            this.firebaseMessaging = null;
            log.info("FCM 자격증명이 없어 푸시 발송은 비활성 상태로 뜬다(알림 설정·토큰 API 는 정상).");
            return;
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(loadCredentials(properties))
                    .setProjectId(properties.projectId())
                    .setConnectTimeout((int) properties.connectTimeout().toMillis())
                    .setReadTimeout((int) properties.readTimeout().toMillis())
                    .build();
            this.firebaseApp = FirebaseApp.getApps().stream()
                    .filter(app -> APP_NAME.equals(app.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
            this.firebaseMessaging = FirebaseMessaging.getInstance(firebaseApp);
            log.info("FCM 푸시 발송 활성화 projectId={}", properties.projectId());
        } catch (IOException e) {
            throw new IllegalStateException("FCM 서비스 계정 자격증명을 읽지 못했습니다.", e);
        }
    }

    private static GoogleCredentials loadCredentials(FcmProperties properties) throws IOException {
        if (properties.hasCredentialsFile()) {
            try (FileInputStream in = new FileInputStream(properties.credentialsPath())) {
                return GoogleCredentials.fromStream(in);
            }
        }
        return GoogleCredentials.fromStream(
                new ByteArrayInputStream(properties.credentialsJson().getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public boolean isConfigured() {
        return firebaseMessaging != null;
    }

    @Override
    public PushResult send(PushMessage message, List<String> tokens) {
        if (firebaseMessaging == null || tokens.isEmpty()) {
            return PushResult.empty();
        }
        int success = 0;
        List<String> invalid = new ArrayList<>();
        for (List<String> chunk : TokenChunks.of(tokens, TokenChunks.MAX_PER_MULTICAST)) {
            MulticastMessage multicast = MulticastMessage.builder()
                    .setNotification(Notification.builder()
                            .setTitle(message.title())
                            .setBody(message.body())
                            .build())
                    .putAllData(message.data())
                    .addAllTokens(chunk)
                    .build();
            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(multicast);
                success += response.getSuccessCount();
                collectInvalid(chunk, response.getResponses(), invalid);
            } catch (FirebaseMessagingException e) {
                // 청크 단위 실패는 넘어간다 — 다음 배치 실행이 재시도한다(멱등은 dispatch 이력이 보장).
                log.warn("FCM 멀티캐스트 실패 tokens={}", chunk.size(), e);
            }
        }
        return new PushResult(success, invalid);
    }

    private static void collectInvalid(List<String> chunk, List<SendResponse> responses, List<String> invalid) {
        for (int i = 0; i < responses.size(); i++) {
            SendResponse response = responses.get(i);
            if (response.isSuccessful() || response.getException() == null) {
                continue;
            }
            MessagingErrorCode code = response.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                invalid.add(chunk.get(i));
            }
        }
    }

    @PreDestroy
    void close() {
        if (firebaseApp != null) {
            firebaseApp.delete();
        }
    }
}
