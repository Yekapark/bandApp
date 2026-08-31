package com.yeka.bandapp.user.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.security.JwtProperties;
import com.yeka.bandapp.common.security.JwtTokenProvider;
import com.yeka.bandapp.common.security.RefreshTokenStore;
import com.yeka.bandapp.common.security.TokenPair;
import com.yeka.bandapp.user.dto.AuthResponse;
import com.yeka.bandapp.user.dto.LoginRequest;
import com.yeka.bandapp.user.dto.SignupRequest;
import com.yeka.bandapp.user.dto.TokenResponse;
import com.yeka.bandapp.user.entity.SocialProvider;
import com.yeka.bandapp.user.entity.User;
import com.yeka.bandapp.user.kakao.KakaoClient;
import com.yeka.bandapp.user.kakao.KakaoProperties;
import com.yeka.bandapp.user.kakao.KakaoTokenInfo;
import com.yeka.bandapp.user.kakao.KakaoUserInfo;
import com.yeka.bandapp.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 가입·로그인·토큰 갱신·로그아웃. 토큰 발급 경로를 담당한다(계정 수명주기는 {@link UserAccountService}).
 */
@Service
public class AuthService {

    private static final int MAX_NAME_LENGTH = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;
    private final KakaoClient kakaoClient;
    private final KakaoProperties kakaoProperties;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, RefreshTokenStore refreshTokenStore,
                       JwtProperties jwtProperties, KakaoClient kakaoClient, KakaoProperties kakaoProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtProperties = jwtProperties;
        this.kakaoClient = kakaoClient;
        this.kakaoProperties = kakaoProperties;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmailAndSocialProviderIsNullAndDeletedAtIsNull(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        User user = userRepository.save(User.ofEmail(
                request.email(), passwordEncoder.encode(request.password()), request.name()));
        return AuthResponse.of(user, issue(user.getId()), true);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // 계정 존재 여부를 노출하지 않도록 이메일/비밀번호 실패를 구분하지 않는다.
        User user = userRepository.findByEmailAndSocialProviderIsNullAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return AuthResponse.of(user, issue(user.getId()), false);
    }

    @Transactional
    public AuthResponse kakaoLogin(String kakaoAccessToken) {
        KakaoTokenInfo tokenInfo = kakaoClient.fetchTokenInfo(kakaoAccessToken);
        if (tokenInfo.appId() == null
                || !String.valueOf(tokenInfo.appId()).equals(kakaoProperties.appId())) {
            throw new BusinessException(ErrorCode.KAKAO_APP_MISMATCH);
        }
        KakaoUserInfo info = kakaoClient.fetchUserInfo(kakaoAccessToken);
        if (info.id() == null || !info.id().equals(tokenInfo.id())) {
            throw new BusinessException(ErrorCode.KAKAO_TOKEN_INVALID);
        }

        Optional<User> existing = userRepository
                .findBySocialProviderAndSocialIdAndDeletedAtIsNull(SocialProvider.KAKAO, info.id());
        boolean newUser = existing.isEmpty();
        User user = existing.orElseGet(() -> userRepository.save(User.ofSocial(
                SocialProvider.KAKAO, info.id(), info.email(), resolveName(info.nickname(), info.id()))));
        return AuthResponse.of(user, issue(user.getId()), newUser);
    }

    public TokenResponse refresh(String refreshToken) {
        JwtTokenProvider.ParsedToken parsed = tokenProvider.parseRefresh(refreshToken);
        long userId = parsed.userId();
        if (!refreshTokenStore.exists(userId, parsed.jti())) {
            // 알 수 없는 jti = 이미 회전된 토큰의 재사용(탈취 정황) / 로그아웃됨 / 탈퇴함.
            // OAuth 2.0 BCP 권고대로 해당 사용자의 모든 세션을 끊는다.
            refreshTokenStore.removeAll(userId);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        TokenPair pair = tokenProvider.issue(userId);
        refreshTokenStore.rotate(userId, parsed.jti(), pair.refreshJti(), jwtProperties.refreshTokenTtl());
        return TokenResponse.from(pair);
    }

    public void logout(String refreshToken) {
        try {
            JwtTokenProvider.ParsedToken parsed = tokenProvider.parseRefresh(refreshToken);
            refreshTokenStore.remove(parsed.userId(), parsed.jti());
        } catch (BusinessException ignored) {
            // 이미 만료·무효한 토큰이면 정리할 것이 없다. 로그아웃은 멱등이다.
        }
    }

    private TokenPair issue(long userId) {
        TokenPair pair = tokenProvider.issue(userId);
        refreshTokenStore.save(userId, pair.refreshJti(), jwtProperties.refreshTokenTtl());
        return pair;
    }

    private String resolveName(String nickname, String socialId) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.length() > MAX_NAME_LENGTH ? nickname.substring(0, MAX_NAME_LENGTH) : nickname;
        }
        String suffix = socialId.length() >= 4 ? socialId.substring(socialId.length() - 4) : socialId;
        return "밴드원" + suffix;
    }
}
