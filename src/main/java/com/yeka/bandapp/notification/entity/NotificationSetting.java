package com.yeka.bandapp.notification.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 사용자별 알림 설정. {@code userId}가 곧 PK(= {@code users.id} FK)다 — 도메인 모델
 * {@code NotificationSetting { userId, pushEnabled, reminderOffsets[] }}에 id 가 없다.
 *
 * <p>{@code reminderOffsets}는 "일정 시작 N분 전"의 분 단위 정수 배열이다. PostgreSQL {@code integer[]}
 * 한 컬럼에 저장한다(별도 테이블을 만들지 않는다 — 사용자당 한 행이라 조회·수정이 단순하다).
 * 정렬·중복 제거·범위 검증은 {@code ReminderOffsets}가 저장 전에 끝낸다.
 *
 * <p>{@code save()}가 assigned-id 라 merge 경로를 타므로, 서비스는 반드시 저장 결과 인스턴스를 쓴다.
 */
@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "reminder_offsets", nullable = false)
    private int[] reminderOffsets;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 설정 변경(PUT 전체 교체). {@code offsets}는 이미 정규화된 값이어야 한다.
     *
     * <p>기본값 행은 저장소의 {@code insertDefaultsIfAbsent}(V9 컬럼 DEFAULT)가 만들고,
     * 이 엔티티는 항상 로드된 뒤에만 다뤄진다 — 그래서 앱 코드가 {@code new} 하는 팩토리가 없다.
     */
    public void update(boolean pushEnabled, int[] offsets, Instant now) {
        this.pushEnabled = pushEnabled;
        this.reminderOffsets = offsets.clone();
        this.updatedAt = now;
    }

    public int[] getReminderOffsets() {
        return reminderOffsets.clone();
    }
}
