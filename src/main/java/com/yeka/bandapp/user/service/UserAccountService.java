package com.yeka.bandapp.user.service;

import com.yeka.bandapp.band.service.BandMemberService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final BandMemberService bandMemberService;

    public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              RefreshTokenStore refreshTokenStore, AccessTokenBlocklist accessTokenBlocklist,
                              JwtProperties jwtProperties, KakaoClient kakaoClient,
                              BandMemberService bandMemberService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlocklist = accessTokenBlocklist;
        this.jwtProperties = jwtProperties;
        this.kakaoClient = kakaoClient;
        this.bandMemberService = bandMemberService;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(long userId) {
        return UserResponse.from(activeUser(userId));
    }

    /**
     * 탈퇴. 즉시 {@code deletedAt} 기록 + 소속 밴드 정리 + Redis 세션 전삭제 + access 차단목록 등재
     * + (소셜) 카카오 unlink.
     * <p>unlink 실패는 탈퇴를 막지 않는다 — 계정 삭제 불가는 스토어 심사 거절 사유다.
     * <p>밴드 정리({@link BandMemberService#handleAccountWithdrawal})는 같은 트랜잭션이라 실패 시 탈퇴 전체가 롤백된다.
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
        Instant now = Instant.now();

        user.withdraw(now);
        bandMemberService.handleAccountWithdrawal(userId, now);
        refreshTokenStore.removeAll(userId);
        accessTokenBlocklist.block(userId, jwtProperties.accessTokenTtl());

        if (social && socialId != null) {
            unlinkKakaoAfterCommit(userId, socialId);
        }
    }

    /**
     * 카카오 unlink 는 외부 HTTP(최대 connect+read 5s)라 트랜잭션 커밋 뒤로 미룬다 — DB 커넥션을
     * 그동안 붙잡지 않기 위해서다. 실패해도 이미 커밋된 탈퇴를 되돌리지 않는다(스토어 심사 요건).
     */
    private void unlinkKakaoAfterCommit(long userId, String socialId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doUnlink(userId, socialId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doUnlink(userId, socialId);
            }
        });
    }

    private void doUnlink(long userId, String socialId) {
        try {
            kakaoClient.unlink(socialId);
        } catch (RuntimeException e) {
            log.error("카카오 unlink 실패, 탈퇴는 이미 완료됨 userId={} socialId={}", userId, socialId, e);
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
