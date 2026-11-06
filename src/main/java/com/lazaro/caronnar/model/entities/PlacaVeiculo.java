package com.lazaro.caronnar.model.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

import java.time.LocalDateTime;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlacaVeiculo {

    @Id
    private String id; // "placa::" + chaveCanonica

    private String veiculoId;
    private String motoristaId;
    private String idempotencyKey;
    private LocalDateTime dataRegistro;
}
