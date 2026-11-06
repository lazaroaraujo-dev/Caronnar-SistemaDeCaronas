package com.lazaro.caronnar.service;

import com.lazaro.caronnar.client.OsrmClient;
import com.lazaro.caronnar.dto.rota.CalculoRotaDTO;
import com.lazaro.caronnar.dto.rota.OsrmRotaResponseDTO;
import com.lazaro.caronnar.exception.CoordenadasInvalidasException;
import com.lazaro.caronnar.exception.RespostaExternaInvalidaException;
import com.lazaro.caronnar.exception.RotaNaoEncontradaException;
import com.lazaro.caronnar.model.embedded.Localizacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RotaServiceTest {

    @Mock
    private OsrmClient osrmClient;

    private RotaService rotaService;

    @BeforeEach
    void setUp() {
        rotaService = new RotaService(osrmClient);
    }

    @Test
    void deveCalcularRotaComSucessoAPartirDeLocalizacoesValidas() {
        Localizacao origem = new Localizacao("São Paulo", "SP", "Praça da Sé", -23.55052, -46.633308);
        Localizacao destino = new Localizacao("Rio de Janeiro", "RJ", "Centro", -22.906847, -43.172896);

        OsrmRotaResponseDTO respostaOsrm = new OsrmRotaResponseDTO(
                "Ok",
                null,
                List.of(new OsrmRotaResponseDTO.RotaItem(435678.9, 19810.0))
        );

        when(osrmClient.consultarRota(-46.633308, -23.55052, -43.172896, -22.906847))
                .thenReturn(respostaOsrm);

        CalculoRotaDTO resultado = rotaService.calcularRota(origem, destino);

        assertNotNull(resultado);
        assertEquals(435.68, resultado.distanciaKm());
        assertEquals(331, resultado.duracaoMinutos());
        verify(osrmClient).consultarRota(-46.633308, -23.55052, -43.172896, -22.906847);
    }

    @Test
    void deveConverterMetrosParaQuilometrosEArredondarSegundosParaMinutosParaCima() {
        OsrmRotaResponseDTO respostaOsrm = new OsrmRotaResponseDTO(
                "Ok",
                null,
                List.of(new OsrmRotaResponseDTO.RotaItem(12345.0, 60.1))
        );

        when(osrmClient.consultarRota(-46.6, -23.5, -46.7, -23.6))
                .thenReturn(respostaOsrm);

        CalculoRotaDTO resultado = rotaService.calcularRota(-23.5, -46.6, -23.6, -46.7);

        assertEquals(12.35, resultado.distanciaKm());
        assertEquals(2, resultado.duracaoMinutos());
    }

    @Test
    void deveArredondarDuracaoMenorQueSessentaSegundosParaUmMinuto() {
        OsrmRotaResponseDTO respostaOsrm = new OsrmRotaResponseDTO(
                "Ok",
                null,
                List.of(new OsrmRotaResponseDTO.RotaItem(550.0, 1.0))
        );

        when(osrmClient.consultarRota(-46.6, -23.5, -46.61, -23.51))
                .thenReturn(respostaOsrm);

        CalculoRotaDTO resultado = rotaService.calcularRota(-23.5, -46.6, -23.51, -46.61);

        assertEquals(0.55, resultado.distanciaKm());
        assertEquals(1, resultado.duracaoMinutos());
    }

    @Test
    void deveManterDuracaoExataQuandoMultiploDeSessentaSegundos() {
        OsrmRotaResponseDTO respostaOsrm = new OsrmRotaResponseDTO(
                "Ok",
                null,
                List.of(new OsrmRotaResponseDTO.RotaItem(2000.0, 120.0))
        );

        when(osrmClient.consultarRota(-46.6, -23.5, -46.62, -23.52))
                .thenReturn(respostaOsrm);

        CalculoRotaDTO resultado = rotaService.calcularRota(-23.5, -46.6, -23.52, -46.62);

        assertEquals(2.0, resultado.distanciaKm());
        assertEquals(2, resultado.duracaoMinutos());
    }

    @Test
    void devePermitirCoordenadasDistintasMuitoProximasSemToleranciaArbitraria() {
        OsrmRotaResponseDTO respostaOsrm = new OsrmRotaResponseDTO(
                "Ok",
                null,
                List.of(new OsrmRotaResponseDTO.RotaItem(10.0, 5.0))
        );

        when(osrmClient.consultarRota(-46.633308, -23.550520, -46.633308, -23.550521))
                .thenReturn(respostaOsrm);

        CalculoRotaDTO resultado = rotaService.calcularRota(-23.550520, -46.633308, -23.550521, -46.633308);

        assertEquals(0.01, resultado.distanciaKm());
        assertEquals(1, resultado.duracaoMinutos());
    }

    @Test
    void deveRejeitarLocalizacaoOrigemOuDestinoNula() {
        Localizacao localizacaoValida = new Localizacao("São Paulo", "SP", "Centro", -23.55, -46.63);

        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(null, localizacaoValida));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(localizacaoValida, null));

        verifyNoInteractions(osrmClient);
    }

    @Test
    void deveRejeitarCoordenadasNulas() {
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(null, -46.63, -22.90, -43.17));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, null, -22.90, -43.17));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, null, -43.17));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, null));

        verifyNoInteractions(osrmClient);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void deveRejeitarCoordenadasNaoFinitas(double valorInvalido) {
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(valorInvalido, -46.63, -22.90, -43.17));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, valorInvalido, -22.90, -43.17));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, valorInvalido, -43.17));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, valorInvalido));

        verifyNoInteractions(osrmClient);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.0001, -120.0, 90.0001, 180.0})
    void deveRejeitarLatitudeForaDosLimites(double latitudeInvalida) {
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(latitudeInvalida, -46.63, -22.90, -43.17));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, latitudeInvalida, -43.17));

        verifyNoInteractions(osrmClient);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.0001, -200.0, 180.0001, 360.0})
    void deveRejeitarLongitudeForaDosLimites(double longitudeInvalida) {
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, longitudeInvalida, -22.90, -43.17));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, longitudeInvalida));

        verifyNoInteractions(osrmClient);
    }

    @Test
    void deveRejeitarOrigemEDestinoIdenticos() {
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55052, -46.633308, -23.55052, -46.633308));
        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-0.0, 0.0, 0.0, -0.0));

        verifyNoInteractions(osrmClient);
    }

    @Test
    void deveLancarRotaNaoEncontradaExceptionQuandoOsrmRetornarNoRoute() {
        OsrmRotaResponseDTO respostaNoRoute = new OsrmRotaResponseDTO(
                "NoRoute",
                "Impossible route between points",
                Collections.emptyList()
        );

        when(osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90))
                .thenReturn(respostaNoRoute);

        assertThrows(RotaNaoEncontradaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
    }

    @ParameterizedTest
    @ValueSource(strings = {"InvalidCoordinate", "InvalidValue", "InvalidQuery"})
    void deveLancarCoordenadasInvalidasExceptionQuandoOsrmRejeitarCoordenadas(String codigoOsrm) {
        OsrmRotaResponseDTO respostaErro = new OsrmRotaResponseDTO(
                codigoOsrm,
                "Invalid coordinate parameter",
                null
        );

        when(osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90))
                .thenReturn(respostaErro);

        assertThrows(CoordenadasInvalidasException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UnknownError", "InvalidOptions", "TooBig", "   "})
    void deveLancarRespostaExternaInvalidaExceptionParaCodigoDesconhecidoOuEmBranco(String codigoInvalido) {
        OsrmRotaResponseDTO resposta = new OsrmRotaResponseDTO(
                codigoInvalido,
                "Unexpected status",
                List.of(new OsrmRotaResponseDTO.RotaItem(1000.0, 60.0))
        );

        when(osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90))
                .thenReturn(resposta);

        assertThrows(RespostaExternaInvalidaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
    }

    @Test
    void deveLancarRespostaExternaInvalidaExceptionQuandoRespostaOuCodigoForNulo() {
        when(osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90))
                .thenReturn(new OsrmRotaResponseDTO(null, null, null));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
    }

    @Test
    void deveLancarRespostaExternaInvalidaExceptionQuandoListaDeRotasForNulaOuVazia() {
        when(osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90))
                .thenReturn(new OsrmRotaResponseDTO("Ok", null, null));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));

        when(osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90))
                .thenReturn(new OsrmRotaResponseDTO("Ok", null, Collections.emptyList()));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));

        when(osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90))
                .thenReturn(new OsrmRotaResponseDTO("Ok", null, Collections.singletonList(null)));

        assertThrows(RespostaExternaInvalidaException.class,
                () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
    }

    @Test
    void deveLancarRespostaExternaInvalidaExceptionQuandoDistanciaOuDuracaoForNulaNegativaOuNaoFinita() {
        List<OsrmRotaResponseDTO.RotaItem> itensInvalidos = List.of(
                new OsrmRotaResponseDTO.RotaItem(null, 120.0),
                new OsrmRotaResponseDTO.RotaItem(1000.0, null),
                new OsrmRotaResponseDTO.RotaItem(-1.0, 120.0),
                new OsrmRotaResponseDTO.RotaItem(1000.0, -5.0),
                new OsrmRotaResponseDTO.RotaItem(Double.NaN, 120.0),
                new OsrmRotaResponseDTO.RotaItem(1000.0, Double.NaN),
                new OsrmRotaResponseDTO.RotaItem(Double.POSITIVE_INFINITY, 120.0),
                new OsrmRotaResponseDTO.RotaItem(1000.0, Double.POSITIVE_INFINITY)
        );

        for (OsrmRotaResponseDTO.RotaItem itemInvalido : itensInvalidos) {
            when(osrmClient.consultarRota(-46.63, -23.55, -43.17, -22.90))
                    .thenReturn(new OsrmRotaResponseDTO("Ok", null, List.of(itemInvalido)));

            assertThrows(RespostaExternaInvalidaException.class,
                    () -> rotaService.calcularRota(-23.55, -46.63, -22.90, -43.17));
        }
    }
}
