package com.yeka.bandapp.board.storage;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * {@link StorageClient}의 실제 구현 — Cloudflare R2(S3 호환) API.
 *
 * <p>{@code S3Presigner}는 <b>오프라인 서명</b>이라 네트워크를 타지 않는다(PUT/GET URL 발급).
 * {@code S3Client}는 업로드 검증({@code headObject})과 정리({@code deleteObject})에만 쓰며,
 * 호출 측({@code MediaAttachmentService})이 <b>트랜잭션 밖에서</b> 부른다(CLAUDE.md 규칙).
 *
 * <p>키가 없으면 클라이언트를 만들지 않고, 각 메서드가 {@code MEDIA_STORAGE_NOT_CONFIGURED}(503)를
 * 던진다. 저장소 통신 실패는 {@code MEDIA_STORAGE_ERROR}(502)로 변환한다({@code KakaoApiClient} 계열).
 */
@Component
public class R2StorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(R2StorageClient.class);

    private final R2Properties properties;
    private final S3Client s3;
    private final S3Presigner presigner;

    public R2StorageClient(R2Properties properties) {
        this.properties = properties;
        if (!properties.isConfigured()) {
            this.s3 = null;
            this.presigner = null;
            log.info("R2 키가 없어 미디어 업로드 API 는 비활성 상태로 뜬다(게시판·신고·차단은 정상).");
            return;
        }
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
        URI endpoint = URI.create(properties.endpoint());
        S3Configuration serviceConfig = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.s3 = S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(endpoint)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(properties.connectTimeout())
                        .socketTimeout(properties.readTimeout())
                        .build())
                .build();
        this.presigner = S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(endpoint)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig)
                .build();
    }

    @Override
    public URI presignPut(String storageKey, String contentType, Duration ttl) {
        S3Presigner client = requirePresigner();
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .contentType(contentType)
                .build();
        try {
            return toUri(client.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(put)
                    .build()).url());
        } catch (SdkException e) {
            throw storageError("presignPut", storageKey, e);
        }
    }

    @Override
    public URI presignGet(String storageKey, String contentType, Duration ttl) {
        S3Presigner client = requirePresigner();
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .responseContentType(contentType)
                .responseContentDisposition("attachment")
                .build();
        try {
            return toUri(client.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(get)
                    .build()).url());
        } catch (SdkException e) {
            throw storageError("presignGet", storageKey, e);
        }
    }

    @Override
    public Optional<StoredObject> head(String storageKey) {
        S3Client client = requireS3();
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .build());
            return Optional.of(new StoredObject(response.contentLength(), response.contentType()));
        } catch (NoSuchKeyException notFound) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw storageError("head", storageKey, e);
        } catch (SdkException e) {
            throw storageError("head", storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        S3Client client = requireS3();
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .build());
        } catch (NoSuchKeyException alreadyGone) {
            // 멱등 — 이미 없으면 성공으로 본다.
        } catch (SdkException e) {
            throw storageError("delete", storageKey, e);
        }
    }

    /**
     * 접두사 아래 객체를 목록으로 훑어 1000개씩 묶어 지운다(S3 DeleteObjects 한 번에 최대 1000).
     * 한 건씩 지우는 것보다 왕복이 훨씬 적고, DB 가 모르는 고아 객체까지 함께 정리된다.
     */
    @Override
    public int deleteByPrefix(String keyPrefix) {
        S3Client client = requireS3();
        int deleted = 0;
        String continuationToken = null;
        try {
            do {
                ListObjectsV2Response listed = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(properties.bucket())
                        .prefix(keyPrefix)
                        .continuationToken(continuationToken)
                        .build());
                List<ObjectIdentifier> keys = listed.contents().stream()
                        .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                        .toList();
                if (!keys.isEmpty()) {
                    client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(properties.bucket())
                            .delete(Delete.builder().objects(keys).build())
                            .build());
                    deleted += keys.size();
                }
                // 목록을 지우는 중이라 다음 페이지 토큰을 그대로 따라간다(삭제분은 이미 응답에 담겼다).
                continuationToken = Boolean.TRUE.equals(listed.isTruncated())
                        ? listed.nextContinuationToken()
                        : null;
            } while (continuationToken != null);
        } catch (SdkException e) {
            throw storageError("deleteByPrefix", keyPrefix, e);
        }
        return deleted;
    }

    @PreDestroy
    void close() {
        if (s3 != null) {
            s3.close();
        }
        if (presigner != null) {
            presigner.close();
        }
    }

    private S3Presigner requirePresigner() {
        if (presigner == null) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
        }
        return presigner;
    }

    private S3Client requireS3() {
        if (s3 == null) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
        }
        return s3;
    }

    private static URI toUri(java.net.URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_ERROR);
        }
    }

    private BusinessException storageError(String op, String storageKey, SdkException e) {
        log.warn("R2 {} 실패 key={}", op, storageKey, e);
        return new BusinessException(ErrorCode.MEDIA_STORAGE_ERROR);
    }
}
