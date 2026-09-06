package com.yeka.bandapp.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정·이메일 인증 코드를 보내는 얇은 래퍼. {@link MailProperties#from()}이 비어 있으면
 * 발송을 건너뛰고 로그만 남긴다(카카오·FCM·R2 선례와 같은 방식) — 발신 계정이 없는 로컬 개발·CI가
 * 이 기능 때문에 막히지 않는다.
 *
 * <p>발송 실패(SMTP 오류)는 예외를 던지지 않는다. 인증번호 발송은 부가 기능이라, 실패해도 이미
 * 진행된 가입·재설정 요청 자체를 막을 이유가 없다 — 사용자는 재발송을 다시 시도하면 된다.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public EmailSender(ObjectProvider<JavaMailSender> mailSenderProvider, MailProperties mailProperties) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.mailProperties = mailProperties;
    }

    public boolean isConfigured() {
        return mailSender != null && mailProperties.isConfigured();
    }

    public void send(String to, String subject, String text) {
        if (!isConfigured()) {
            log.warn("[email] 발신 계정 미설정 — 발송 건너뜀 subject={}", subject);
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
}
