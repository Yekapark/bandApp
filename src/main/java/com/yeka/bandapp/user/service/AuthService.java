package com.yeka.bandapp.user.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.security.JwtProperties;
import com.yeka.bandapp.common.security.JwtTokenProvider;
import com.yeka.bandapp.common.security.RefreshTokenStore;
import com.yeka.bandapp.common.security.TokenPair;
import com.yeka.bandapp.notification.service.DeviceTokenService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/**
 * 가입·로그인·토큰 갱신·로그아웃. 토큰 발급 경로를 담당한다(계정 수명주기는 {@link UserAccountService}).
 */
@Service
public class AuthService {

    private static final int MAX_NAME_LENGTH = 30;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 이 해 이후까지면 영구 정지로 본다(운영 스크립트는 9999-12-31 을 쓴다). */
    private static final int PERMANENT_FROM_YEAR = 9000;
    /** 정지 안내에 적는 이의 제기 창구 — 약관 제14조·개인정보처리방침의 연락처와 같다. */
    static final String APPEAL_CONTACT = "notice@bandule.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;
    private final KakaoClient kakaoClient;
    private final KakaoProperties kakaoProperties;
    /** 가입 시점에 동의 사실을 남긴다 — 앱의 동의 화면을 두 가입 경로가 모두 거친다. */
    private final TermsAgreementService termsAgreements;
    private final DeviceTokenService deviceTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, RefreshTokenStore refreshTokenStore,
                       JwtProperties jwtProperties, KakaoClient kakaoClient, KakaoProperties kakaoProperties,
                       TermsAgreementService termsAgreements, DeviceTokenService deviceTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtProperties = jwtProperties;
        this.kakaoClient = kakaoClient;
        this.kakaoProperties = kakaoProperties;
        this.termsAgreements = termsAgreements;
        this.deviceTokenService = deviceTokenService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        // 반송이 확정된 예약 도메인(example.com, .test …)은 계정을 만들지 않는다 — 받을 수 없는
        // 주소는 비밀번호 재설정도 못 받아 사실상 잠긴 계정이 된다(EmailPolicy 주석).
        EmailPolicy.requireDeliverable(email);
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
        termsAgreements.record(user.getId(), Instant.now());
        // 가입 시 인증 메일을 보내지 않는다. 인증을 강제하지도 않고, 코드를 입력할 화면도
        // 없어서 쓸 데가 없었다(반송만 쌓였다). "이메일 인증" 자체는 UserController 의
        // resend/confirm 에 남겨 뒀다 — 앱에서 필요해지면 그때 화면과 함께 되살린다.
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
        // 비밀번호가 맞은 뒤에만 알린다 — 틀린 비밀번호로 정지 여부를 캐낼 수 없게.
        requireNotSuspended(user);
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
            requireNotSuspended(user);
            return AuthResponse.of(user, issue(user.getId()), false);
        }
        try {
            User created = userRepository.saveAndFlush(User.ofSocial(SocialProvider.KAKAO, identity.id(),
                    normalizeEmail(identity.email()), resolveName(identity.nickname(), identity.id())));
            termsAgreements.record(created.getId(), Instant.now());
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

        // 정지는 운영자가 DB 에 적는다(tools/moderate.py). 갱신 때마다 확인해 두면 늦어도 access 토큰 수명(30분)
        // 안에 막힌다 — 스크립트가 차단 목록에도 올리므로 보통은 즉시다. 남은 세션도 모두 지운다.
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.isSuspendedAt(Instant.now())) {
            refreshTokenStore.removeAll(userId);
            throw suspended(user);
        }

        TokenPair pair = tokenProvider.issue(userId);
        return refreshTokenStore.rotate(userId, jti, pair.refreshJti(), jwtProperties.refreshTokenTtl(),
                        encodeReplay(pair))
                .map(this::decodeReplay)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    /**
     * 로그아웃 — 세션(refresh) 정리 + 이 기기의 푸시 토큰 정리.
     *
     * <p>푸시 토큰을 여기서 지우는 이유 — 예전에는 앱이 로그아웃 뒤에 인증이 필요한
     * {@code DELETE /device-tokens} 를 불렀는데, 그땐 이미 토큰을 지운 뒤라 401 이 나고 서버에 토큰이 남았다.
     * 공용 폰이면 로그아웃한 사람의 일정·정산 알림이 다음 사람에게 보였다(LAUNCH_REVIEW U1). 로그아웃 요청 자체에
     * 실어 보내면 인증 순서와 상관없이 지워진다. refresh 토큰이 이미 만료된 강제 로그아웃도 같다.
     */
    public void logout(String refreshToken, String deviceToken) {
        try {
            JwtTokenProvider.ParsedToken parsed = tokenProvider.parseRefresh(refreshToken);
            refreshTokenStore.remove(parsed.userId(), parsed.jti());
        } catch (BusinessException ignored) {
            // 이미 만료·무효한 토큰이면 정리할 것이 없다. 로그아웃은 멱등이다.
        }
        deviceTokenService.forgetDevice(deviceToken);
    }

    private static void requireNotSuspended(User user) {
        if (user.isSuspendedAt(Instant.now())) {
            throw suspended(user);
        }
    }

    /** 본인에게는 기간과 이의 창구만 알린다. 사유는 운영 기록이라 싣지 않는다(정지 안내 메일에서 따로 알린다). */
    static BusinessException suspended(User user) {
        LocalDate until = user.getSuspendedUntil().atZone(KST).toLocalDate();
        String period = until.getYear() >= PERMANENT_FROM_YEAR
                ? ""
                : " (%d년 %d월 %d일까지)".formatted(until.getYear(), until.getMonthValue(), until.getDayOfMonth());
        return new BusinessException(ErrorCode.ACCOUNT_SUSPENDED,
                "이용이 정지된 계정이에요" + period + ". 이의가 있으면 " + APPEAL_CONTACT + " 로 알려 주세요.");
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
