package com.lazaro.caronnar.dto.viagem;

public record ResultadoPublicacaoViagemDTO(
        ViagemRespostaDTO resposta,
        boolean novaPublicacao
) {
}
