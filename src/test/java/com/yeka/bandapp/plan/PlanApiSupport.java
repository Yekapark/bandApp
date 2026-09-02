package com.yeka.bandapp.plan;

import com.yeka.bandapp.board.BoardApiSupport;
import com.yeka.bandapp.support.FakeStorageClient;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요금제 통합 테스트 공통 헬퍼. 밴드·게시판 픽스처는 {@link BoardApiSupport} 에서 온다.
 */
public abstract class PlanApiSupport extends BoardApiSupport {

    protected String planPath(long bandId) {
        return "/api/v1/bands/" + bandId + "/plan";
    }

    protected ResponseEntity<String> viewPlan(String token, long bandId) {
        return get(planPath(bandId), token);
    }

    protected ResponseEntity<String> subscribe(String token, long bandId) {
        return post(planPath(bandId) + "/subscribe", "{}", token);
    }

    protected ResponseEntity<String> cancel(String token, long bandId) {
        return post(planPath(bandId) + "/cancel", "{}", token);
    }

    protected ResponseEntity<String> renew(String token, long bandId) {
        return post(planPath(bandId) + "/renew", "{}", token);
    }

    /**
     * 게시글 하나에 이미지 첨부를 올려 READY 까지 만든 뒤 mediaId 를 돌려준다.
     * ({@code MediaExpirationJobTest.uploadReady} 와 같은 절차.)
     */
    protected long uploadReadyMedia(FakeStorageClient storage, String token, long bandId, long postId) {
        long imageBytes = 1024;
        long mediaId = data(issueUploadUrl(token, bandId, postId, "image/jpeg", imageBytes))
                .get("mediaId").asLong();
        storage.putObject(storage.lastPresignedPutKey(), imageBytes, "image/jpeg");
        assertThat(completeUpload(token, bandId, postId, mediaId).getStatusCode().value()).isEqualTo(200);
        return mediaId;
    }
}
