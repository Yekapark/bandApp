package com.yeka.bandapp.user.service;

import com.yeka.bandapp.user.entity.User;
import com.yeka.bandapp.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 다른 도메인(밴드 등)이 사용자 정보를 볼 때 쓰는 읽기 전용 창구.
 * 도메인 간 참조는 저장소가 아니라 이 서비스를 통한다(코딩 컨벤션).
 */
@Service
public class UserDirectoryService {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean existsActive(long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId).isPresent();
    }

    /**
     * 주어진 id 들의 표시용 요약. 탈퇴/익명화된 사용자도 포함해 반환한다
     * (밴드 멤버 목록에서 "탈퇴한 사용자"로 보여야 하므로).
     */
    @Transactional(readOnly = true)
    public List<UserSummary> summariesOf(Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .map(UserSummary::from)
                .toList();
    }

    public record UserSummary(long userId, String name, String email) {
        static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getName(), user.getEmail());
        }
    }
}
