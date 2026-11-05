package com.lazaro.caronnar.dto.veiculo;

import jakarta.validation.constraints.NotBlank;

public record CadastroVeiculoDTO(

        @NotBlank(message = "A placa é obrigatória")
        String placa,

        @NotBlank(message = "O modelo é obrigatório")
        String modelo,

        @NotBlank(message = "A cor é obrigatória")
        String cor
) {
}
