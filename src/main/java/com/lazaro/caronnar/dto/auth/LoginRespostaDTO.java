package com.lazaro.caronnar.dto.auth;

import java.util.Set;

public record LoginRespostaDTO(
        String id,
        String nome,
        String email,
        Set<String> papeis
) {
}
