package com.yeka.bandapp.board.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 신고 접수를 누구에게 알릴지.
 *
 * <p>이 앱에는 "운영자" 라는 역할이 따로 없다. 운영자 도구를 만드는 것은 아직 이르고,
 * 그때까지는 <b>운영자로 쓸 계정의 {@code users.id} 를 설정에 적어 두는 방식</b>으로 둔다.
 * 서버 설정이라 앱을 다시 빌드하지 않고 바꿀 수 있다.
 *
 * <p>비워 두면 알림이 나가지 않는다 — 신고 접수 자체는 그대로 동작한다. 로컬·테스트에서
 * 기본값이 이쪽이라, 설정을 안 넣었다고 개발이 막히지는 않는다.
 *
 * <p>메일 수신자는 <b>계정과 따로</b> 둔다. 푸시는 기기 토큰이 있어야 닿는데(앱을 깔고 로그인해야
 * 한다) 신고는 앱을 안 켜고 있어도 알아야 하고, 운영자가 앱 계정을 갖고 있으리라는 보장도 없다.
 * 그래서 주소를 직접 적는다.
 *
 * @param notifyUserIds 신고 접수 푸시를 받을 사용자 id 목록
 * @param notifyEmails  신고 접수 메일을 받을 주소 목록
 */
@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(List<Long> notifyUserIds, List<String> notifyEmails) {

    public ReportProperties {
        notifyUserIds = notifyUserIds == null ? List.of() : List.copyOf(notifyUserIds);
        notifyEmails = notifyEmails == null ? List.of()
                : notifyEmails.stream().map(String::trim).filter(e -> !e.isEmpty()).toList();
    }
}
