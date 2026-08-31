package com.yeka.bandapp.band.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBandRequest(
        @NotBlank @Size(max = 50) String name
) {
}
