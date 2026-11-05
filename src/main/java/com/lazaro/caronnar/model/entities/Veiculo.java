package com.lazaro.caronnar.model.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Veiculo {

    @Id
    private String id;

    private String motoristaId;
    private String placa;
    private String modelo;
    private String cor;

    /**
     * Capacidade de passageiros do veículo, excluindo o motorista (entre 1 e 8).
     */
    private Integer capacidadePassageiros;
    private Boolean ativo;
}
