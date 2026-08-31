package com.yeka.bandapp.band.dto;

import jakarta.validation.constraints.NotNull;

public record DelegateLeadershipRequest(
        @NotNull Long newLeaderUserId
) {
}
