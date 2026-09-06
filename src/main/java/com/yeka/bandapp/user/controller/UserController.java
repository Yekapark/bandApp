package com.yeka.bandapp.user.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.user.dto.EmailVerificationConfirmRequest;
import com.yeka.bandapp.user.dto.UserResponse;
import com.yeka.bandapp.user.dto.WithdrawRequest;
import com.yeka.bandapp.user.service.EmailVerificationService;
import com.yeka.bandapp.user.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 계정. Bearer 인증 필요. */
@Tag(name = "2. 내 계정", description = "내 정보 조회, 회원 탈퇴, 이메일 인증.")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;
    private final EmailVerificationService emailVerificationService;

    public UserController(UserAccountService userAccountService,
                          EmailVerificationService emailVerificationService) {
        this.userAccountService = userAccountService;
        this.emailVerificationService = emailVerificationService;
    }

    @Operation(summary = "내 정보 조회", description = "토큰 주인의 계정 정보(id, 이메일, 이름 등).")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userAccountService.getMe(principal.userId()));
    }

    @Operation(summary = "회원 탈퇴",
            description = "계정을 즉시 삭제 처리한다(204). 이메일 계정은 본문에 password 재확인이 필요하고, "
                    + "소셜 계정은 본문 없이({}) 호출한다(카카오 연결 해제도 함께 시도). "
                    + "탈퇴 즉시 기존 access 토큰이 막히고, 소속 밴드에서 자동 탈퇴한다 — 탈퇴자가 밴드장이면 "
                    + "가장 먼저 가입한 멤버에게 밴드장이 자동 위임된다. "
                    + "DELETE가 아니라 POST인 것은 통합 테스트 도구 제약 때문.")
    @PostMapping("/me/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthPrincipal principal,
                         @RequestBody(required = false) WithdrawRequest request) {
        userAccountService.withdraw(principal.userId(), request == null ? null : request.password());
    }

    @Operation(summary = "이메일 인증번호 재발송",
            description = "가입 시 자동 발송된 것과 별개로 다시 보낸다(204). 이미 인증됐거나 "
                    + "이메일이 없는 계정(소셜 미동의 등)이면 발송 없이 204. 계정당 분당 상한 있음.")
    @PostMapping("/me/email-verification/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendEmailVerification(@AuthenticationPrincipal AuthPrincipal principal) {
        emailVerificationService.resend(principal.userId());
    }

    @Operation(summary = "이메일 인증 확인",
            description = "인증번호가 맞으면 계정을 인증 완료 처리한다(204). 미인증이어도 다른 "
                    + "기능은 막히지 않는다 — 이 API는 배너 안내를 없애기 위한 것. 인증번호가 틀리거나 "
                    + "만료되면 400 EMAIL_VERIFICATION_CODE_INVALID.")
    @PostMapping("/me/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailVerification(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationService.confirm(principal.userId(), request.code());
    }
}
