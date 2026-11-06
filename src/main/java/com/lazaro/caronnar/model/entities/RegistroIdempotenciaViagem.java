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
public class RegistroIdempotenciaViagem {

    @Id
    private String id;

    private String idempotencyKey;
    private String motoristaId;
    private String viagemId;
    private String requestFingerprint;
    private LocalDateTime dataCriacao;
}
