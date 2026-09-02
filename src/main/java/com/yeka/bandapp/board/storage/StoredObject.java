package com.yeka.bandapp.board.storage;

/**
 * 저장소에 실제로 올라온 객체의 메타데이터. 완료 콜백이 클라이언트가 신고한 값과 대조한다.
 * 바이트 자체는 담지 않는다 — 서버는 파일 내용을 읽지 않는다.
 */
public record StoredObject(long sizeBytes, String contentType) {
}
