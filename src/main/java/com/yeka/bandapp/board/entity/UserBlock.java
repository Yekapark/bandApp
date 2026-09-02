package com.yeka.bandapp.board.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 간 전역(밴드 무관) 차단. 게시판 목록·상세는 "내가 차단했거나 나를 차단한" 사용자의 글을
 * 양방향으로 제외한다({@code UserBlockService#hiddenUserIdsFor}).
 */
@Entity
@Table(name = "user_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    private UserBlock(long blockerId, long blockedUserId) {
        this.blockerId = blockerId;
        this.blockedUserId = blockedUserId;
    }

    public static UserBlock of(long blockerId, long blockedUserId) {
        return new UserBlock(blockerId, blockedUserId);
    }
}
