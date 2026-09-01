package com.yeka.bandapp.recurring.dto;

import java.util.List;

/** 밴드의 활성 정기 일정 규칙 목록. 최신 등록순. */
public record RecurringRuleListResponse(
        long bandId,
        int ruleCount,
        List<RecurringRuleResponse> rules
) {
}
