package com.yeka.bandapp.user.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.mail.EmailSender;
import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import com.yeka.bandapp.user.entity.User;
import com.yeka.bandapp.user.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 이메일 계정 가입자의 이메일 인증. 강제하지 않는다 — 미인증이어도 모든 기능을 그대로 쓸 수 있고,
 * 클라이언트가 배너로만 안내한다({@code BUILD_PLAN} 배포 전 체크리스트, 2026-09-06 결정).
 * 소셜(카카오) 가입자는 {@link User#ofSocial}에서 이미 인증된 상태로 생성되므로 이 서비스가
 * 관여하지 않는다.
 */
@Service
public class EmailVerificationService {

    private static final String CODE_KEY_PREFIX = "auth:emailverify:code:";
    private static final String ATTEMPTS_KEY_PREFIX = "auth:emailverify:attempts:";
    private static final Duration CODE_TTL = Duration.ofHours(24);
    private static final int MAX_ATTEMPTS = 5;
    private static final String RATE_LIMIT_BUCKET = "email-verification:user";

    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final EmailSender emailSender;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public EmailVerificationService(UserRepository userRepository, StringRedisTemplate redis,
                                    EmailSender emailSender, RedisRateLimiter rateLimiter,
                                    RateLimitProperties rateLimitProperties) {
        this.userRepository = userRepository;
        this.redis = redis;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * 가입 직후 자동 발송(레이트리밋 없이 내부에서만 호출) 및 재발송 공용 진입점. 이미 인증됐거나
     * 이메일이 없는 계정(소셜 미동의)이면 조용히 끝난다.
     */
    public void sendVerification(long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(user -> !user.isEmailVerified() && user.getEmail() != null)
                .ifPresent(user -> {
                    String code = generateCode();
                    redis.opsForValue().set(codeKey(userId), code, CODE_TTL);
                    redis.delete(attemptsKey(userId));
                    emailSender.send(user.getEmail(), "[밴듈] 이메일 인증번호",
                            "인증번호는 " + code + " 입니다. 24시간 안에 입력해 주세요.");
                });
    }

    /** 사용자가 직접 누르는 재발송 버튼 — 계정당 분당 상한을 건다. */
    public void resend(long userId) {
        rateLimiter.check(RATE_LIMIT_BUCKET, String.valueOf(userId),
                rateLimitProperties.emailVerificationPerUserPerMin());
        sendVerification(userId);
    }

    @Transactional
    public void confirm(long userId, String code) {
        String key = codeKey(userId);
        String storedCode = redis.opsForValue().get(key);
        if (storedCode == null || !storedCode.equals(code)) {
            registerFailedAttempt(userId);
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.verifyEmail();
        redis.delete(key);
        redis.delete(attemptsKey(userId));
    }

    private void registerFailedAttempt(long userId) {
        String key = attemptsKey(userId);
        Long attempts = redis.opsForValue().increment(key);
        redis.expire(key, CODE_TTL);
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redis.delete(codeKey(userId));
            redis.delete(key);
        }
    }

    private static String generateCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private static String codeKey(long userId) {
        return CODE_KEY_PREFIX + userId;
    }

    private static String attemptsKey(long userId) {
        return ATTEMPTS_KEY_PREFIX + userId;
    }
}
