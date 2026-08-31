package com.yeka.bandapp.band.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinBandRequest(
        @NotBlank String code
) {
}
