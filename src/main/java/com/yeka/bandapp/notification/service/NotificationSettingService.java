package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.notification.NotificationProperties;
import com.yeka.bandapp.notification.dto.NotificationSettingResponse;
import com.yeka.bandapp.notification.entity.NotificationSetting;
import com.yeka.bandapp.notification.repository.NotificationSettingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사용자별 알림 설정(푸시 on/off, 리마인더 시점). 순수 DB 라 일반 {@code @Transactional}이다.
 *
 * <p>설정 행은 <b>처음 조회하거나 처음 수정할 때</b> 기본값으로 만들어진다 — 가입 시점에 미리 만들지 않는다.
 */
@Service
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;
    private final NotificationProperties properties;

    public NotificationSettingService(NotificationSettingRepository settingRepository,
                                      NotificationProperties properties) {
        this.settingRepository = settingRepository;
        this.properties = properties;
    }

    /** 현재 설정. 없으면 기본값(푸시 on + 기본 리마인더 시점) 행을 만들어 반환한다. */
    @Transactional
    public NotificationSettingResponse getOrCreate(long userId) {
        return NotificationSettingResponse.from(loadOrCreate(userId));
    }

    /** 설정 변경(PUT 전체 교체). offset 목록은 여기서 정규화·검증된다(범위·개수 위반 400). */
    @Transactional
    public NotificationSettingResponse update(long userId, boolean pushEnabled, List<Integer> reminderOffsets) {
        int[] normalized = ReminderOffsets.normalize(
                reminderOffsets, properties.maxReminderOffsetMinutes(), properties.maxReminderOffsets());
        NotificationSetting setting = loadOrCreate(userId);
        setting.update(pushEnabled, normalized, Instant.now());
        return NotificationSettingResponse.from(setting);
    }

    /** {@code userIds} 중 푸시를 명시적으로 끈 사용자. 설정 행이 없으면 기본 on 이라 여기 안 들어간다. */
    @Transactional(readOnly = true)
    public Set<Long> pushDisabledUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return settingRepository.findByUserIdInAndPushEnabledFalse(userIds).stream()
                .map(NotificationSetting::getUserId)
                .collect(Collectors.toSet());
    }

    /** 리마인더 배치용 — 각 사용자의 시점 배열. 설정 행이 없는 사용자는 기본값을 채운다. */
    @Transactional(readOnly = true)
    public Map<Long, int[]> reminderOffsetsFor(Collection<Long> userIds) {
        Map<Long, int[]> stored = settingRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(NotificationSetting::getUserId, NotificationSetting::getReminderOffsets));
        int[] fallback = properties.defaultReminderOffsetsParsed();
        Map<Long, int[]> result = new HashMap<>();
        for (Long userId : userIds) {
            result.put(userId, stored.getOrDefault(userId, fallback));
        }
        return result;
    }

    private NotificationSetting loadOrCreate(long userId) {
        return settingRepository.findById(userId).orElseGet(() -> {
            try {
                return settingRepository.saveAndFlush(NotificationSetting.defaults(
                        userId, properties.defaultReminderOffsetsParsed(), Instant.now()));
            } catch (DataIntegrityViolationException race) {
                return settingRepository.findById(userId).orElseThrow(() -> race);
            }
        });
    }
}
