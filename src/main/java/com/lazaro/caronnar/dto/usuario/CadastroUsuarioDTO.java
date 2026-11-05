package com.lazaro.caronnar.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import com.lazaro.caronnar.model.enums.PapelUsuario;

import java.util.Set;

public record CadastroUsuarioDTO(

        @NotBlank(message = "O nome é obrigatório")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail deve ser válido")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        String senha,

        String telefone,

        @NotEmpty(message = "Pelo menos um papel deve ser informado")
        Set<PapelUsuario> papeis
) {
}
