package com.lazaro.caronnar.dto.veiculo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CadastroVeiculoDTO(

        @NotBlank(message = "A placa é obrigatória")
        @Pattern(regexp = "^\\s*[A-Za-z]{3}-?[0-9][A-Za-z0-9][0-9]{2}\\s*$",
                message = "A placa deve estar no formato AAA-9999 ou Mercosul AAA9A99")
        @Schema(description = "Placa do veículo no formato brasileiro antigo (AAA-9999) ou Mercosul (AAA9A99)",
                example = "ABC1D23")
        String placa,

        @NotBlank(message = "O modelo é obrigatório")
        @Schema(description = "Modelo do veículo", example = "Corolla")
        String modelo,

        @NotBlank(message = "A cor é obrigatória")
        @Schema(description = "Cor do veículo", example = "Prata")
        String cor,

        @NotNull(message = "A capacidade de passageiros é obrigatória")
        @Min(value = 1, message = "A capacidade de passageiros deve ser de pelo menos 1")
        @Max(value = 8, message = "A capacidade de passageiros deve ser de no máximo 8")
        @Schema(description = "Capacidade de passageiros do veículo, excluindo o motorista (entre 1 e 8)",
                minimum = "1", maximum = "8", example = "4")
        Integer capacidadePassageiros
) {
}
