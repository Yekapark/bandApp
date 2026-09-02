package com.yeka.bandapp.board.service;

import com.yeka.bandapp.board.dto.BlockListResponse;
import com.yeka.bandapp.board.dto.BlockResponse;
import com.yeka.bandapp.board.dto.CreateBlockRequest;
import com.yeka.bandapp.board.entity.UserBlock;
import com.yeka.bandapp.board.repository.UserBlockRepository;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.user.service.UserDirectoryService;
import com.yeka.bandapp.user.service.UserDirectoryService.UserSummary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 전역(밴드 무관) 사용자 차단. 차단은 <b>양방향</b>으로 작동한다 — 게시판 목록·상세는 "내가 차단한"
 * 사용자뿐 아니라 "나를 차단한" 사용자의 글도 숨긴다({@link #hiddenUserIdsFor}). 단방향이면 차단
 * 사실이 상대에게 역으로 드러난다.
 */
@Service
public class UserBlockService {

    private final UserBlockRepository blockRepository;
    private final UserDirectoryService userDirectory;

    public UserBlockService(UserBlockRepository blockRepository, UserDirectoryService userDirectory) {
        this.blockRepository = blockRepository;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public BlockResponse block(long callerId, CreateBlockRequest request) {
        long targetId = request.blockedUserId();
        if (targetId == callerId) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }
        if (!userDirectory.existsActive(targetId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserBlock block = UserBlock.of(callerId, targetId);
        try {
            blockRepository.saveAndFlush(block);
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
        return BlockResponse.of(block, displayName(targetId));
    }

    @Transactional(readOnly = true)
    public BlockListResponse list(long callerId) {
        List<UserBlock> blocks = blockRepository.findByBlockerIdOrderByIdDesc(callerId);
        Map<Long, UserSummary> byId = userDirectory
                .summariesOf(blocks.stream().map(UserBlock::getBlockedUserId).toList()).stream()
                .collect(Collectors.toMap(UserSummary::userId, Function.identity()));
        List<BlockResponse> rows = blocks.stream()
                .map(b -> {
                    UserSummary summary = byId.get(b.getBlockedUserId());
                    return BlockResponse.of(b, summary != null ? summary.name() : "(알 수 없음)");
                })
                .toList();
        return new BlockListResponse(rows.size(), rows);
    }

    @Transactional
    public void unblock(long callerId, long blockedUserId) {
        UserBlock block = blockRepository.findByBlockerIdAndBlockedUserId(callerId, blockedUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOCK_NOT_FOUND));
        blockRepository.delete(block);
    }

    /**
     * 게시판 목록·상세 필터용 — 요청자가 차단했거나 요청자를 차단한 모든 userId. 게시글 쿼리가
     * {@code author_id NOT IN} 으로 쓴다.
     */
    @Transactional(readOnly = true)
    public Set<Long> hiddenUserIdsFor(long callerId) {
        return new HashSet<>(blockRepository.findRelatedUserIds(callerId));
    }

    private String displayName(long userId) {
        return userDirectory.summariesOf(List.of(userId)).stream()
                .findFirst()
                .map(UserSummary::name)
                .orElse("(알 수 없음)");
    }
}
