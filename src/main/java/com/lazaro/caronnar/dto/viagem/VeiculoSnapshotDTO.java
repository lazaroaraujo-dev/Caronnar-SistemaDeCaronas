package com.lazaro.caronnar.dto.viagem;

import io.swagger.v3.oas.annotations.media.Schema;

public record VeiculoSnapshotDTO(

        @Schema(description = "Placa normalizada do veículo", example = "ABC1D23")
        String placa,

        @Schema(description = "Modelo do veículo", example = "Corolla")
        String modelo,

        @Schema(description = "Cor do veículo", example = "Prata")
        String cor
) {
}
