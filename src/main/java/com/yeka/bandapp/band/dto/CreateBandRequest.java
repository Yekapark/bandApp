package com.yeka.bandapp.band.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBandRequest(
        @Schema(description = "밴드 이름. 1~50자.", example = "Rose Motel")
        @NotBlank @Size(max = 50) String name
) {
}
