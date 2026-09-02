package com.yeka.bandapp.board.controller;

import com.yeka.bandapp.board.dto.IssueUploadUrlRequest;
import com.yeka.bandapp.board.dto.MediaResponse;
import com.yeka.bandapp.board.dto.UploadUrlResponse;
import com.yeka.bandapp.board.service.MediaAttachmentService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 첨부 미디어. 파일은 <b>백엔드를 지나지 않는다</b> — 서버는 presigned PUT URL 을 발급하고,
 * 클라이언트가 Cloudflare R2 로 직접 올린 뒤 완료 콜백을 호출한다. 콜백에서 R2 HEAD 로 실제 크기·형식을
 * 확인해 READY 로 확정하며, 신고 값과 다르면 객체와 레코드를 함께 지운다.
 *
 * <p>모든 엔드포인트는 밴드 멤버십 + 게시글 작성자 본인을 검증한다(첨부 삭제만 밴드장도 가능).
 * 서버에 R2 키가 없으면 503 MEDIA_STORAGE_NOT_CONFIGURED.
 */
@Tag(name = "13. 첨부 미디어",
        description = "게시글 사진·영상 첨부. upload-url 로 presigned PUT URL 을 받아 R2 에 직접 PUT → "
                + "complete 로 실제 업로드를 검증(HEAD)해 READY 전환. 이미지 10MB / 영상 50MB, URL 만료 5~15분.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/posts/{postId}/media")
public class MediaAttachmentController {

    private final MediaAttachmentService mediaAttachmentService;

    public MediaAttachmentController(MediaAttachmentService mediaAttachmentService) {
        this.mediaAttachmentService = mediaAttachmentService;
    }

    @Operation(summary = "업로드 URL 발급",
            description = "contentType(허용: image/jpeg·png·webp, video/mp4·quicktime)·sizeBytes 필수. "
                    + "PENDING 첨부를 선생성하고 presigned PUT URL 을 준다. 게시글 작성자만(그 외 403 NOT_POST_OWNER, "
                    + "타 밴드 글 404 POST_NOT_FOUND). 형식 위반 400 MEDIA_TYPE_NOT_SUPPORTED, 크기 초과 400 "
                    + "MEDIA_SIZE_EXCEEDED, 첨부 수 상한 409 MEDIA_LIMIT_EXCEEDED, 과다 요청 429, 저장소 미설정 503.")
    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadUrlResponse> issueUploadUrl(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable long bandId,
                                                         @PathVariable long postId,
                                                         @Valid @RequestBody IssueUploadUrlRequest request) {
        return ApiResponse.ok(
                mediaAttachmentService.issueUploadUrl(bandId, postId, principal.userId(), request));
    }

    @Operation(summary = "업로드 완료 콜백",
            description = "R2 HEAD 로 실제 객체를 확인해 READY 로 전환한다. 객체가 없으면 409 MEDIA_NOT_UPLOADED "
                    + "(재시도 가능), 신고 크기·형식과 다르면 409 MEDIA_SIZE_MISMATCH / MEDIA_CONTENT_TYPE_MISMATCH "
                    + "(객체·레코드 삭제), 이미 완료/취소면 409 MEDIA_NOT_PENDING. 게시글 작성자만.")
    @PostMapping("/{mediaId}/complete")
    public ApiResponse<MediaResponse> complete(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable long bandId,
                                               @PathVariable long postId,
                                               @PathVariable long mediaId) {
        return ApiResponse.ok(mediaAttachmentService.complete(bandId, postId, mediaId, principal.userId()));
    }

    @Operation(summary = "첨부 삭제",
            description = "DB 행을 지우고 R2 객체를 정리한다(204). 작성자 본인 또는 밴드장만(그 외 403 NOT_POST_OWNER). "
                    + "없으면 404 MEDIA_NOT_FOUND.")
    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable long bandId,
                       @PathVariable long postId,
                       @PathVariable long mediaId) {
        mediaAttachmentService.delete(bandId, postId, mediaId, principal.userId());
    }
}
