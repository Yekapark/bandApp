package com.yeka.bandapp.board.service;

import com.yeka.bandapp.board.entity.MediaType;
import com.yeka.bandapp.board.storage.StoredObject;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;

import java.util.Map;

/**
 * 첨부 미디어의 형식·크기 규칙(순수 함수). 상태·트랜잭션이 없어 Docker 없이 단위 테스트한다
 * ({@code SettlementCalculator} 선례).
 *
 * <p><b>바이트 내용(매직넘버) 검증은 하지 않는다</b> — 파일을 서버가 읽어야 하므로 금지 규칙에 걸린다.
 * 대신 ① 실행 벡터가 되는 SVG·HTML 을 허용목록에서 빼고, ② 조회용 presigned GET 에
 * {@code Content-Disposition: attachment} 를 고정해 브라우저 실행 경로를 끊는다(StorageClient).
 */
public final class MediaPolicy {

    public static final long IMAGE_MAX_BYTES = 10L * 1024 * 1024; // 10MB
    /**
     * 영상 상한 200MB. 합주 영상은 5~6분이 예사인데 50MB 로는 480p 도 빠듯했다.
     * 클라이언트가 업로드 전에 720p 로 압축하므로 6분이 대략 90MB 안쪽에 들어온다.
     *
     * <p>파일은 presigned URL 로 R2 에 직접 올라가 백엔드를 거치지 않고, 클라이언트도
     * 스트림으로 흘려보내므로 상한을 올려도 서버·앱 메모리에 부담이 없다.
     */
    public static final long VIDEO_MAX_BYTES = 200L * 1024 * 1024; // 200MB
    public static final int MAX_ATTACHMENTS_PER_POST = 10;

    /** 허용 MIME → 미디어 종류. image/svg+xml·image/gif·text/* 등은 의도적으로 뺀다. */
    private static final Map<String, MediaType> ALLOWED = Map.of(
            "image/jpeg", MediaType.IMAGE,
            "image/png", MediaType.IMAGE,
            "image/webp", MediaType.IMAGE,
            "video/mp4", MediaType.VIDEO,
            "video/quicktime", MediaType.VIDEO
    );

    private MediaPolicy() {
    }

    /** {@code contentType}(파라미터·대소문자 무시)을 허용목록과 대조해 종류를 정한다. 없으면 400. */
    public static MediaType resolveType(String contentType) {
        MediaType type = ALLOWED.get(normalize(contentType));
        if (type == null) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED);
        }
        return type;
    }

    public static long maxBytesFor(MediaType type) {
        return type == MediaType.VIDEO ? VIDEO_MAX_BYTES : IMAGE_MAX_BYTES;
    }

    /** 클라이언트가 신고한 크기가 0 이하면 400 INVALID_INPUT, 형식별 상한 초과면 400 MEDIA_SIZE_EXCEEDED. */
    public static void requireWithinLimit(MediaType type, long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (sizeBytes > maxBytesFor(type)) {
            throw new BusinessException(ErrorCode.MEDIA_SIZE_EXCEEDED);
        }
    }

    /**
     * 완료 콜백 — R2 HEAD 로 얻은 실제 객체가 신고 값과 맞는지 확인한다.
     * 크기가 다르면 409 MEDIA_SIZE_MISMATCH, (실제 형식이 확인되고) 다르면 409 MEDIA_CONTENT_TYPE_MISMATCH,
     * 실제 크기가 형식별 상한을 넘으면 409 MEDIA_SIZE_MISMATCH(위조로 취급).
     */
    public static void verifyUpload(MediaType type, long declaredSize, String declaredContentType,
                                    StoredObject actual) {
        if (actual.sizeBytes() != declaredSize || actual.sizeBytes() > maxBytesFor(type)) {
            throw new BusinessException(ErrorCode.MEDIA_SIZE_MISMATCH);
        }
        String actualType = normalize(actual.contentType());
        if (!actualType.isEmpty() && !actualType.equals(normalize(declaredContentType))) {
            throw new BusinessException(ErrorCode.MEDIA_CONTENT_TYPE_MISMATCH);
        }
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return base.trim().toLowerCase();
    }
}
