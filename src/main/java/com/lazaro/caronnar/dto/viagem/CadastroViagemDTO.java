package com.lazaro.caronnar.dto.viagem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CadastroViagemDTO(

        @NotBlank(message = "O identificador do veículo é obrigatório")
        @Schema(description = "Identificador do veículo do motorista autenticado",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String veiculoId,

        @NotNull(message = "A localização de origem é obrigatória")
        @Valid
        @Schema(description = "Dados de localização de origem da viagem")
        LocalizacaoDTO origem,

        @NotNull(message = "A localização de destino é obrigatória")
        @Valid
        @Schema(description = "Dados de localização de destino da viagem")
        LocalizacaoDTO destino,

        @NotNull(message = "A data e hora de partida são obrigatórias")
        @Schema(description = "Data e hora de partida no fuso America/Sao_Paulo",
                example = "2026-12-01T08:30:00")
        LocalDateTime dataHoraPartida,

        @NotNull(message = "A quantidade de vagas oferecidas é obrigatória")
        @Min(value = 1, message = "A quantidade de vagas oferecidas deve ser de pelo menos 1")
        @Max(value = 8, message = "A quantidade de vagas oferecidas deve ser de no máximo 8")
        @Schema(description = "Quantidade de vagas oferecidas aos passageiros (entre 1 e a capacidadePassageiros do veículo, excluindo o motorista)",
                minimum = "1", maximum = "8", example = "3")
        Integer vagasTotais,

        @NotNull(message = "O valor por passageiro é obrigatório")
        @DecimalMin(value = "0.00", inclusive = true, message = "O valor por passageiro não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "O valor por passageiro deve ter no máximo 8 dígitos inteiros e 2 casas decimais")
        @Schema(description = "Valor cobrado por passageiro", example = "35.00")
        BigDecimal valorPorPassageiro
) {
}
