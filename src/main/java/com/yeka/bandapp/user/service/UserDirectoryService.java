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
     *
     * <p>탈퇴한 사람은 <b>탈퇴 즉시</b> "탈퇴한 사용자" 로, 이메일은 비워서 돌려준다(LAUNCH_REVIEW L8).
     * 예전엔 저장된 값을 그대로 줘서 90일 파기({@code User#anonymize}) 전까지 정산·게시판·출석 등에 실명이 보였다.
     * 정산·게시판·출석·멤버 목록의 다른 사람 이름은 모두 이 메서드를 지난다. 운영 도구(ReportMail 의 SQL)는 원래 값을 본다.
     */
    @Transactional(readOnly = true)
    public List<UserSummary> summariesOf(Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .map(UserSummary::from)
                .toList();
    }

    public record UserSummary(long userId, String name, String email) {
        static UserSummary from(User user) {
            if (user.isWithdrawn()) {
                return new UserSummary(user.getId(), User.WITHDRAWN_NAME, null);
            }
            return new UserSummary(user.getId(), user.getName(), user.getEmail());
        }
    }
}
