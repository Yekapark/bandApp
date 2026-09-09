package com.yeka.bandapp.common.mail;

import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 비밀번호 재설정·이메일 인증 코드를 보내는 얇은 래퍼. {@link MailProperties#from()}이 비어 있으면
 * 발송을 건너뛰고 로그만 남긴다(카카오·FCM·R2 선례와 같은 방식) — 발신 계정이 없는 로컬 개발·CI가
 * 이 기능 때문에 막히지 않는다.
 *
 * <p>발송 실패(SMTP 오류)는 예외를 던지지 않는다. 인증번호 발송은 부가 기능이라, 실패해도 이미
 * 진행된 가입·재설정 요청 자체를 막을 이유가 없다 — 사용자는 재발송을 다시 시도하면 된다.
 *
 * <p><b>받는 주소마다 분당 상한을 건다</b>({@code app.ratelimit.email-per-address-per-min}).
 * 메일을 보내는 경로가 넷(가입 인증·재발송·비밀번호 재설정·신고 알림)인데 각자 다른 기준으로
 * 제한돼 있었고, 특히 비밀번호 재설정 요청은 IP 당 분당 20회라 <b>남의 주소로 분당 20통</b>을
 * 쏠 수 있었다. 여기가 모든 발송이 지나는 유일한 길목이라 한 곳만 막으면 네 경로가 다 덮인다.
 *
 * <p>상한을 넘으면 <b>예외를 던지지 않고 조용히 건너뛴다.</b> 429 를 돌려주면
 * {@code PasswordResetService.request} 가 "이 주소는 가입돼 있다"를 알려 주는 꼴이 된다 —
 * 그 메서드가 계정 존재 여부를 숨기려고 일부러 항상 조용히 끝나는데, 그 노력이 무너진다.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private static final String RATE_LIMIT_BUCKET = "email:address";

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public EmailSender(ObjectProvider<JavaMailSender> mailSenderProvider, MailProperties mailProperties,
                       RedisRateLimiter rateLimiter, RateLimitProperties rateLimitProperties) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.mailProperties = mailProperties;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    public boolean isConfigured() {
        return mailSender != null && mailProperties.isConfigured();
    }

    public void send(String to, String subject, String text) {
        if (!isConfigured()) {
            log.warn("[email] 발신 계정 미설정 — 발송 건너뜀 subject={}", subject);
            return;
        }
        // check() 가 아니라 tryAcquire() — 초과를 예외로 알리면 계정 존재 여부가 샌다(위 주석).
        if (!rateLimiter.tryAcquire(RATE_LIMIT_BUCKET, bucketKey(to),
                rateLimitProperties.emailPerAddressPerMin())) {
            log.warn("[email] 주소당 분당 상한 초과 — 발송 건너뜀 subject={}", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (MailException e) {
            log.error("[email] 발송 실패 subject={}", subject, e);
        }
    }

    /** 대소문자·앞뒤 공백이 달라도 같은 주소로 세도록 정규화한다(안 하면 상한을 우회할 수 있다). */
    private static String bucketKey(String to) {
        return to == null ? "unknown" : to.trim().toLowerCase(Locale.ROOT);
    }
}
