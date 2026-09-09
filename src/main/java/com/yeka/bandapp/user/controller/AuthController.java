package com.yeka.bandapp.user.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.user.dto.AuthResponse;
import com.yeka.bandapp.user.dto.KakaoLoginRequest;
import com.yeka.bandapp.user.dto.LoginRequest;
import com.yeka.bandapp.user.dto.LogoutRequest;
import com.yeka.bandapp.user.dto.PasswordResetConfirmRequest;
import com.yeka.bandapp.user.dto.PasswordResetRequestRequest;
import com.yeka.bandapp.user.dto.SignupRequest;
import com.yeka.bandapp.user.dto.TokenRefreshRequest;
import com.yeka.bandapp.user.dto.TokenResponse;
import com.yeka.bandapp.user.service.AuthService;
import com.yeka.bandapp.user.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 엔드포인트. 전부 무인증({@code /api/v1/auth/**} permitAll)이다.
 * IP 당 분당 20회 레이트리밋(초과 시 429 TOO_MANY_REQUESTS).
 */
@Tag(name = "1. 인증", description = "가입·로그인·토큰 갱신·로그아웃. 모두 토큰 없이 호출한다. IP당 분당 20회 제한.")
@SecurityRequirements // 이 컨트롤러 엔드포인트는 Authorize 불필요
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "이메일 회원가입",
            description = "새 이메일 계정을 만들고 바로 로그인 상태로 토큰을 발급한다(201). "
                    + "이메일은 대소문자·공백을 정규화해 저장한다. 이미 있으면 409 EMAIL_ALREADY_REGISTERED, "
                    + "비밀번호 8자 미만 등은 400 INVALID_INPUT. 메일을 받을 수 없는 예약 도메인"
                    + "(example.com, .test, .invalid, .localhost, .local)은 400 EMAIL_DOMAIN_NOT_ALLOWED.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @Operation(summary = "이메일 로그인",
            description = "성공 시 access/refresh 토큰을 발급한다. 이메일 미존재와 비밀번호 불일치를 "
                    + "구분하지 않고 모두 401 INVALID_CREDENTIALS(계정 존재 여부 비노출).")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "카카오 로그인",
            description = "앱이 카카오 SDK로 받은 access token을 넘기면 서버가 카카오에 사용자 정보를 조회해 "
                    + "가입/로그인 처리한다. 서버에 카카오 키가 설정되지 않았으면 503 KAKAO_NOT_CONFIGURED, "
                    + "다른 앱의 토큰이면 401 KAKAO_APP_MISMATCH.")
    @PostMapping("/kakao")
    public ApiResponse<AuthResponse> kakao(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.ok(authService.kakaoLogin(request.accessToken()));
    }

    @Operation(summary = "토큰 갱신(refresh)",
            description = "refresh 토큰으로 새 access/refresh 한 쌍을 발급하고 이전 refresh는 폐기한다. "
                    + "네트워크 재시도 대비로, 방금 쓴 refresh를 60초 안에 다시 보내면 같은 결과를 그대로 돌려준다. "
                    + "그 창을 넘겨 재사용하면 401 REFRESH_TOKEN_INVALID이며 해당 사용자의 전 세션이 무효화된다.")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃",
            description = "refresh 토큰만으로 해당 세션을 정리한다(204). 이미 만료·무효한 토큰이어도 204(멱등). "
                    + "다른 기기 세션은 유지된다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(summary = "비밀번호 재설정 인증번호 발송",
            description = "이메일 계정에 6자리 인증번호를 보낸다(204). 존재하지 않는 이메일·소셜 "
                    + "계정이어도 항상 204를 반환한다(계정 존재 여부 비노출). 15분간 유효.")
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        passwordResetService.request(request.email());
    }

    @Operation(summary = "비밀번호 재설정 확인",
            description = "인증번호와 새 비밀번호로 재설정한다(204). 인증번호가 틀리거나 만료되면 "
                    + "400 PASSWORD_RESET_CODE_INVALID(5회 오답 시 인증번호 자체가 무효화된다). "
                    + "성공하면 이 계정의 모든 기기 세션이 로그아웃된다 — 다시 로그인해야 한다.")
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.email(), request.code(), request.newPassword());
    }
}
