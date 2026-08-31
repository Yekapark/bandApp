package com.yeka.bandapp.user.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.user.dto.UserResponse;
import com.yeka.bandapp.user.dto.WithdrawRequest;
import com.yeka.bandapp.user.service.UserAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 계정. Bearer 인증 필요. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userAccountService.getMe(principal.userId()));
    }

    /**
     * 회원 탈퇴. 이메일 계정은 본문에 {@code password} 재확인이 필요하다.
     * {@code DELETE}가 아니라 {@code POST}인 이유: {@code TestRestTemplate}의 기본 요청 팩토리가
     * 본문 있는 DELETE를 지원하지 않아 완료 기준 통합 테스트가 깨진다.
     */
    @PostMapping("/me/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthPrincipal principal,
                         @RequestBody(required = false) WithdrawRequest request) {
        userAccountService.withdraw(principal.userId(), request == null ? null : request.password());
    }
}
