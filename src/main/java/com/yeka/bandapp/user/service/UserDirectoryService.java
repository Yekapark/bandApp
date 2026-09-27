package com.yeka.bandapp.user.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.user.entity.User;
import com.yeka.bandapp.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 다른 도메인(밴드 등)의 사용자 조회·활성 계정 검증 창구.
 * 도메인 간 참조는 저장소가 아니라 이 서비스를 통한다(코딩 컨벤션).
 */
@Service
public class UserDirectoryService {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 계정 삭제와 겹쳐 멤버십이 새로 생기지 않도록 사용자 → 밴드 순서로 잠근다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActiveUser(long userId) {
        userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
