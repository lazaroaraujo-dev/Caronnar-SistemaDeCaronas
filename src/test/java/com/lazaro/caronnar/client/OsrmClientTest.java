package com.lazaro.caronnar.client;

import com.lazaro.caronnar.dto.rota.OsrmRotaResponseDTO;
import com.lazaro.caronnar.exception.CoordenadasInvalidasException;
import com.lazaro.caronnar.exception.RespostaExternaInvalidaException;
import com.lazaro.caronnar.exception.RotaNaoEncontradaException;
import com.lazaro.caronnar.exception.ServicoIndisponivelException;
import com.lazaro.caronnar.service.RotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OsrmClientTest {

    private static final String BASE_URL = "http://osrm.local";

    private MockRestServiceServer mockServer;
    private OsrmClient osrmClient;
    private RotaService rotaService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        osrmClient = new OsrmClient(builder.build());
        rotaService = new RotaService(osrmClient);
    }

    @Test
    void deveEnviarRequisicaoComOrdemLongitudeLatitudeEReceberResposta200Valida() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.633308,-23.55052;-43.172896,-22.906847?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "code": "Ok",
                          "routes": [
                            {
                              "distance": 435678.9,
                              "duration": 19810.4,
                              "weight": 19810.4
                            }
                          ],
                          "waypoints": []
                        }
                        """, MediaType.APPLICATION_JSON));

        OsrmRotaResponseDTO resposta = osrmClient.consultarRota(-46.633308, -23.55052, -43.172896, -22.906847);

        mockServer.verify();
        assertNotNull(resposta);
        assertEquals("Ok", resposta.code());
        assertEquals(1, resposta.routes().size());
        assertEquals(435678.9, resposta.routes().getFirst().distance());
        assertEquals(19810.4, resposta.routes().getFirst().duration());
    }

    @Test
    void deveFormatarCoordenadasComPontoDecimalIndependenteDoLocaleDoSistema() {
        Locale localeOriginal = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("pt", "BR"));

            mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.633308,-23.55052;0.000012,-0.000034?overview=false"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {
                              "code": "Ok",
                              "routes": [
                                {
                                  "distance": 1500.0,
                                  "duration": 120.0
                                }
                              ]
                            }
                            """, MediaType.APPLICATION_JSON));

            OsrmRotaResponseDTO resposta = osrmClient.consultarRota(-46.633308, -23.55052, 0.000012, -0.000034);

            mockServer.verify();
            assertEquals("Ok", resposta.code());
        } finally {
            Locale.setDefault(localeOriginal);
        }
    }

    @Test
    void devePreservarCorpoNoRouteEmHttp400EPermitirClassificacaoComoRotaNaoEncontrada() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "NoRoute",
                                  "message": "Impossible route between points"
                                }
                                """));

        assertThrows(RotaNaoEncontradaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
        mockServer.verify();
    }

    @Test
    void devePreservarCodigoInvalidCoordinateEmHttp400EPermitirClassificacaoComoCoordenadasInvalidas() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "InvalidCoordinate",
                                  "message": "Invalid coordinate value."
                                }
                                """));

        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
        mockServer.verify();
    }

    @Test
    void deveLancarRespostaExternaInvalidaExceptionQuandoHttp400NaoPossuirCorpoOsrmValido() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Bad Request Gateway Error"));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
        mockServer.verify();
    }

    @Test
    void deveLancarRespostaExternaInvalidaExceptionQuandoHttp400TiverCorpoVazioEJamaisConfundirComNoRoute() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
        mockServer.verify();
    }

    @Test
    void deveLancarRespostaExternaInvalidaExceptionParaHttp404GenericoSemClassificarComoNoRoute() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><body>404 Not Found</body></html>"));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
        mockServer.verify();
    }

    @Test
    void deveLancarServicoIndisponivelExceptionQuandoOsrmRetornarHttp429() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(ServicoIndisponivelException.class,
                () -> osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90));
        mockServer.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void deveLancarServicoIndisponivelExceptionQuandoOsrmRetornarHttp5xx(int statusHttp) {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.valueOf(statusHttp)));

        assertThrows(ServicoIndisponivelException.class,
                () -> osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90));
        mockServer.verify();
    }

    @Test
    void deveLancarServicoIndisponivelExceptionEmCasoDeTimeoutDeLeitura() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(ServicoIndisponivelException.class,
                () -> osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90));
        mockServer.verify();
    }

    @Test
    void deveLancarServicoIndisponivelExceptionEmCasoDeFalhaDeConexao() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThrows(ServicoIndisponivelException.class,
                () -> osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90));
        mockServer.verify();
    }

    @Test
    void deveLancarRespostaExternaInvalidaExceptionQuandoHttp200RetornarJsonMalformado() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{json-malformado", MediaType.APPLICATION_JSON));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90));
        mockServer.verify();
    }

    @Test
    void deveLancarRespostaExternaInvalidaExceptionQuandoHttp200RetornarCorpoVazio() {
        mockServer.expect(requestTo(BASE_URL + "/route/v1/driving/-46.63,-23.55;-43.17,-22.9?overview=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90));
        mockServer.verify();
    }

    @Test
    void deveRejeitarCoordenadasNulasOuNaoFinitasNoCliente() {
        assertThrows(CoordenadasInvalidasException.class,
                () -> osrmClient.consultarRota(null, -23.55, -43.17, -22.90));
        assertThrows(CoordenadasInvalidasException.class,
                () -> osrmClient.consultarRota(Double.NaN, -23.55, -43.17, -22.90));
        assertThrows(CoordenadasInvalidasException.class,
                () -> osrmClient.consultarRota(-46.63, Double.POSITIVE_INFINITY, -43.17, -22.90));
    }
}
