package com.yeka.bandapp.user;

import com.yeka.bandapp.support.IntegrationTestSupport;
import com.yeka.bandapp.user.entity.User;
import com.yeka.bandapp.user.repository.UserRepository;
import com.yeka.bandapp.user.service.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파기 로직은 스케줄러를 기다리지 않고 서비스 메서드를 직접 호출해 검증한다.
 */
class WithdrawnUserPurgeJobTest extends IntegrationTestSupport {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserAccountService userAccountService;

    private User withdrawnDaysAgo(String email, int days) {
        User user = User.ofEmail(email, "{bcrypt}dummyhash", "이름");
        user.withdraw(Instant.now().minus(days, ChronoUnit.DAYS));
        return userRepository.save(user);
    }

    @Test
    void purges_only_accounts_past_retention() {
        User old = withdrawnDaysAgo("old@band.app", 100);
        User recent = withdrawnDaysAgo("recent@band.app", 10);
        User active = userRepository.save(User.ofEmail("active@band.app", "{bcrypt}dummyhash", "액티브"));

        int purged = userAccountService.anonymizeWithdrawnBefore(Instant.now().minus(90, ChronoUnit.DAYS));

        assertThat(purged).isEqualTo(1);

        User reloadedOld = userRepository.findById(old.getId()).orElseThrow();
        assertThat(reloadedOld.getEmail()).isNull();
        assertThat(reloadedOld.getPasswordHash()).isNull();
        assertThat(reloadedOld.getSocialId()).isNull();
        assertThat(reloadedOld.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(reloadedOld.getId()).isEqualTo(old.getId());

        assertThat(userRepository.findById(recent.getId()).orElseThrow().getEmail()).isEqualTo("recent@band.app");
        assertThat(userRepository.findById(active.getId()).orElseThrow().getEmail()).isEqualTo("active@band.app");
    }

    @Test
    void is_idempotent() {
        withdrawnDaysAgo("old@band.app", 100);
        Instant threshold = Instant.now().minus(90, ChronoUnit.DAYS);

        assertThat(userAccountService.anonymizeWithdrawnBefore(threshold)).isEqualTo(1);
        assertThat(userAccountService.anonymizeWithdrawnBefore(threshold)).isZero();
    }
}
