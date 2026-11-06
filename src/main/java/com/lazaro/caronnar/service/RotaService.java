package com.lazaro.caronnar.service;

import com.lazaro.caronnar.client.OsrmClient;
import com.lazaro.caronnar.dto.rota.CalculoRotaDTO;
import com.lazaro.caronnar.dto.rota.OsrmRotaResponseDTO;
import com.lazaro.caronnar.exception.CoordenadasInvalidasException;
import com.lazaro.caronnar.exception.RespostaExternaInvalidaException;
import com.lazaro.caronnar.exception.RotaNaoEncontradaException;
import com.lazaro.caronnar.model.embedded.Localizacao;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RotaService {

    private static final double LATITUDE_MINIMA = -90.0;
    private static final double LATITUDE_MAXIMA = 90.0;
    private static final double LONGITUDE_MINIMA = -180.0;
    private static final double LONGITUDE_MAXIMA = 180.0;

    private final OsrmClient osrmClient;

    public RotaService(OsrmClient osrmClient) {
        this.osrmClient = osrmClient;
    }

    public CalculoRotaDTO calcularRota(Localizacao origem, Localizacao destino) {
        validarLocalizacoes(origem, destino);
        return calcularRota(
                origem.getLatitude(),
                origem.getLongitude(),
                destino.getLatitude(),
                destino.getLongitude()
        );
    }

    public void validarLocalizacoes(Localizacao origem, Localizacao destino) {
        if (origem == null || destino == null) {
            throw new CoordenadasInvalidasException("A localização de origem e a de destino são obrigatórias.");
        }
        validarCoordenadas(
                origem.getLatitude(),
                origem.getLongitude(),
                destino.getLatitude(),
                destino.getLongitude()
        );
    }

    public CalculoRotaDTO calcularRota(Double latitudeOrigem,
                                       Double longitudeOrigem,
                                       Double latitudeDestino,
                                       Double longitudeDestino) {
        validarCoordenadas(latitudeOrigem, longitudeOrigem, latitudeDestino, longitudeDestino);

        OsrmRotaResponseDTO resposta = osrmClient.consultarRota(
                longitudeOrigem,
                latitudeOrigem,
                longitudeDestino,
                latitudeDestino
        );

        if (resposta == null || resposta.code() == null || resposta.code().isBlank()) {
            throw new RespostaExternaInvalidaException("A resposta do serviço OSRM não contém o código de status esperado.");
        }

        String codigo = resposta.code().trim();

        if ("NoRoute".equalsIgnoreCase(codigo)) {
            throw new RotaNaoEncontradaException(
                    "Não foi possível encontrar uma rota dirigível entre a origem e o destino informados.");
        }

        if ("InvalidCoordinate".equalsIgnoreCase(codigo)
                || "InvalidValue".equalsIgnoreCase(codigo)
                || "InvalidQuery".equalsIgnoreCase(codigo)) {
            throw new CoordenadasInvalidasException(
                    "As coordenadas informadas foram consideradas inválidas pelo serviço de rotas.");
        }

        if (!"Ok".equalsIgnoreCase(codigo)) {
            throw new RespostaExternaInvalidaException(
                    "Código de resposta inválido ou desconhecido retornado pelo OSRM: " + codigo);
        }

        if (resposta.routes() == null || resposta.routes().isEmpty() || resposta.routes().getFirst() == null) {
            throw new RespostaExternaInvalidaException(
                    "A resposta do OSRM indicou sucesso, mas não retornou nenhuma rota.");
        }

        OsrmRotaResponseDTO.RotaItem rota = resposta.routes().getFirst();
        Double distanciaMetros = rota.distance();
        Double duracaoSegundos = rota.duration();

        if (distanciaMetros == null || !Double.isFinite(distanciaMetros) || distanciaMetros < 0.0) {
            throw new RespostaExternaInvalidaException(
                    "A distância retornada pelo serviço OSRM é ausente ou inválida.");
        }

        if (duracaoSegundos == null || !Double.isFinite(duracaoSegundos) || duracaoSegundos < 0.0) {
            throw new RespostaExternaInvalidaException(
                    "A duração retornada pelo serviço OSRM é ausente ou inválida.");
        }

        double distanciaKm = BigDecimal.valueOf(distanciaMetros)
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                .doubleValue();

        double minutosArredondadosParaCima = Math.ceil(duracaoSegundos / 60.0);
        if (minutosArredondadosParaCima > Integer.MAX_VALUE) {
            throw new RespostaExternaInvalidaException(
                    "A duração retornada pelo serviço OSRM excede o limite suportado.");
        }
        int duracaoMinutos = (int) minutosArredondadosParaCima;

        return new CalculoRotaDTO(distanciaKm, duracaoMinutos);
    }

    private void validarCoordenadas(Double latitudeOrigem,
                                    Double longitudeOrigem,
                                    Double latitudeDestino,
                                    Double longitudeDestino) {
        validarLatitude(latitudeOrigem, "origem");
        validarLongitude(longitudeOrigem, "origem");
        validarLatitude(latitudeDestino, "destino");
        validarLongitude(longitudeDestino, "destino");

        if (latitudeOrigem.doubleValue() == latitudeDestino.doubleValue()
                && longitudeOrigem.doubleValue() == longitudeDestino.doubleValue()) {
            throw new CoordenadasInvalidasException(
                    "As coordenadas de origem e destino não podem ser idênticas.");
        }
    }

    private void validarLatitude(Double latitude, String ponto) {
        if (latitude == null || !Double.isFinite(latitude)) {
            throw new CoordenadasInvalidasException(
                    "A latitude de " + ponto + " é obrigatória e deve ser um número finito.");
        }
        if (latitude < LATITUDE_MINIMA || latitude > LATITUDE_MAXIMA) {
            throw new CoordenadasInvalidasException(
                    "A latitude de " + ponto + " deve estar entre -90 e 90 graus.");
        }
    }

    private void validarLongitude(Double longitude, String ponto) {
        if (longitude == null || !Double.isFinite(longitude)) {
            throw new CoordenadasInvalidasException(
                    "A longitude de " + ponto + " é obrigatória e deve ser um número finito.");
        }
        if (longitude < LONGITUDE_MINIMA || longitude > LONGITUDE_MAXIMA) {
            throw new CoordenadasInvalidasException(
                    "A longitude de " + ponto + " deve estar entre -180 e 180 graus.");
        }
    }
}
