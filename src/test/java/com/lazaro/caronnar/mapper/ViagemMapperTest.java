package com.lazaro.caronnar.mapper;

import com.couchbase.client.java.json.JsonObject;
import com.lazaro.caronnar.dto.rota.CalculoRotaDTO;
import com.lazaro.caronnar.dto.viagem.CadastroViagemDTO;
import com.lazaro.caronnar.dto.viagem.LocalizacaoDTO;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.model.entities.Viagem;
import com.lazaro.caronnar.model.enums.StatusViagem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.couchbase.core.convert.CouchbaseCustomConversions;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.convert.translation.JacksonTranslationService;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViagemMapperTest {

    private TimeZone fusoOriginalJvm;
    private MappingCouchbaseConverter couchbaseConverter;
    private JacksonTranslationService translationService;

    @BeforeEach
    void setUp() {
        fusoOriginalJvm = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));

        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbaseCustomConversions customConversions = new CouchbaseCustomConversions(Collections.emptyList());
        couchbaseConverter = new MappingCouchbaseConverter(mappingContext, null, customConversions);
        couchbaseConverter.afterPropertiesSet();

        translationService = new JacksonTranslationService();
        translationService.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        if (fusoOriginalJvm != null) {
            TimeZone.setDefault(fusoOriginalJvm);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "35.20",
            "35.27",
            "0.01",
            "12345678.99",
            "125.50",
            "0.10",
            "0.00",
            "10",
            "15.5",
            "42.125",
            "0.123456",
            "99999999.99",
            "123456789012345678.99"
    })
    void devePreservarPrecisaoEEscalaDoBigDecimalNoRoundTripDeEscritaELeitura(String valorLiteral) {
        BigDecimal valorEsperado = new BigDecimal(valorLiteral);
        Viagem viagemOriginal = criarViagemPadrao(valorEsperado);

        JsonObject jsonObject = ViagemMapper.paraJsonObjectCompatisComCouchbaseConverter(viagemOriginal, couchbaseConverter);

        Object valorArmazenadoNoJsonObject = jsonObject.get("valorPorPassageiro");
        assertInstanceOf(String.class, valorArmazenadoNoJsonObject);
        assertEquals(valorEsperado.toPlainString(), valorArmazenadoNoJsonObject);

        String jsonSerializado = jsonObject.toString();
        JsonObject jsonDeserializadoPeloSdk = JsonObject.fromJson(jsonSerializado);
        assertEquals(valorEsperado.toPlainString(), jsonDeserializadoPeloSdk.get("valorPorPassageiro"));

        CouchbaseDocument documentoDecodificado = new CouchbaseDocument(viagemOriginal.getId());
        translationService.decode(jsonDeserializadoPeloSdk.toString(), documentoDecodificado);

        Viagem viagemLida = couchbaseConverter.read(Viagem.class, documentoDecodificado);

        assertNotNull(viagemLida);
        assertNotNull(viagemLida.getValorPorPassageiro());
        assertEquals(0, valorEsperado.compareTo(viagemLida.getValorPorPassageiro()));
        assertEquals(valorEsperado, viagemLida.getValorPorPassageiro());
        assertEquals(valorEsperado.scale(), viagemLida.getValorPorPassageiro().scale());
    }

    @Test
    void devePreservarTodosOsDemaisCamposDaEntidadeDuranteAConversaoERoundTrip() {
        BigDecimal valorEsperado = new BigDecimal("125.50");
        Viagem viagemOriginal = criarViagemPadrao(valorEsperado);

        JsonObject jsonObject = ViagemMapper.paraJsonObjectCompatisComCouchbaseConverter(viagemOriginal, couchbaseConverter);

        assertEquals(Viagem.class.getName(), jsonObject.getString("_class"));
        assertInstanceOf(Long.class, jsonObject.get("dataHoraPartida"));
        assertInstanceOf(Long.class, jsonObject.get("dataCriacao"));
        assertNotNull(jsonObject.getObject("origem"));
        assertNotNull(jsonObject.getObject("destino"));
        assertNotNull(jsonObject.getObject("veiculoSnapshot"));

        CouchbaseDocument documentoParaLeitura = new CouchbaseDocument(viagemOriginal.getId());
        translationService.decode(jsonObject.toString(), documentoParaLeitura);

        Viagem viagemLida = couchbaseConverter.read(Viagem.class, documentoParaLeitura);

        assertNotNull(viagemLida);
        assertEquals(viagemOriginal.getId(), viagemLida.getId());
        assertEquals(viagemOriginal.getMotoristaId(), viagemLida.getMotoristaId());
        assertEquals(viagemOriginal.getVeiculoId(), viagemLida.getVeiculoId());

        assertNotNull(viagemLida.getVeiculoSnapshot());
        assertEquals("BRA2E19", viagemLida.getVeiculoSnapshot().getPlaca());
        assertEquals("Toyota Corolla", viagemLida.getVeiculoSnapshot().getModelo());
        assertEquals("Prata", viagemLida.getVeiculoSnapshot().getCor());

        assertNotNull(viagemLida.getOrigem());
        assertEquals("São Paulo", viagemLida.getOrigem().getCidade());
        assertEquals("SP", viagemLida.getOrigem().getEstado());
        assertEquals("Av. Paulista, 1000", viagemLida.getOrigem().getEndereco());
        assertEquals(-23.561414, viagemLida.getOrigem().getLatitude());
        assertEquals(-46.655881, viagemLida.getOrigem().getLongitude());

        assertNotNull(viagemLida.getDestino());
        assertEquals("Campinas", viagemLida.getDestino().getCidade());
        assertEquals("SP", viagemLida.getDestino().getEstado());
        assertEquals("Av. Francisco Glicério, 500", viagemLida.getDestino().getEndereco());
        assertEquals(-22.90556, viagemLida.getDestino().getLatitude());
        assertEquals(-47.06083, viagemLida.getDestino().getLongitude());

        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 30, 15, 123_000_000), viagemLida.getDataHoraPartida());
        assertEquals(3, viagemLida.getVagasTotais());
        assertEquals(3, viagemLida.getVagasDisponiveis());
        assertEquals(0, valorEsperado.compareTo(viagemLida.getValorPorPassageiro()));
        assertEquals(valorEsperado, viagemLida.getValorPorPassageiro());
        assertEquals(98.45, viagemLida.getDistanciaKm());
        assertEquals(78, viagemLida.getDuracaoMinutos());
        assertEquals(StatusViagem.AGENDADA, viagemLida.getStatus());
        assertEquals(LocalDateTime.of(2026, 11, 5, 22, 10, 5, 456_000_000), viagemLida.getDataCriacao());
    }

    @Test
    void deveRejeitarArgumentosNulosNaConversaoParaJsonObject() {
        Viagem viagem = criarViagemPadrao(new BigDecimal("50.00"));
        assertThrows(IllegalArgumentException.class,
                () -> ViagemMapper.paraJsonObjectCompatisComCouchbaseConverter(null, couchbaseConverter));
        assertThrows(IllegalArgumentException.class,
                () -> ViagemMapper.paraJsonObjectCompatisComCouchbaseConverter(viagem, null));
    }

    private Viagem criarViagemPadrao(BigDecimal valorPorPassageiro) {
        LocalizacaoDTO origem = new LocalizacaoDTO(
                "  São Paulo  ",
                "sp",
                "  Av. Paulista, 1000  ",
                -23.561414,
                -46.655881
        );
        LocalizacaoDTO destino = new LocalizacaoDTO(
                "  Campinas ",
                "sp",
                "  Av. Francisco Glicério, 500 ",
                -22.90556,
                -47.06083
        );
        CadastroViagemDTO dto = new CadastroViagemDTO(
                "veiculo-123",
                origem,
                destino,
                LocalDateTime.of(2026, 11, 10, 14, 30, 15, 123_987_654),
                3,
                valorPorPassageiro
        );
        Veiculo veiculo = new Veiculo(
                "veiculo-123",
                "motorista-456",
                "BRA2E19",
                "Toyota Corolla",
                "Prata",
                4,
                true
        );
        CalculoRotaDTO calculoRota = new CalculoRotaDTO(98.45, 78);
        LocalDateTime dataCriacao = LocalDateTime.of(2026, 11, 5, 22, 10, 5, 456_789_123);

        return ViagemMapper.paraNovaEntidade(
                dto,
                "viagem-789",
                "motorista-456",
                veiculo,
                calculoRota,
                dataCriacao
        );
    }
}
