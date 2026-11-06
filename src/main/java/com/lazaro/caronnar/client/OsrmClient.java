package com.lazaro.caronnar.client;

import com.lazaro.caronnar.dto.rota.OsrmRotaResponseDTO;
import com.lazaro.caronnar.exception.CoordenadasInvalidasException;
import com.lazaro.caronnar.exception.RespostaExternaInvalidaException;
import com.lazaro.caronnar.exception.ServicoIndisponivelException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

@Component
public class OsrmClient {

    private static final String ROTA_URI_TEMPLATE =
            "/route/v1/driving/{longitudeOrigem},{latitudeOrigem};{longitudeDestino},{latitudeDestino}?overview=false";

    private final RestClient osrmRestClient;

    public OsrmClient(@Qualifier("osrmRestClient") RestClient osrmRestClient) {
        this.osrmRestClient = osrmRestClient;
    }

    public OsrmRotaResponseDTO consultarRota(Double longitudeOrigem,
                                             Double latitudeOrigem,
                                             Double longitudeDestino,
                                             Double latitudeDestino) {
        if (!ehCoordenadaFinita(longitudeOrigem)
                || !ehCoordenadaFinita(latitudeOrigem)
                || !ehCoordenadaFinita(longitudeDestino)
                || !ehCoordenadaFinita(latitudeDestino)) {
            throw new CoordenadasInvalidasException("Todas as coordenadas devem ser numéricas e finitas.");
        }

        try {
            OsrmRotaResponseDTO resposta = osrmRestClient.get()
                    .uri(ROTA_URI_TEMPLATE,
                            formatarCoordenada(longitudeOrigem),
                            formatarCoordenada(latitudeOrigem),
                            formatarCoordenada(longitudeDestino),
                            formatarCoordenada(latitudeDestino))
                    .retrieve()
                    .body(OsrmRotaResponseDTO.class);

            if (resposta == null) {
                throw new RespostaExternaInvalidaException("O serviço OSRM retornou uma resposta vazia.");
            }

            return resposta;

        } catch (ResourceAccessException e) {
            throw new ServicoIndisponivelException(
                    "Falha de conexão ou timeout ao consultar o serviço de rotas OSRM.", e);

        } catch (RestClientResponseException e) {
            HttpStatusCode statusCode = e.getStatusCode();

            if (statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value() || statusCode.is5xxServerError()) {
                throw new ServicoIndisponivelException(
                        "O serviço de rotas OSRM está temporariamente indisponível (HTTP " + statusCode.value() + ").", e);
            }

            if (statusCode.is4xxClientError()) {
                OsrmRotaResponseDTO corpoErro = extrairCorpoOsrm(e);
                if (corpoErro != null && corpoErro.code() != null && !corpoErro.code().isBlank()) {
                    return corpoErro;
                }
            }

            throw new RespostaExternaInvalidaException(
                    "Resposta HTTP inesperada recebida do serviço OSRM (HTTP " + statusCode.value() + ").", e);

        } catch (HttpMessageConversionException e) {
            throw new RespostaExternaInvalidaException(
                    "Resposta malformada recebida do serviço OSRM.", e);

        } catch (RestClientException e) {
            throw new RespostaExternaInvalidaException(
                    "Não foi possível processar a resposta recebida do serviço OSRM.", e);
        }
    }

    private OsrmRotaResponseDTO extrairCorpoOsrm(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(OsrmRotaResponseDTO.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean ehCoordenadaFinita(Double valor) {
        return valor != null && Double.isFinite(valor);
    }

    private String formatarCoordenada(Double valor) {
        double normalizado = valor == 0.0d ? 0.0d : valor;
        return BigDecimal.valueOf(normalizado).toPlainString();
    }
}
