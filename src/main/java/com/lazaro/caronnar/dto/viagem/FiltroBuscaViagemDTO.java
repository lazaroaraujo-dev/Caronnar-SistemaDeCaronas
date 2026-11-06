package com.lazaro.caronnar.dto.viagem;

import java.time.LocalDate;

public record FiltroBuscaViagemDTO(
        String origemCidade,
        String origemEstado,
        String destinoCidade,
        String destinoEstado,
        LocalDate dataPartida,
        Integer vagasMinimas,
        int pagina,
        int tamanho
) {
    public FiltroBuscaViagemDTO(
            String origemCidade,
            String origemEstado,
            String destinoCidade,
            String destinoEstado,
            LocalDate dataPartida,
            int pagina,
            int tamanho
    ) {
        this(origemCidade, origemEstado, destinoCidade, destinoEstado, dataPartida, 1, pagina, tamanho);
    }
}
