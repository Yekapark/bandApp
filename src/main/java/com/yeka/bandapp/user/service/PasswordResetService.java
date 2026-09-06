package com.yeka.bandapp.user.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.mail.EmailSender;
import com.yeka.bandapp.common.security.RefreshTokenStore;
import com.yeka.bandapp.user.entity.User;
import com.yeka.bandapp.user.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

/**
 * 이메일 계정 비밀번호 재설정. 6자리 인증번호를 Redis에 임시 보관한다 — 재설정 이력이
 * 남을 필요가 없어 DB 테이블·마이그레이션 없이 refresh 토큰(§{@link RefreshTokenStore})과
 * 같은 방식으로 처리한다.
 *
 * <p>계정 존재 여부를 응답으로 드러내지 않는다({@link #request}는 항상 조용히 끝난다) —
 * {@link AuthService#login}이 이메일 열거를 막는 것과 같은 이유.
 */
@Service
public class PasswordResetService {

    private static final String CODE_KEY_PREFIX = "auth:pwreset:code:";
    private static final String ATTEMPTS_KEY_PREFIX = "auth:pwreset:attempts:";
    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final EmailSender emailSender;
    private final RefreshTokenStore refreshTokenStore;

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                StringRedisTemplate redis, EmailSender emailSender,
                                RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.emailSender = emailSender;
        this.refreshTokenStore = refreshTokenStore;
    }

    /**
     * 인증번호 발송. 이메일이 존재하지 않거나 소셜 계정이어도 예외 없이 조용히 끝난다(호출자는
     * 항상 204를 본다) — 계정 존재 여부를 노출하지 않는다.
     */
    public void request(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        userRepository.findByEmailAndSocialProviderIsNullAndDeletedAtIsNull(email).ifPresent(user -> {
            String code = generateCode();
            redis.opsForValue().set(codeKey(email), code, CODE_TTL);
            redis.delete(attemptsKey(email));
            emailSender.send(email, "[밴듈] 비밀번호 재설정 인증번호",
                    "인증번호는 " + code + " 입니다. 15분 안에 입력해 주세요.\n"
                            + "본인이 요청하지 않았다면 이 메일을 무시해도 됩니다.");
        });
    }

    /**
     * 인증번호 확인 + 비밀번호 변경. 성공하면 이 계정의 모든 refresh 세션을 무효화한다 —
     * 비밀번호가 새어 재설정하는 상황이라면 기존 세션도 함께 끊는 것이 안전하다.
     */
    @Transactional
    public void confirm(String rawEmail, String code, String newPassword) {
        String email = normalizeEmail(rawEmail);
        String storedCode = redis.opsForValue().get(codeKey(email));
        if (storedCode == null || !storedCode.equals(code)) {
            registerFailedAttempt(email);
            throw new BusinessException(ErrorCode.PASSWORD_RESET_CODE_INVALID);
        }
        User user = userRepository.findByEmailAndSocialProviderIsNullAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_CODE_INVALID));

        user.changePassword(passwordEncoder.encode(newPassword));
        redis.delete(codeKey(email));
        redis.delete(attemptsKey(email));
        refreshTokenStore.removeAll(user.getId());
    }

    /** 인증번호 오답이 {@link #MAX_ATTEMPTS}회 쌓이면 코드를 무효화한다(무차별 대입 방어). */
    private void registerFailedAttempt(String email) {
        String key = attemptsKey(email);
        Long attempts = redis.opsForValue().increment(key);
        redis.expire(key, CODE_TTL);
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redis.delete(codeKey(email));
            redis.delete(key);
        }
    }

    private static String generateCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private static String codeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }

    private static String attemptsKey(String email) {
        return ATTEMPTS_KEY_PREFIX + email;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
