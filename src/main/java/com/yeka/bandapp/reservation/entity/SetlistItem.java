package com.yeka.bandapp.reservation.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 일정의 셋리스트 한 곡. 곡명·아티스트·참고 링크·순서(BUILD_PLAN 도메인 모델 {@code SetlistItem}).
 *
 * <p>{@code orderNo}는 1부터 서버가 부여하고, 재정렬 API 가 1..N 을 다시 매긴다. 같은 곡을 두 번 넣거나
 * 하는 것은 막지 않는다(자유 기재).
 */
@Entity
@Table(name = "setlist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetlistItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 200)
    private String artist;

    @Column(name = "reference_url", length = 2000)
    private String referenceUrl;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    private SetlistItem(Long reservationId, String title, String artist, String referenceUrl, int orderNo) {
        this.reservationId = reservationId;
        this.title = title;
        this.artist = artist;
        this.referenceUrl = referenceUrl;
        this.orderNo = orderNo;
    }

    /** 새 곡. {@code orderNo}는 호출 측(서비스)이 현재 마지막 다음 번호로 넘긴다. */
    public static SetlistItem create(long reservationId, String title, String artist,
                                     String referenceUrl, int orderNo) {
        return new SetlistItem(reservationId, title, artist, referenceUrl, orderNo);
    }

    /** 곡 정보 수정. 순서는 재정렬 API 로 따로 바꾼다. */
    public void edit(String title, String artist, String referenceUrl) {
        this.title = title;
        this.artist = artist;
        this.referenceUrl = referenceUrl;
    }

    /** 재정렬 시 새 순서 번호로 옮긴다. */
    public void moveTo(int orderNo) {
        this.orderNo = orderNo;
    }
}
