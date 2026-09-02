package com.yeka.bandapp.board.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * 비공개 객체 저장소(Cloudflare R2) 경계. 이 인터페이스가 미디어 저장의 유일한 접점이며,
 * 나머지 코드는 R2·S3 를 모른다({@code GeocodingClient}와 같은 역할).
 *
 * <p><b>이 창구에는 바이트를 받거나 돌려주는 메서드가 없다.</b> 업로드는 클라이언트가 presigned PUT URL 로
 * R2 에 직접 하고, 다운로드도 presigned GET URL 로 직접 한다 — 파일 스트림이 백엔드를 지나지 않는다
 * (BUILD_PLAN §2-5). 서버는 서명(오프라인)과 메타데이터 확인(HEAD)·정리(DELETE)만 한다.
 *
 * <p>키가 설정되지 않았으면 모든 메서드가 {@code MEDIA_STORAGE_NOT_CONFIGURED}(503)를, 저장소 통신
 * 실패는 {@code MEDIA_STORAGE_ERROR}(502)를 던진다({@code KakaoApiClient}와 같은 실패 정책).
 */
public interface StorageClient {

    /** 업로드용 서명 URL. 서명은 네트워크를 타지 않는다. {@code contentType}이 서명에 포함돼 다른 타입으로 올리면 R2 가 거부한다. */
    URI presignPut(String storageKey, String contentType, Duration ttl);

    /**
     * 조회용 서명 URL. 응답 {@code Content-Type}을 저장된 값으로, {@code Content-Disposition}을
     * {@code attachment}로 고정해 오분류된 파일이 브라우저에서 실행되는 것을 막는다.
     */
    URI presignGet(String storageKey, String contentType, Duration ttl);

    /** 실제 업로드 확인. 객체가 없으면 {@link Optional#empty()}(예외 아님 — 정상적인 "아직 안 올림"). */
    Optional<StoredObject> head(String storageKey);

    /** 멱등 삭제. 객체가 이미 없어도 성공으로 취급한다. */
    void delete(String storageKey);
}
