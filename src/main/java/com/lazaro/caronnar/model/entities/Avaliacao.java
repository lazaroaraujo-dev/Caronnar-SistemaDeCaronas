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
public class Avaliacao {

    @Id
    private String id;

    private String avaliadorId;
    private String avaliadoId;
    private String viagemId;

    private Integer nota;
    private String comentario;

    private LocalDateTime dataAvaliacao;
}