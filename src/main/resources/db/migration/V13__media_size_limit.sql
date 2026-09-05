-- 영상 상한을 200MB 로 올린다.
--
-- V8 은 영상 50MB 를 전제로 CHECK 를 걸었는데, 그 뒤 MediaPolicy.VIDEO_MAX_BYTES 와 클라이언트가
-- 200MB 로 올라가면서 이 제약만 남았다. 업로드 URL 발급 시 신고 크기로 PENDING 행을 먼저 넣기
-- 때문에(MediaAttachmentService), 50MB 를 넘는 영상은 업로드를 시작하지도 못하고 500 이 났다.
-- 클라이언트가 720p 로 압축해도 6분이면 90MB 안팎이라 정상 사용 경로가 막혀 있었다.
ALTER TABLE media_attachments DROP CONSTRAINT ck_media_attachments_size;
ALTER TABLE media_attachments ADD CONSTRAINT ck_media_attachments_size
    CHECK (size_bytes > 0 AND size_bytes <= 209715200);   -- 200MB = MediaPolicy.VIDEO_MAX_BYTES
