package com.yeka.bandapp.recurring.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 반복 합주 규칙. {@code Reservation}·{@code Room}과 같은 스타일 — 연관관계 매핑 없이 {@code Long} FK,
 * 정적 팩토리, 의미 있는 메서드로만 상태를 바꾼다.
 *
 * <p>{@code dayOfWeek}·{@code startTime}·{@code endTime}은 로컬(Asia/Seoul) 기준이다. 회차의 UTC
 * 시각은 {@code app.recurring.zone}으로 변환해 계산한다.
 *
 * <p>삭제는 {@link #delete}로 {@code deletedAt}을 찍는 소프트 삭제다. 과거 회차가 이 규칙을 계속
 * 참조할 수 있어야 하기 때문이다({@code Room}과 같은 이유).
 */
@Entity
@Table(name = "recurring_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "band_id", nullable = false)
    private Long bandId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 종료일. {@code null}이면 종료일 없음 — 배치가 계속 이어서 회차를 만든다. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 생성되는 모든 회차에 복사되는 비용(원). 참고용이며 정산 입력이 아니다. */
    private Integer cost;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private RecurringRule(Long bandId, Long roomId, RecurringFrequency frequency, DayOfWeek dayOfWeek,
                          LocalTime startTime, LocalTime endTime, LocalDate startDate, LocalDate endDate,
                          Integer cost, String note, Long createdBy) {
        this.bandId = bandId;
        this.roomId = roomId;
        this.frequency = frequency;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startDate = startDate;
        this.endDate = endDate;
        this.cost = cost;
        this.note = note;
        this.createdBy = createdBy;
    }

    public static RecurringRule create(long bandId, long roomId, RecurringFrequency frequency,
                                       DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                                       LocalDate startDate, LocalDate endDate, Integer cost, String note,
                                       long createdBy) {
        return new RecurringRule(bandId, roomId, frequency, dayOfWeek, startTime, endTime,
                startDate, endDate, cost, note, createdBy);
    }

    /** 소프트 삭제. 이미 삭제돼 있으면 아무 일도 하지 않는다. */
    public void delete(Instant when) {
        if (deletedAt == null) {
            this.deletedAt = when;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean belongsTo(long bandId) {
        return this.bandId != null && this.bandId == bandId;
    }

    public boolean isCreatedBy(long userId) {
        return this.createdBy != null && this.createdBy == userId;
    }
}
