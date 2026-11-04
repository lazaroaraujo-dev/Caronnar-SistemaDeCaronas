package com.lazaro.caronnar.model.entities;

import com.lazaro.caronnar.model.enums.StatusReserva;
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
public class Reserva {

    @Id
    private String id;

    private String passageiroId;
    private String viagemId;

    private Integer quantidadeVagas;

    private StatusReserva status;

    private LocalDateTime dataReserva;
}