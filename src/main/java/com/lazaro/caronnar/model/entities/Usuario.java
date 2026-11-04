package com.lazaro.caronnar.model.entities;

import com.lazaro.caronnar.model.enums.PapelUsuario;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Set;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    private String id;

    private String nome;
    private String email;
    private String senha;
    private String telefone;

    private Set<PapelUsuario> papeis;

    private LocalDateTime dataCadastro;
}