package com.lazaro.caronnar.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Localizacao {

    private String cidade;
    private String estado;
    private String endereco;

    private Double latitude;
    private Double longitude;
}