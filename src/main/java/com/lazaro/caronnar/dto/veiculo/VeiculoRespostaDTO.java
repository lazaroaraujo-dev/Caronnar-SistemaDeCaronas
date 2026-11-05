package com.lazaro.caronnar.dto.veiculo;

import io.swagger.v3.oas.annotations.media.Schema;

public record VeiculoRespostaDTO(

        @Schema(description = "Identificador único do veículo", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String id,

        @Schema(description = "Placa normalizada do veículo", example = "ABC1D23")
        String placa,

        @Schema(description = "Modelo do veículo", example = "Corolla")
        String modelo,

        @Schema(description = "Cor do veículo", example = "Prata")
        String cor,

        @Schema(description = "Capacidade de passageiros do veículo, excluindo o motorista", example = "4")
        Integer capacidadePassageiros,

        @Schema(description = "Indica se o veículo está ativo no sistema", example = "true")
        Boolean ativo
) {
}
