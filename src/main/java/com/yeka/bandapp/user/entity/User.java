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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String passwordHash, String name, SocialProvider socialProvider, String socialId) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.socialProvider = socialProvider;
        this.socialId = socialId;
    }

    public static User ofEmail(String email, String passwordHash, String name) {
        return User.builder().email(email).passwordHash(passwordHash).name(name).build();
    }

    public static User ofSocial(SocialProvider provider, String socialId, String email, String name) {
        return User.builder().socialProvider(provider).socialId(socialId).email(email).name(name).build();
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
