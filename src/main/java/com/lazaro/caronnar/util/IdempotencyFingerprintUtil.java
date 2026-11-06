package com.lazaro.caronnar.util;

import com.lazaro.caronnar.dto.veiculo.CadastroVeiculoDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class IdempotencyFingerprintUtil {

    private static final String VERSAO_FORMATO = "v1";

    private IdempotencyFingerprintUtil() {
    }

    /**
     * Gera um fingerprint determinístico com prefixo de versão e codificação por comprimento (length-prefix),
     * eliminando qualquer ambiguidade de delimitação entre os campos relevantes da requisição.
     */
    public static String calcularFingerprint(String motoristaId, String placaCanonica, CadastroVeiculoDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Os dados do veículo não podem ser nulos.");
        }
        return calcularFingerprint(
                motoristaId,
                placaCanonica,
                dto.modelo(),
                dto.cor(),
                dto.capacidadePassageiros()
        );
    }

    /**
     * Gera um fingerprint determinístico com prefixo de versão e codificação por comprimento (length-prefix),
     * a partir dos campos individuais normalizados.
     */
    public static String calcularFingerprint(String motoristaId,
                                            String placaCanonica,
                                            String modeloBruto,
                                            String corBruta,
                                            Integer capacidadePassageiros) {
        if (motoristaId == null || placaCanonica == null) {
            throw new IllegalArgumentException("Motorista e placa canônica são obrigatórios para o cálculo do fingerprint.");
        }

        String modelo = modeloBruto != null ? modeloBruto.trim() : "";
        String cor = corBruta != null ? corBruta.trim() : "";
        String capacidade = capacidadePassageiros != null ? capacidadePassageiros.toString() : "";

        StringBuilder sb = new StringBuilder();
        sb.append(VERSAO_FORMATO).append(';');
        appendCampoComComprimento(sb, motoristaId);
        appendCampoComComprimento(sb, placaCanonica);
        appendCampoComComprimento(sb, modelo);
        appendCampoComComprimento(sb, cor);
        appendCampoComComprimento(sb, capacidade);

        return sha256Hex(sb.toString());
    }

    private static void appendCampoComComprimento(StringBuilder sb, String valor) {
        int tamanhoBytes = valor.getBytes(StandardCharsets.UTF_8).length;
        sb.append(tamanhoBytes).append(':').append(valor).append(';');
    }

    private static String sha256Hex(String entrada) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(entrada.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 não disponível no ambiente.", e);
        }
    }
}
