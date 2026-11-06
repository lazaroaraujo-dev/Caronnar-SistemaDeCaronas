package com.lazaro.caronnar.dto.viagem;

import com.lazaro.caronnar.model.enums.StatusViagem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ViagemRespostaDTO(

        @Schema(description = "Identificador único da viagem", example = "9c42e8a1-4b11-4562-b3fc-2c963f66afa6")
        String id,

        @Schema(description = "Identificador do motorista responsável pela viagem", example = "usuario-1")
        String motoristaId,

        @Schema(description = "Identificador do veículo utilizado na viagem", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String veiculoId,

        @Schema(description = "Snapshot dos dados do veículo no momento da publicação")
        VeiculoSnapshotDTO veiculoSnapshot,

        @Schema(description = "Localização de origem da viagem")
        LocalizacaoDTO origem,

        @Schema(description = "Localização de destino da viagem")
        LocalizacaoDTO destino,

        @Schema(description = "Data e hora de partida no fuso America/Sao_Paulo", example = "2026-12-01T08:30:00")
        LocalDateTime dataHoraPartida,

        @Schema(description = "Quantidade total de vagas oferecidas aos passageiros", example = "3")
        Integer vagasTotais,

        @Schema(description = "Quantidade atual de vagas disponíveis para reserva", example = "3")
        Integer vagasDisponiveis,

        @Schema(description = "Valor por passageiro", example = "35.00")
        BigDecimal valorPorPassageiro,

        @Schema(description = "Distância estimada da rota em quilômetros", example = "98.45")
        Double distanciaKm,

        @Schema(description = "Duração estimada da rota em minutos", example = "78")
        Integer duracaoMinutos,

        @Schema(description = "Status atual da viagem", example = "AGENDADA")
        StatusViagem status,

        @Schema(description = "Data e hora de criação da viagem no fuso America/Sao_Paulo", example = "2026-11-05T22:55:00")
        LocalDateTime dataCriacao
) {
}
