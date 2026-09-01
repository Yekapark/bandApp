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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
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
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailAndSocialProviderIsNullAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        User user;
        try {
            user = userRepository.saveAndFlush(User.ofEmail(
                    email, passwordEncoder.encode(request.password()), request.name()));
        } catch (DataIntegrityViolationException e) {
            // 위 선검사와 INSERT 사이의 경합. 부분 유니크 인덱스 ux_users_email_active 가 최종 방어선이고,
            // 위반은 여기서 409 로 변환한다(변환이 없으면 공통 Exception 핸들러에 걸려 500).
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        return AuthResponse.of(user, issue(user.getId()), true);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // 계정 존재 여부를 노출하지 않도록 이메일/비밀번호 실패를 구분하지 않는다.
        User user = userRepository
                .findByEmailAndSocialProviderIsNullAndDeletedAtIsNull(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return AuthResponse.of(user, issue(user.getId()), false);
    }

    /**
     * 카카오 로그인. 카카오 API 왕복(외부 HTTP)은 트랜잭션 밖에서 먼저 끝내고, DB 는 find-or-create 만 한다.
     * 외부 I/O 를 트랜잭션 경계 안에 두지 않는다({@link UserAccountService#withdraw}의 unlink 처리와 같은 원칙).
     */
    public AuthResponse kakaoLogin(String kakaoAccessToken) {
        KakaoIdentity identity = fetchKakaoIdentity(kakaoAccessToken);

        Optional<User> existing = userRepository
                .findBySocialProviderAndSocialIdAndDeletedAtIsNull(SocialProvider.KAKAO, identity.id());
        if (existing.isPresent()) {
            User user = existing.get();
            return AuthResponse.of(user, issue(user.getId()), false);
        }
        try {
            User created = userRepository.saveAndFlush(User.ofSocial(SocialProvider.KAKAO, identity.id(),
                    normalizeEmail(identity.email()), resolveName(identity.nickname(), identity.id())));
            return AuthResponse.of(created, issue(created.getId()), true);
        } catch (DataIntegrityViolationException race) {
            // 같은 카카오 계정의 동시 최초 로그인 — 다른 요청이 먼저 INSERT 했다.
            // ux_users_social_active 가 최종 방어선이고, 위반은 여기서 그 계정으로 이어간다(없으면 500).
            User now = userRepository
                    .findBySocialProviderAndSocialIdAndDeletedAtIsNull(SocialProvider.KAKAO, identity.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.KAKAO_TOKEN_INVALID));
            return AuthResponse.of(now, issue(now.getId()), false);
        }
    }

    public TokenResponse refresh(String refreshToken) {
        JwtTokenProvider.ParsedToken parsed = tokenProvider.parseRefresh(refreshToken);
        long userId = parsed.userId();
        String jti = parsed.jti();

        if (!refreshTokenStore.exists(userId, jti)) {
            // 방금 이 토큰으로 갱신한 결과가 아직 캐시돼 있으면 재시도·더블탭·탭 중복으로 보고
            // 같은 응답을 다시 돌려준다(멱등). 세션은 건드리지 않는다.
            Optional<TokenResponse> replay = refreshTokenStore.recallRotation(userId, jti)
                    .map(this::decodeReplay);
            if (replay.isPresent()) {
                return replay.get();
            }
            // 활성도 아니고 방금 회전된 것도 아니다 = 이미 회전된 토큰의 재사용(탈취 정황) / 로그아웃 / 탈퇴.
            // OAuth 2.0 BCP 권고대로 해당 사용자의 모든 세션을 끊는다.
            refreshTokenStore.removeAll(userId);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        TokenPair pair = tokenProvider.issue(userId);
        refreshTokenStore.rotate(userId, jti, pair.refreshJti(), jwtProperties.refreshTokenTtl());
        refreshTokenStore.rememberRotation(userId, jti, encodeReplay(pair));
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

    /** 카카오 토큰·사용자 정보 조회(외부 HTTP)와 그 검증만 담당한다. DB·트랜잭션과 무관하게 먼저 끝낸다. */
    private KakaoIdentity fetchKakaoIdentity(String kakaoAccessToken) {
        KakaoTokenInfo tokenInfo = kakaoClient.fetchTokenInfo(kakaoAccessToken);
        if (tokenInfo.appId() == null
                || !String.valueOf(tokenInfo.appId()).equals(kakaoProperties.appId())) {
            throw new BusinessException(ErrorCode.KAKAO_APP_MISMATCH);
        }
        KakaoUserInfo info = kakaoClient.fetchUserInfo(kakaoAccessToken);
        if (info.id() == null || !info.id().equals(tokenInfo.id())) {
            throw new BusinessException(ErrorCode.KAKAO_TOKEN_INVALID);
        }
        return new KakaoIdentity(info.id(), info.email(), info.nickname());
    }

    /** 이메일은 자격증명·표시값이라 대소문자·앞뒤 공백을 정규화해 저장·조회한다(별개 계정 난립 방지). */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // refresh 회전 재시도(A2) 캐시 직렬화: JWT 는 base64url 이라 탭을 포함하지 않는다.
    private static final char REPLAY_SEP = '\t';

    private String encodeReplay(TokenPair pair) {
        return pair.accessToken() + REPLAY_SEP + pair.refreshToken();
    }

    private TokenResponse decodeReplay(String payload) {
        int sep = payload.indexOf(REPLAY_SEP);
        return new TokenResponse(payload.substring(0, sep), payload.substring(sep + 1),
                "Bearer", jwtProperties.accessTokenTtl().toSeconds());
    }

    private record KakaoIdentity(String id, String email, String nickname) {
    }
}
