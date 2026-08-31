package com.yeka.bandapp.user.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.security.AccessTokenBlocklist;
import com.yeka.bandapp.common.security.JwtProperties;
import com.yeka.bandapp.common.security.RefreshTokenStore;
import com.yeka.bandapp.user.dto.UserResponse;
import com.yeka.bandapp.user.entity.User;
import com.yeka.bandapp.user.kakao.KakaoClient;
import com.yeka.bandapp.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 계정 조회·탈퇴, 그리고 보관기간 경과 계정의 개인정보 파기.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    /** 야간 배치가 커넥션을 오래 잡지 않도록 한 번에 처리하는 최대 건수. */
    public static final int PURGE_BATCH_SIZE = 500;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenBlocklist accessTokenBlocklist;
    private final JwtProperties jwtProperties;
    private final KakaoClient kakaoClient;

    public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              RefreshTokenStore refreshTokenStore, AccessTokenBlocklist accessTokenBlocklist,
                              JwtProperties jwtProperties, KakaoClient kakaoClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlocklist = accessTokenBlocklist;
        this.jwtProperties = jwtProperties;
        this.kakaoClient = kakaoClient;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(long userId) {
        return UserResponse.from(activeUser(userId));
    }

    /**
     * 탈퇴. 즉시 {@code deletedAt} 기록 + Redis 세션 전삭제 + access 차단목록 등재 + (소셜) 카카오 unlink.
     * <p>unlink 실패는 탈퇴를 막지 않는다 — 계정 삭제 불가는 스토어 심사 거절 사유다.
     */
    @Transactional
    public void withdraw(long userId, String rawPassword) {
        User user = activeUser(userId);
        if (user.isEmailAccount()
                && (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash()))) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String socialId = user.getSocialId();
        boolean social = !user.isEmailAccount();

        user.withdraw(Instant.now());
        refreshTokenStore.removeAll(userId);
        accessTokenBlocklist.block(userId, jwtProperties.accessTokenTtl());

        if (social && socialId != null) {
            try {
                kakaoClient.unlink(socialId);
            } catch (RuntimeException e) {
                log.error("카카오 unlink 실패, 탈퇴는 계속 진행 userId={} socialId={}", userId, socialId, e);
            }
        }
    }

    /**
     * {@code threshold} 이전에 탈퇴한 계정의 개인정보 컬럼을 파기한다. 최대 {@link #PURGE_BATCH_SIZE}건.
     * 익명화 후에는 조회 조건에서 자연히 빠지므로 멱등하다.
     *
     * @return 이번 호출에서 파기한 건수
     */
    @Transactional
    public int anonymizeWithdrawnBefore(Instant threshold) {
        List<User> targets = userRepository.findPurgeTargets(threshold, PageRequest.of(0, PURGE_BATCH_SIZE));
        targets.forEach(User::anonymize);
        return targets.size();
    }

    private User activeUser(long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
