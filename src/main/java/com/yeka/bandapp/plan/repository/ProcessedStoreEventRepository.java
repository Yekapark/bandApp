package com.yeka.bandapp.plan.repository;

import com.yeka.bandapp.plan.entity.ProcessedStoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 처리한 스토어 웹훅 메시지 저장소. {@code messageId}(PK)로만 다룬다 — {@code saveAndFlush} 가
 * {@code DataIntegrityViolationException} 을 던지면 이미 처리한 메시지다.
 */
public interface ProcessedStoreEventRepository extends JpaRepository<ProcessedStoreEvent, String> {
}
