package com.lazaro.caronnar.dto.viagem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LocalizacaoDTO(

        @NotBlank(message = "A cidade é obrigatória")
        @Schema(description = "Nome da cidade", example = "São Paulo")
        String cidade,

        @NotBlank(message = "O estado é obrigatório")
        @Schema(description = "Sigla ou nome do estado", example = "SP")
        String estado,

        @NotBlank(message = "O endereço é obrigatório")
        @Schema(description = "Endereço ou ponto de referência", example = "Av. Paulista, 1000")
        String endereco,

        @NotNull(message = "A latitude é obrigatória")
        @DecimalMin(value = "-90.0", message = "A latitude deve ser no mínimo -90.0")
        @DecimalMax(value = "90.0", message = "A latitude deve ser no máximo 90.0")
        @Schema(description = "Latitude em graus decimais (-90.0 a 90.0)", example = "-23.564886")
        Double latitude,

        @NotNull(message = "A longitude é obrigatória")
        @DecimalMin(value = "-180.0", message = "A longitude deve ser no mínimo -180.0")
        @DecimalMax(value = "180.0", message = "A longitude deve ser no máximo 180.0")
        @Schema(description = "Longitude em graus decimais (-180.0 a 180.0)", example = "-46.651919")
        Double longitude
) {
}
