package com.yeka.bandapp.recurring.schedule;

import com.yeka.bandapp.recurring.service.RecurringRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 만료가 다가온(지평선 끝에 닿은) 정기 규칙의 회차를 이어서 만드는 배치.
 * cron/zone 은 {@code app.recurring.*}에서 온다. 테스트에서는 {@code extend-cron="-"}로 비활성화하고
 * {@link RecurringRuleService#extendRule}을 직접 호출해 검증한다.
 *
 * <p>규칙마다 별도 트랜잭션({@code extendRule})으로 처리해, 한 규칙이 실패해도 나머지는 계속 이어간다.
 * 단일 VM 운영 전제라 분산 락은 없다({@code SchedulingConfig} 참조) — 다중 인스턴스로 확장하면
 * 중복 실행 방지가 필요하다.
 */
@Component
public class RecurringExtensionJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringExtensionJob.class);

    private final RecurringRuleService recurringRuleService;

    public RecurringExtensionJob(RecurringRuleService recurringRuleService) {
        this.recurringRuleService = recurringRuleService;
    }

    @Scheduled(cron = "${app.recurring.extend-cron}", zone = "${app.recurring.zone}")
    public void extend() {
        int total = 0;
        int rules = 0;
        long afterId = 0;
        while (true) {
            List<Long> page = recurringRuleService.activeRuleIdsAfter(afterId);
            if (page.isEmpty()) {
                break;
            }
            for (Long ruleId : page) {
                rules++;
                try {
                    total += recurringRuleService.extendRule(ruleId);
                } catch (RuntimeException e) {
                    log.warn("정기 일정 회차 연장 실패 ruleId={}", ruleId, e);
                }
                afterId = ruleId;
            }
            if (page.size() < RecurringRuleService.EXTEND_PAGE_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("정기 일정 회차 연장 완료 rules={} created={}", rules, total);
        }
    }
}
