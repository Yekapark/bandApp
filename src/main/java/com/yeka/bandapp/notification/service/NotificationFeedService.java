package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.notification.dto.NotificationFeedResponse;
import com.yeka.bandapp.notification.entity.NotificationDispatch;
import com.yeka.bandapp.notification.repository.NotificationDispatchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 앱의 알림 목록. 발송 이력({@link NotificationDispatch})을 그대로 읽어 최신순으로 돌려준다.
 *
 * <p>보낸 문구를 이력에 함께 남겨 두므로 여기서 일정·정산을 다시 조회하지 않는다 — 알림이
 * 가리키던 일정이 나중에 바뀌거나 지워져도 "그때 받은 그 알림"이 그대로 남는다.
 *
 * <p>읽음 여부는 서버가 갖지 않는다. 클라이언트가 기기에 마지막 확인 시각을 저장하고 그보다
 * 새 알림을 안 읽은 것으로 센다 — 밴드 앱에 서버 읽음 상태까지 둘 이유가 없다.
 */
@Service
public class NotificationFeedService {

    /** 한 번에 돌려주는 최대 건수. 클라이언트가 더 큰 값을 보내도 여기서 자른다. */
    private static final int MAX_SIZE = 50;
    private static final int DEFAULT_SIZE = 20;

    private final NotificationDispatchRepository dispatchRepository;
    private final BandAccessGuard accessGuard;

    public NotificationFeedService(NotificationDispatchRepository dispatchRepository,
                                   BandAccessGuard accessGuard) {
        this.dispatchRepository = dispatchRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public NotificationFeedResponse feed(long bandId, long userId, Long cursor, Integer size) {
        accessGuard.requireActiveMember(bandId, userId);
        int limit = clamp(size);
        // 다음 페이지가 있는지 알아야 하므로 한 건 더 읽는다.
        List<NotificationDispatch> rows =
                dispatchRepository.findFeed(userId, bandId, cursor, PageRequest.of(0, limit + 1));
        return NotificationFeedResponse.of(rows, limit);
    }

    private static int clamp(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
