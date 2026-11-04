package com.lazaro.caronnar.model.entities;

import com.lazaro.caronnar.model.embedded.Localizacao;
import com.lazaro.caronnar.model.enums.StatusViagem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Viagem {

    @Id
    private String id;

    private String motoristaId;

    private Localizacao origem;
    private Localizacao destino;

    private LocalDateTime dataHoraPartida;

    private Integer vagasTotais;
    private Integer vagasDisponiveis;

    private BigDecimal valorPorPassageiro;

    private Double distanciaKm;
    private Integer duracaoMinutos;

    private StatusViagem status;
}