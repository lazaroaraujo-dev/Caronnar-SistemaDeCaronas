package com.lazaro.caronnar.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VeiculoSnapshot {

    private String placa;
    private String modelo;
    private String cor;
}
