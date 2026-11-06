package com.lazaro.caronnar.dto.rota;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OsrmRotaResponseDTO(
        String code,
        String message,
        List<RotaItem> routes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RotaItem(
            Double distance,
            Double duration
    ) {
    }
}
