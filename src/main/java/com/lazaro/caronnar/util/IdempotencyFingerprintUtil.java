package com.lazaro.caronnar.util;

import com.lazaro.caronnar.dto.veiculo.CadastroVeiculoDTO;
import com.lazaro.caronnar.dto.viagem.CadastroViagemDTO;
import com.lazaro.caronnar.dto.viagem.LocalizacaoDTO;
import com.lazaro.caronnar.exception.CoordenadasInvalidasException;
import com.lazaro.caronnar.exception.DataPartidaInvalidaException;
import com.lazaro.caronnar.exception.RegraNegocioViagemException;
import com.lazaro.caronnar.exception.UsuarioNaoAutenticadoException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;

public final class IdempotencyFingerprintUtil {

    private static final String VERSAO_FORMATO = "v1";
    private static final String VERSAO_FORMATO_VIAGEM = "viagem-v1";

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

    public static String calcularFingerprintViagem(String motoristaId, CadastroViagemDTO dto) {
        if (motoristaId == null || motoristaId.isBlank()) {
            throw new UsuarioNaoAutenticadoException("Usuário motorista não autenticado.");
        }
        if (dto == null) {
            throw new RegraNegocioViagemException("Os dados da viagem são obrigatórios.");
        }
        if (dto.origem() == null || dto.destino() == null) {
            throw new CoordenadasInvalidasException("A localização de origem e a de destino são obrigatórias.");
        }
        if (dto.dataHoraPartida() == null) {
            throw new DataPartidaInvalidaException(
                    "A data e hora de partida são obrigatórias e devem ser futuras no fuso America/Sao_Paulo.");
        }
        if (dto.vagasTotais() == null || dto.valorPorPassageiro() == null) {
            throw new RegraNegocioViagemException("A quantidade de vagas e o valor por passageiro são obrigatórios.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(VERSAO_FORMATO_VIAGEM).append(';');
        appendCampoComComprimento(sb, motoristaId.trim());
        appendCampoComComprimento(sb, dto.veiculoId() != null ? dto.veiculoId().trim() : "");
        appendLocalizacao(sb, dto.origem(), "origem");
        appendLocalizacao(sb, dto.destino(), "destino");
        LocalDateTime partidaNormalizada = dto.dataHoraPartida().truncatedTo(ChronoUnit.MILLIS);
        appendCampoComComprimento(sb, partidaNormalizada.toString());
        appendCampoComComprimento(sb, dto.vagasTotais().toString());
        appendCampoComComprimento(sb, normalizarValorMonetario(dto.valorPorPassageiro()));

        return sha256Hex(sb.toString());
    }

    private static void appendLocalizacao(StringBuilder sb, LocalizacaoDTO loc, String ponto) {
        String cidade = loc.cidade() != null ? loc.cidade().trim() : "";
        String estado = loc.estado() != null ? loc.estado().trim().toUpperCase(Locale.ROOT) : "";
        String endereco = loc.endereco() != null ? loc.endereco().trim() : "";
        String latitude = formatarLatitudeDeterministica(loc.latitude(), ponto);
        String longitude = formatarLongitudeDeterministica(loc.longitude(), ponto);

        appendCampoComComprimento(sb, cidade);
        appendCampoComComprimento(sb, estado);
        appendCampoComComprimento(sb, endereco);
        appendCampoComComprimento(sb, latitude);
        appendCampoComComprimento(sb, longitude);
    }

    private static String formatarLatitudeDeterministica(Double latitude, String ponto) {
        if (latitude == null || !Double.isFinite(latitude)) {
            throw new CoordenadasInvalidasException(
                    "A latitude de " + ponto + " é obrigatória e deve ser um número finito.");
        }
        if (latitude < -90.0d || latitude > 90.0d) {
            throw new CoordenadasInvalidasException(
                    "A latitude de " + ponto + " deve estar entre -90 e 90 graus.");
        }
        double normalizada = latitude == 0.0d ? 0.0d : latitude;
        return BigDecimal.valueOf(normalizada).stripTrailingZeros().toPlainString();
    }

    private static String formatarLongitudeDeterministica(Double longitude, String ponto) {
        if (longitude == null || !Double.isFinite(longitude)) {
            throw new CoordenadasInvalidasException(
                    "A longitude de " + ponto + " é obrigatória e deve ser um número finito.");
        }
        if (longitude < -180.0d || longitude > 180.0d) {
            throw new CoordenadasInvalidasException(
                    "A longitude de " + ponto + " deve estar entre -180 e 180 graus.");
        }
        double normalizada = longitude == 0.0d ? 0.0d : longitude;
        return BigDecimal.valueOf(normalizada).stripTrailingZeros().toPlainString();
    }

    private static String normalizarValorMonetario(BigDecimal valor) {
        if (valor.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return valor.stripTrailingZeros().toPlainString();
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
