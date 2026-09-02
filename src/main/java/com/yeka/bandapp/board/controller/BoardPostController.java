package com.yeka.bandapp.board.controller;

import com.yeka.bandapp.board.dto.CreatePostRequest;
import com.yeka.bandapp.board.dto.PostListResponse;
import com.yeka.bandapp.board.dto.PostResponse;
import com.yeka.bandapp.board.dto.UpdatePostRequest;
import com.yeka.bandapp.board.service.BoardPostService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 밴드 내부 게시판. Bearer 인증 필요, 모든 엔드포인트가 밴드 멤버십을 검증한다. 목록·상세에서
 * "내가 차단했거나 나를 차단한" 사용자의 글은 빠진다. 수정·삭제는 작성자 본인 또는 밴드장만.
 */
@Tag(name = "12. 게시판",
        description = "밴드 멤버가 합주 사진·영상을 공유하는 게시판. 글 CRUD 는 여기서, 첨부 미디어(R2 presigned "
                + "업로드)는 '13. 첨부 미디어'에서 다룬다. 목록은 커서 페이징(created_at 내림차순)이며 차단 관계의 "
                + "사용자 글은 제외된다.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/posts")
public class BoardPostController {

    private final BoardPostService boardPostService;

    public BoardPostController(BoardPostService boardPostService) {
        this.boardPostService = boardPostService;
    }

    @Operation(summary = "게시글 작성",
            description = "제목·본문 필수. 그 밴드 멤버만(비멤버 403 NOT_BAND_MEMBER). 작성 후 첨부는 "
                    + "POST .../posts/{postId}/media/upload-url 로 올린다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable long bandId,
                                            @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(boardPostService.create(bandId, principal.userId(), request));
    }

    @Operation(summary = "게시글 목록",
            description = "created_at 내림차순. cursor(직전 응답의 nextCursor)와 limit(1~50, 기본 20)로 페이징한다. "
                    + "차단했거나 나를 차단한 사용자의 글은 빠진다. 잘못된 커서는 400 POST_CURSOR_INVALID.")
    @GetMapping
    public ApiResponse<PostListResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable long bandId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(boardPostService.list(bandId, principal.userId(), cursor, limit));
    }

    @Operation(summary = "게시글 상세",
            description = "본문 전체와 첨부 목록(READY 첨부에는 짧은 만료의 다운로드 URL). 삭제됐거나 타 밴드 "
                    + "글이거나 차단 관계면 404 POST_NOT_FOUND.")
    @GetMapping("/{postId}")
    public ApiResponse<PostResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable long bandId,
                                         @PathVariable long postId) {
        return ApiResponse.ok(boardPostService.get(bandId, postId, principal.userId()));
    }

    @Operation(summary = "게시글 수정",
            description = "PUT 전체 교체(제목·본문). 작성자 본인 또는 밴드장만(그 외 403 NOT_POST_OWNER). "
                    + "삭제된 글은 404 POST_NOT_FOUND.")
    @PutMapping("/{postId}")
    public ApiResponse<PostResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable long bandId,
                                            @PathVariable long postId,
                                            @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.ok(boardPostService.update(bandId, postId, principal.userId(), request));
    }

    @Operation(summary = "게시글 삭제",
            description = "소프트 삭제(204). 작성자 본인 또는 밴드장만. 첨부는 EXPIRED 로 바뀌고 R2 객체는 "
                    + "정리된다(실패해도 보관기한 배치가 최종 정리).")
    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable long bandId,
                       @PathVariable long postId) {
        boardPostService.delete(bandId, postId, principal.userId());
    }
}
