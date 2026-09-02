package com.yeka.bandapp.support;

import com.yeka.bandapp.board.storage.StorageClient;
import com.yeka.bandapp.board.storage.StoredObject;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 인메모리 R2 스텁. <b>바이트를 다루지 않는다</b> — 테스트가 {@link #putObject}로 "클라이언트가 R2 에
 * 이만큼 올렸다"는 상태만 심는다. 완료 콜백이 이 값을 신고 값과 대조하는 흐름(완료 기준 ②)을 검증한다.
 *
 * <p>응답에 {@code storageKey}를 넣지 않기로 했으므로, 테스트는 {@link #lastPresignedPutKey()}로
 * 방금 발급된 키를 얻는다.
 */
public class FakeStorageClient implements StorageClient {

    private final Map<String, StoredObject> objects = new LinkedHashMap<>();
    private final List<String> presignedPutKeys = new ArrayList<>();
    private final List<String> presignedGetKeys = new ArrayList<>();
    private final List<String> deletedKeys = new ArrayList<>();
    private boolean failNextHead = false;
    private boolean failNextDelete = false;

    public void reset() {
        objects.clear();
        presignedPutKeys.clear();
        presignedGetKeys.clear();
        deletedKeys.clear();
        failNextHead = false;
        failNextDelete = false;
    }

    /** 업로드 성공 시뮬레이션. */
    public void putObject(String storageKey, long sizeBytes, String contentType) {
        objects.put(storageKey, new StoredObject(sizeBytes, contentType));
    }

    /** 다음 {@link #head} 호출이 저장소 통신 실패로 터지게 한다. */
    public void failNextHead() {
        this.failNextHead = true;
    }

    /** 다음 {@link #delete} 호출이 저장소 통신 실패로 터지게 한다(만료 배치의 "삭제 실패 후 재시도" 검증용). */
    public void failNextDelete() {
        this.failNextDelete = true;
    }

    public String lastPresignedPutKey() {
        if (presignedPutKeys.isEmpty()) {
            throw new IllegalStateException("presignPut 이 호출된 적이 없습니다.");
        }
        return presignedPutKeys.get(presignedPutKeys.size() - 1);
    }

    public List<String> deletedKeys() {
        return List.copyOf(deletedKeys);
    }

    public boolean objectExists(String storageKey) {
        return objects.containsKey(storageKey);
    }

    @Override
    public URI presignPut(String storageKey, String contentType, Duration ttl) {
        presignedPutKeys.add(storageKey);
        return URI.create("https://fake-r2.test/" + storageKey + "?sig=fake&op=put");
    }

    @Override
    public URI presignGet(String storageKey, String contentType, Duration ttl) {
        presignedGetKeys.add(storageKey);
        return URI.create("https://fake-r2.test/" + storageKey + "?sig=fake&op=get");
    }

    @Override
    public Optional<StoredObject> head(String storageKey) {
        if (failNextHead) {
            failNextHead = false;
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_ERROR);
        }
        return Optional.ofNullable(objects.get(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        if (failNextDelete) {
            failNextDelete = false;
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_ERROR);
        }
        objects.remove(storageKey);
        deletedKeys.add(storageKey);
    }
}
