package com.lazaro.caronnar.dto.usuario;

import com.lazaro.caronnar.model.enums.PapelUsuario;

import java.time.LocalDateTime;
import java.util.Set;

public record UsuarioRespostaDTO(
        String id,
        String nome,
        String email,
        String telefone,
        Set<PapelUsuario> papeis,
        LocalDateTime dataCadastro
) {
}
