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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
     *
     * <p>이미 보낸 인증번호가 살아 있으면 <b>새로 만들지 않고 같은 번호를 다시 보낸다.</b> 예전엔 요청마다
     * 번호를 갈고 오답 횟수를 지웠다. 그러면 (1) 요청 + 4번 대입을 되풀이해 5회 상한을 무시하고 끝없이
     * 맞혀 볼 수 있었고, (2) 주소당 메일 상한({@code EmailSender})에 걸려 메일이 안 나간 요청도 번호를
     * 바꿔, 본인이 받은 메일 속 번호가 이미 무효가 됐다.
     * 오답 소진으로 잠긴 동안에는 쓸 수 없는 번호를 보내지 않는다.
     */
    public void request(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        userRepository.findByEmailAndSocialProviderIsNullAndDeletedAtIsNull(email).ifPresent(user -> {
            if (isLocked(email)) {
                return;
            }
            String code = redis.opsForValue().get(codeKey(email));
            if (code == null) {
                code = generateCode();
                // 새로 만들 때만 TTL 을 건다 — 재요청으로 같은 번호의 수명을 끝없이 늘리지 못하게.
                redis.opsForValue().set(codeKey(email), code, CODE_TTL);
            }
            emailSender.send(email, "[밴듈] 비밀번호 재설정 인증번호",
                    "인증번호는 " + code + " 입니다. 15분 안에 입력해 주세요.\n"
                            + "본인이 요청하지 않았다면 이 메일을 무시해도 됩니다.");
        });
    }

    /**
     * 인증번호 확인 + 비밀번호 변경. 성공하면 이 계정의 모든 refresh 세션을 무효화한다 —
     * 비밀번호가 새어 재설정하는 상황이라면 기존 세션도 함께 끊는 것이 안전하다.
     *
     * <p>시도는 <b>비교하기 전에</b> 센다. 비교 뒤에 세면 동시에 쏜 요청들이 모두 "아직 0회" 를 보고
     * 통과해 5회 상한을 넘겨 대입할 수 있다. 상한을 넘기면 {@link #CODE_TTL} 동안 잠기고
     * 429 TOO_MANY_REQUESTS("잠시 후 다시") 로 답한다 — 없는 이메일에도 똑같이 세므로 계정 존재는 안 샌다.
     */
    @Transactional
    public void confirm(String rawEmail, String code, String newPassword) {
        String email = normalizeEmail(rawEmail);
        long attempts = countAttempt(email);
        if (attempts > MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        String storedCode = redis.opsForValue().get(codeKey(email));
        if (storedCode == null || code == null || !MessageDigest.isEqual(
                storedCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
            if (attempts >= MAX_ATTEMPTS) {
                redis.delete(codeKey(email));
            }
            throw new BusinessException(ErrorCode.PASSWORD_RESET_CODE_INVALID);
        }
        User user = userRepository.findByEmailAndSocialProviderIsNullAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_CODE_INVALID));

        user.changePassword(passwordEncoder.encode(newPassword));
        redis.delete(codeKey(email));
        redis.delete(attemptsKey(email));
        refreshTokenStore.removeAll(user.getId());
    }

    /** 이번 시도를 포함한 횟수. 창은 첫 시도부터 {@link #CODE_TTL} — 키와 TTL 을 한 번에 건다(TTL 없는 좀비 키 방지). */
    private long countAttempt(String email) {
        String key = attemptsKey(email);
        redis.opsForValue().setIfAbsent(key, "0", CODE_TTL);
        Long attempts = redis.opsForValue().increment(key);
        return attempts == null ? Long.MAX_VALUE : attempts;
    }

    private boolean isLocked(String email) {
        String attempts = redis.opsForValue().get(attemptsKey(email));
        return attempts != null && Long.parseLong(attempts) >= MAX_ATTEMPTS;
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
