package com.yeka.bandapp.user.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자 계정. 이메일 가입자는 {@code socialProvider == null} + {@code passwordHash} 보유,
 * 소셜 가입자는 {@code socialProvider} + {@code socialId} 보유({@code passwordHash} 없음).
 *
 * <p>{@code user}는 PostgreSQL 예약어라 테이블명은 {@code users}.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", length = 20)
    private SocialProvider socialProvider;

    @Column(name = "social_id", length = 64)
    private String socialId;

    /**
     * 소셜 가입자는 카카오가 이미 확인해 준 이메일이라 항상 {@code true}. 이메일 가입자만
     * 가입 시점에 {@code false}로 시작해 인증 코드 메일을 받는다. 강제하지 않는다 — 미인증
     * 이어도 모든 기능을 그대로 쓸 수 있고, 클라이언트가 배너로만 안내한다.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String passwordHash, String name, SocialProvider socialProvider, String socialId,
                boolean emailVerified) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.socialProvider = socialProvider;
        this.socialId = socialId;
        this.emailVerified = emailVerified;
    }

    public static User ofEmail(String email, String passwordHash, String name) {
        return User.builder().email(email).passwordHash(passwordHash).name(name).emailVerified(false).build();
    }

    public static User ofSocial(SocialProvider provider, String socialId, String email, String name) {
        return User.builder().socialProvider(provider).socialId(socialId).email(email).name(name)
                .emailVerified(true).build();
    }

    public boolean isEmailAccount() {
        return socialProvider == null;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    /** 소프트 삭제. 이미 탈퇴한 계정이면 시각을 덮어쓰지 않는다. */
    public void withdraw(Instant when) {
        if (deletedAt == null) {
            this.deletedAt = when;
        }
    }

    /** 비밀번호 재설정. 이미 인코딩된 해시를 받는다 — 인코딩은 서비스 레이어 책임. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /** 이메일 인증 완료 처리. 이미 인증된 상태여도 안전하게 다시 호출할 수 있다(멱등). */
    public void verifyEmail() {
        this.emailVerified = true;
    }

    /**
     * 개인정보 파기 (탈퇴 후 보관기간 경과 시). {@code socialProvider}는 개인정보가 아니라
     * 가입 경로 통계로 쓸 수 있어 남긴다. {@code id}/{@code createdAt}/{@code deletedAt}도 유지 —
     * 이후 Phase의 FK 무결성에 필요하다.
     */
    public void anonymize() {
        this.email = null;
        this.passwordHash = null;
        this.socialId = null;
        this.name = "탈퇴한 사용자";
    }

    public boolean isAnonymized() {
        return email == null && passwordHash == null && socialId == null;
    }
}
