package com.yeka.bandapp.notification.dto;

import com.yeka.bandapp.notification.entity.NotificationDispatch;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 알림 목록 한 페이지. 최신순이며, 다음 페이지는 {@code nextCursor}를 {@code cursor}로 다시 보낸다
 * ({@code null}이면 마지막 페이지).
 *
 * <p>읽음 여부는 서버가 갖고 있지 않다 — 클라이언트가 기기에 "마지막 확인 시각"을 저장하고
 * 그보다 새 알림을 안 읽은 것으로 센다.
 */
public record NotificationFeedResponse(
        @Schema(description = "알림 목록(최신순).")
        List<NotificationItem> notifications,

        @Schema(description = "다음 페이지 커서. null 이면 더 없음.", example = "128")
        Long nextCursor
) {

    public static NotificationFeedResponse of(List<NotificationDispatch> rows, int size) {
        boolean hasMore = rows.size() > size;
        List<NotificationDispatch> page = hasMore ? rows.subList(0, size) : rows;
        return new NotificationFeedResponse(
                page.stream().map(NotificationItem::from).toList(),
                hasMore ? page.get(page.size() - 1).getId() : null);
    }

    /** 알림 한 건. {@code type}·{@code reservationId}는 눌렀을 때 이동할 화면을 정하는 데 쓴다. */
    public record NotificationItem(
            @Schema(example = "128") long id,
            @Schema(description = "알림 종류.", example = "RESERVATION_REMINDER") String type,
            @Schema(description = "알림이 가리키는 일정 id.", example = "12") long reservationId,
            @Schema(example = "합주 리마인더") String title,
            @Schema(example = "9월 4일 19:00 합주가 60분 뒤 시작해요.") String body,
            @Schema(description = "발송 시각(UTC).") Instant sentAt
    ) {
        static NotificationItem from(NotificationDispatch d) {
            return new NotificationItem(
                    d.getId(), d.getType().name(), d.getTargetId(),
                    d.getTitle(), d.getBody(), d.getCreatedAt());
        }
    }
}
