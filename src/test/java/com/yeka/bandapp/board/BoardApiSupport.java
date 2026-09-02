package com.yeka.bandapp.board;

import com.yeka.bandapp.band.BandApiSupport;
import org.springframework.http.ResponseEntity;

/**
 * 게시판 통합 테스트 공통 헬퍼. 밴드 픽스처(가입·밴드 생성·초대·참여)는 {@link BandApiSupport}에서 온다.
 */
public abstract class BoardApiSupport extends BandApiSupport {

    protected String postsPath(long bandId) {
        return "/api/v1/bands/" + bandId + "/posts";
    }

    protected String postPath(long bandId, long postId) {
        return postsPath(bandId) + "/" + postId;
    }

    protected String mediaPath(long bandId, long postId) {
        return postPath(bandId, postId) + "/media";
    }

    /** 게시글을 만들고 postId 를 돌려준다. */
    protected long createPost(String token, long bandId, String title, String content) {
        ResponseEntity<String> res = post(postsPath(bandId),
                "{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}", token);
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("게시글 작성 실패: " + res.getBody());
        }
        return data(res).get("id").asLong();
    }

    /** 업로드 URL 을 발급받고 응답 data(mediaId·uploadUrl 등)를 돌려준다. */
    protected ResponseEntity<String> issueUploadUrl(String token, long bandId, long postId,
                                                    String contentType, long sizeBytes) {
        return post(mediaPath(bandId, postId) + "/upload-url",
                "{\"contentType\":\"" + contentType + "\",\"sizeBytes\":" + sizeBytes + "}", token);
    }

    protected ResponseEntity<String> completeUpload(String token, long bandId, long postId, long mediaId) {
        return post(mediaPath(bandId, postId) + "/" + mediaId + "/complete", "{}", token);
    }
}
