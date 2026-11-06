package com.lazaro.caronnar;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.lazaro.caronnar.client.OsrmClient;
import com.lazaro.caronnar.dto.rota.OsrmRotaResponseDTO;
import com.lazaro.caronnar.model.entities.Usuario;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.model.enums.PapelUsuario;
import com.lazaro.caronnar.repository.UsuarioRepository;
import com.lazaro.caronnar.repository.VeiculoRepository;
import com.lazaro.caronnar.security.JwtService;
import com.lazaro.caronnar.security.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.viagem.publicacao-ativa=true",
        "security.jwt.secret=test-jwt-secret-key-with-at-least-32-bytes",
        "security.jwt.expiration-minutes=60"
})
@AutoConfigureMockMvc
class ViagemPublicacaoPesquisaIntegradoTest {

    private static final String PREFIXO_IDEMPOTENCIA_VIAGEM = "idempotency::viagem::";
    private static final int MAX_TENTATIVAS_BUSCA = 5;
    private static final long PAUSA_ENTRE_TENTATIVAS_MS = 200L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private CouchbaseClientFactory couchbaseClientFactory;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Clock appClock;

    @MockitoBean
    private OsrmClient osrmClient;

    private String motoristaId;
    private String veiculoId;
    private String outroMotoristaId;
    private String veiculoOutroMotoristaId;
    private String idempotencyKey;
    private String viagemIdCriada;
    private String origemCidadeUnica;
    private String destinoCidadeUnica;
    private String tokenJwt;
    private final List<String> chavesIdempotenciaExtras = new ArrayList<>();
    private final List<String> viagensIdsExtras = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String sufixoUnico = UUID.randomUUID().toString().substring(0, 8);
        motoristaId = "test-motorista-" + UUID.randomUUID();
        veiculoId = "test-veiculo-" + UUID.randomUUID();
        outroMotoristaId = null;
        veiculoOutroMotoristaId = null;
        idempotencyKey = UUID.randomUUID().toString();
        viagemIdCriada = null;
        origemCidadeUnica = "OrigemSintetica-" + sufixoUnico;
        destinoCidadeUnica = "DestinoSintetico-" + sufixoUnico;
        chavesIdempotenciaExtras.clear();
        viagensIdsExtras.clear();

        Usuario usuario = new Usuario(
                motoristaId,
                "Motorista Sintetico " + sufixoUnico,
                "motorista." + sufixoUnico + "@sintetico.test",
                "hash-sintetico-teste",
                "11900000000",
                Set.of(PapelUsuario.PASSAGEIRO, PapelUsuario.MOTORISTA),
                LocalDateTime.now(appClock)
        );
        usuarioRepository.save(usuario);

        Veiculo veiculo = new Veiculo(
                veiculoId,
                motoristaId,
                "TST1A23",
                "Modelo Sintetico",
                "Prata",
                4,
                true
        );
        veiculoRepository.save(veiculo);

        tokenJwt = jwtService.gerarToken(new UsuarioAutenticado(usuario));

        when(osrmClient.consultarRota(-46.655881, -23.561414, -47.06083, -22.90556))
                .thenReturn(new OsrmRotaResponseDTO(
                        "Ok",
                        null,
                        List.of(new OsrmRotaResponseDTO.RotaItem(95400.0, 4320.0))
                ));
    }

    @AfterEach
    void tearDown() {
        Collection collection = couchbaseClientFactory.getDefaultCollection();

        String chaveIdempotencia = idempotencyKey != null
                ? PREFIXO_IDEMPOTENCIA_VIAGEM + idempotencyKey.trim().toLowerCase(Locale.ROOT)
                : null;

        if (viagemIdCriada == null && chaveIdempotencia != null) {
            viagemIdCriada = recuperarViagemIdDoRegistroIdempotencia(collection, chaveIdempotencia);
        }

        for (String chaveExtra : chavesIdempotenciaExtras) {
            String viagemAssociada = recuperarViagemIdDoRegistroIdempotencia(collection, chaveExtra);
            removerDocumentoSilenciosamente(collection, viagemAssociada);
            removerDocumentoSilenciosamente(collection, chaveExtra);
        }
        for (String viagemExtraId : viagensIdsExtras) {
            removerDocumentoSilenciosamente(collection, viagemExtraId);
        }

        removerDocumentoSilenciosamente(collection, chaveIdempotencia);
        removerDocumentoSilenciosamente(collection, viagemIdCriada);
        removerDocumentoSilenciosamente(collection, veiculoOutroMotoristaId);
        removerDocumentoSilenciosamente(collection, outroMotoristaId);
        removerDocumentoSilenciosamente(collection, veiculoId);
        removerDocumentoSilenciosamente(collection, motoristaId);
    }

    @Test
    void devePublicarViagemEPesquisarComSucessoNoFluxoIntegrado() throws Exception {
        LocalDate dataPartida = LocalDate.now(appClock).plusDays(2);
        LocalDateTime dataHoraPartida = dataPartida.atTime(9, 30, 0);
        String dataHoraPartidaIso = dataHoraPartida.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String payloadPublicacao = """
                {
                  "veiculoId": "%s",
                  "origem": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Origem, 100",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Destino, 200",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "%s",
                  "vagasTotais": 3,
                  "valorPorPassageiro": 45.50
                }
                """.formatted(veiculoId, origemCidadeUnica, destinoCidadeUnica, dataHoraPartidaIso);

        ResultActions resultadoPost = mockMvc.perform(post("/api/viagens")
                .header("Authorization", "Bearer " + tokenJwt)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadPublicacao));

        MvcResult mvcResultPost = resultadoPost.andReturn();
        String corpoPost = mvcResultPost.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (corpoPost != null && !corpoPost.isBlank()) {
            try {
                viagemIdCriada = JsonObject.fromJson(corpoPost).getString("id");
            } catch (Exception ignored) {
            }
        }

        resultadoPost
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.motoristaId").value(motoristaId))
                .andExpect(jsonPath("$.veiculoId").value(veiculoId))
                .andExpect(jsonPath("$.veiculoSnapshot.placa").value("TST1A23"))
                .andExpect(jsonPath("$.veiculoSnapshot.modelo").value("Modelo Sintetico"))
                .andExpect(jsonPath("$.veiculoSnapshot.cor").value("Prata"))
                .andExpect(jsonPath("$.origem.cidade").value(origemCidadeUnica))
                .andExpect(jsonPath("$.origem.estado").value("SP"))
                .andExpect(jsonPath("$.destino.cidade").value(destinoCidadeUnica))
                .andExpect(jsonPath("$.destino.estado").value("SP"))
                .andExpect(jsonPath("$.vagasTotais").value(3))
                .andExpect(jsonPath("$.vagasDisponiveis").value(3))
                .andExpect(jsonPath("$.valorPorPassageiro").value(45.50))
                .andExpect(jsonPath("$.distanciaKm").value(95.40))
                .andExpect(jsonPath("$.duracaoMinutos").value(72))
                .andExpect(jsonPath("$.status").value("AGENDADA"));

        assertNotNull(viagemIdCriada);

        ResultActions resultadoGet = executarPesquisaComRetentativaLimitada(dataPartida);

        resultadoGet
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(viagemIdCriada))
                .andExpect(jsonPath("$[0].motoristaId").value(motoristaId))
                .andExpect(jsonPath("$[0].veiculoId").value(veiculoId))
                .andExpect(jsonPath("$[0].veiculoSnapshot.placa").value("TST1A23"))
                .andExpect(jsonPath("$[0].veiculoSnapshot.modelo").value("Modelo Sintetico"))
                .andExpect(jsonPath("$[0].veiculoSnapshot.cor").value("Prata"))
                .andExpect(jsonPath("$[0].origem.cidade").value(origemCidadeUnica))
                .andExpect(jsonPath("$[0].origem.estado").value("SP"))
                .andExpect(jsonPath("$[0].destino.cidade").value(destinoCidadeUnica))
                .andExpect(jsonPath("$[0].destino.estado").value("SP"))
                .andExpect(jsonPath("$[0].vagasTotais").value(3))
                .andExpect(jsonPath("$[0].vagasDisponiveis").value(3))
                .andExpect(jsonPath("$[0].valorPorPassageiro").value(45.50))
                .andExpect(jsonPath("$[0].distanciaKm").value(95.40))
                .andExpect(jsonPath("$[0].duracaoMinutos").value(72))
                .andExpect(jsonPath("$[0].status").value("AGENDADA"));
    }

    @Test
    void deveRetornar200EMesmaViagemSemNovaChamadaAoOsrmNemDuplicacaoQuandoRepetirPublicacaoComMesmaChaveEMesmoPayload() throws Exception {
        LocalDate dataPartida = LocalDate.now(appClock).plusDays(2);
        LocalDateTime dataHoraPartida = dataPartida.atTime(9, 30, 0);
        String dataHoraPartidaIso = dataHoraPartida.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String payloadPublicacao = """
                {
                  "veiculoId": "%s",
                  "origem": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Origem, 100",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Destino, 200",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "%s",
                  "vagasTotais": 3,
                  "valorPorPassageiro": 45.50
                }
                """.formatted(veiculoId, origemCidadeUnica, destinoCidadeUnica, dataHoraPartidaIso);

        ResultActions primeiroPost = mockMvc.perform(post("/api/viagens")
                .header("Authorization", "Bearer " + tokenJwt)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadPublicacao));

        String corpoPrimeiroPost = primeiroPost.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (corpoPrimeiroPost != null && !corpoPrimeiroPost.isBlank()) {
            try {
                viagemIdCriada = JsonObject.fromJson(corpoPrimeiroPost).getString("id");
            } catch (Exception ignored) {
            }
        }

        primeiroPost
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.motoristaId").value(motoristaId))
                .andExpect(jsonPath("$.veiculoId").value(veiculoId))
                .andExpect(jsonPath("$.vagasTotais").value(3))
                .andExpect(jsonPath("$.vagasDisponiveis").value(3))
                .andExpect(jsonPath("$.valorPorPassageiro").value(45.50))
                .andExpect(jsonPath("$.status").value("AGENDADA"));

        assertNotNull(viagemIdCriada);

        Collection collection = couchbaseClientFactory.getDefaultCollection();
        String chaveIdempotencia = PREFIXO_IDEMPOTENCIA_VIAGEM + idempotencyKey.trim().toLowerCase(Locale.ROOT);
        GetResult idempAntes = collection.get(chaveIdempotencia);
        long casIdempotenciaOriginal = idempAntes.cas();
        String fingerprintOriginal = idempAntes.contentAsObject().getString("requestFingerprint");
        long casViagemOriginal = collection.get(viagemIdCriada).cas();

        ResultActions segundoPost = mockMvc.perform(post("/api/viagens")
                .header("Authorization", "Bearer " + tokenJwt)
                .header("Idempotency-Key", "  " + idempotencyKey.toUpperCase(Locale.ROOT) + "  ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadPublicacao));

        segundoPost
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(viagemIdCriada))
                .andExpect(jsonPath("$.motoristaId").value(motoristaId))
                .andExpect(jsonPath("$.veiculoId").value(veiculoId))
                .andExpect(jsonPath("$.veiculoSnapshot.placa").value("TST1A23"))
                .andExpect(jsonPath("$.veiculoSnapshot.modelo").value("Modelo Sintetico"))
                .andExpect(jsonPath("$.veiculoSnapshot.cor").value("Prata"))
                .andExpect(jsonPath("$.origem.cidade").value(origemCidadeUnica))
                .andExpect(jsonPath("$.origem.estado").value("SP"))
                .andExpect(jsonPath("$.destino.cidade").value(destinoCidadeUnica))
                .andExpect(jsonPath("$.destino.estado").value("SP"))
                .andExpect(jsonPath("$.vagasTotais").value(3))
                .andExpect(jsonPath("$.vagasDisponiveis").value(3))
                .andExpect(jsonPath("$.valorPorPassageiro").value(45.50))
                .andExpect(jsonPath("$.distanciaKm").value(95.40))
                .andExpect(jsonPath("$.duracaoMinutos").value(72))
                .andExpect(jsonPath("$.status").value("AGENDADA"));

        verify(osrmClient, times(1)).consultarRota(-46.655881, -23.561414, -47.06083, -22.90556);

        GetResult idempDepois = collection.get(chaveIdempotencia);
        assertEquals(casIdempotenciaOriginal, idempDepois.cas());
        assertEquals(viagemIdCriada, idempDepois.contentAsObject().getString("viagemId"));
        assertEquals(fingerprintOriginal, idempDepois.contentAsObject().getString("requestFingerprint"));
        assertEquals(casViagemOriginal, collection.get(viagemIdCriada).cas());

        ResultActions resultadoGet = executarPesquisaComRetentativaLimitada(dataPartida);
        resultadoGet
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(viagemIdCriada));
    }

    @Test
    void deveRetornar422EManterViagemERegistroIdempotenciaInalteradosQuandoReutilizarChaveComPayloadDivergente() throws Exception {
        LocalDate dataPartida = LocalDate.now(appClock).plusDays(2);
        LocalDateTime dataHoraPartida = dataPartida.atTime(9, 30, 0);
        String dataHoraPartidaIso = dataHoraPartida.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String payloadOriginal = """
                {
                  "veiculoId": "%s",
                  "origem": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Origem, 100",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Destino, 200",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "%s",
                  "vagasTotais": 3,
                  "valorPorPassageiro": 45.50
                }
                """.formatted(veiculoId, origemCidadeUnica, destinoCidadeUnica, dataHoraPartidaIso);

        ResultActions primeiroPost = mockMvc.perform(post("/api/viagens")
                .header("Authorization", "Bearer " + tokenJwt)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadOriginal));

        String corpoPrimeiroPost = primeiroPost.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (corpoPrimeiroPost != null && !corpoPrimeiroPost.isBlank()) {
            try {
                viagemIdCriada = JsonObject.fromJson(corpoPrimeiroPost).getString("id");
            } catch (Exception ignored) {
            }
        }

        primeiroPost.andExpect(status().isCreated());
        assertNotNull(viagemIdCriada);

        Collection collection = couchbaseClientFactory.getDefaultCollection();
        String chaveIdempotencia = PREFIXO_IDEMPOTENCIA_VIAGEM + idempotencyKey.trim().toLowerCase(Locale.ROOT);
        GetResult idempAntes = collection.get(chaveIdempotencia);
        long casIdempotenciaOriginal = idempAntes.cas();
        String fingerprintOriginal = idempAntes.contentAsObject().getString("requestFingerprint");

        GetResult viagemAntes = collection.get(viagemIdCriada);
        long casViagemOriginal = viagemAntes.cas();

        String payloadDivergente = """
                {
                  "veiculoId": "%s",
                  "origem": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Origem, 100",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Destino, 200",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "%s",
                  "vagasTotais": 2,
                  "valorPorPassageiro": 60.00
                }
                """.formatted(veiculoId, origemCidadeUnica, destinoCidadeUnica, dataHoraPartidaIso);

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadDivergente))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.erro").value("Entidade improcessável"))
                .andExpect(jsonPath("$.mensagem").value(
                        "A chave de idempotência já foi utilizada com parâmetros divergentes desta requisição."));

        verify(osrmClient, times(1)).consultarRota(-46.655881, -23.561414, -47.06083, -22.90556);

        GetResult idempDepois = collection.get(chaveIdempotencia);
        assertEquals(casIdempotenciaOriginal, idempDepois.cas());
        assertEquals(viagemIdCriada, idempDepois.contentAsObject().getString("viagemId"));
        assertEquals(fingerprintOriginal, idempDepois.contentAsObject().getString("requestFingerprint"));

        GetResult viagemDepois = collection.get(viagemIdCriada);
        JsonObject docViagemDepois = viagemDepois.contentAsObject();
        assertEquals(casViagemOriginal, viagemDepois.cas());
        assertEquals(3, docViagemDepois.getInt("vagasTotais"));
        assertEquals(3, docViagemDepois.getInt("vagasDisponiveis"));
        assertEquals("45.50", docViagemDepois.getString("valorPorPassageiro"));
    }

    @Test
    void deveRetornar404ENaoCriarViagemNemRegistroIdempotenciaQuandoVeiculoNaoExistir() throws Exception {
        String veiculoInexistenteId = "test-veiculo-inexistente-" + UUID.randomUUID();
        Collection collection = couchbaseClientFactory.getDefaultCollection();
        assertFalse(collection.exists(veiculoInexistenteId).exists());

        LocalDate dataPartida = LocalDate.now(appClock).plusDays(2);
        LocalDateTime dataHoraPartida = dataPartida.atTime(9, 30, 0);
        String dataHoraPartidaIso = dataHoraPartida.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String payloadPublicacao = """
                {
                  "veiculoId": "%s",
                  "origem": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Origem, 100",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Destino, 200",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "%s",
                  "vagasTotais": 3,
                  "valorPorPassageiro": 45.50
                }
                """.formatted(veiculoInexistenteId, origemCidadeUnica, destinoCidadeUnica, dataHoraPartidaIso);

        ResultActions resultadoPost = mockMvc.perform(post("/api/viagens")
                .header("Authorization", "Bearer " + tokenJwt)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadPublicacao));

        String corpoPost = resultadoPost.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (corpoPost != null && !corpoPost.isBlank()) {
            try {
                viagemIdCriada = JsonObject.fromJson(corpoPost).getString("id");
            } catch (Exception ignored) {
            }
        }

        resultadoPost
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro").value("Não encontrado"))
                .andExpect(jsonPath("$.mensagem").value("Veículo não encontrado com o ID: " + veiculoInexistenteId));

        verifyNoInteractions(osrmClient);

        String chaveIdempotencia = PREFIXO_IDEMPOTENCIA_VIAGEM + idempotencyKey.trim().toLowerCase(Locale.ROOT);
        assertFalse(collection.exists(chaveIdempotencia).exists());
        assertNull(recuperarViagemIdDoRegistroIdempotencia(collection, chaveIdempotencia));
        assertFalse(collection.exists(veiculoInexistenteId).exists());
        assertNull(viagemIdCriada);

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("origemEstado", "SP")
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("destinoEstado", "SP")
                        .param("dataPartida", dataPartida.toString())
                        .param("vagasMinimas", "1")
                        .param("pagina", "0")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deveRetornar403ENaoCriarViagemNemRegistroIdempotenciaQuandoVeiculoPertencerAOutroMotorista() throws Exception {
        String sufixoOutro = UUID.randomUUID().toString().substring(0, 8);
        outroMotoristaId = "test-outro-motorista-" + UUID.randomUUID();
        veiculoOutroMotoristaId = "test-veiculo-outro-" + UUID.randomUUID();

        Usuario outroMotorista = new Usuario(
                outroMotoristaId,
                "Outro Motorista Sintetico " + sufixoOutro,
                "outro.motorista." + sufixoOutro + "@sintetico.test",
                "hash-sintetico-teste",
                "11911111111",
                Set.of(PapelUsuario.PASSAGEIRO, PapelUsuario.MOTORISTA),
                LocalDateTime.now(appClock)
        );
        usuarioRepository.save(outroMotorista);

        Veiculo veiculoOutro = new Veiculo(
                veiculoOutroMotoristaId,
                outroMotoristaId,
                "OTR9B87",
                "Modelo Outro Motorista",
                "Preto",
                4,
                true
        );
        veiculoRepository.save(veiculoOutro);

        Collection collection = couchbaseClientFactory.getDefaultCollection();
        assertTrue(collection.exists(outroMotoristaId).exists());
        assertTrue(collection.exists(veiculoOutroMotoristaId).exists());

        LocalDate dataPartida = LocalDate.now(appClock).plusDays(2);
        LocalDateTime dataHoraPartida = dataPartida.atTime(9, 30, 0);
        String dataHoraPartidaIso = dataHoraPartida.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String payloadPublicacao = """
                {
                  "veiculoId": "%s",
                  "origem": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Origem, 100",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Destino, 200",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "%s",
                  "vagasTotais": 3,
                  "valorPorPassageiro": 45.50
                }
                """.formatted(veiculoOutroMotoristaId, origemCidadeUnica, destinoCidadeUnica, dataHoraPartidaIso);

        ResultActions resultadoPost = mockMvc.perform(post("/api/viagens")
                .header("Authorization", "Bearer " + tokenJwt)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadPublicacao));

        String corpoPost = resultadoPost.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (corpoPost != null && !corpoPost.isBlank()) {
            try {
                viagemIdCriada = JsonObject.fromJson(corpoPost).getString("id");
            } catch (Exception ignored) {
            }
        }

        resultadoPost
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("Acesso negado"))
                .andExpect(jsonPath("$.mensagem").value("O veículo informado não pertence ao motorista."));

        verifyNoInteractions(osrmClient);

        String chaveIdempotencia = PREFIXO_IDEMPOTENCIA_VIAGEM + idempotencyKey.trim().toLowerCase(Locale.ROOT);
        assertFalse(collection.exists(chaveIdempotencia).exists());
        assertNull(recuperarViagemIdDoRegistroIdempotencia(collection, chaveIdempotencia));
        assertNull(viagemIdCriada);

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("origemEstado", "SP")
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("destinoEstado", "SP")
                        .param("dataPartida", dataPartida.toString())
                        .param("vagasMinimas", "1")
                        .param("pagina", "0")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deveRetornar422ENaoCriarViagemNemRegistroIdempotenciaQuandoVeiculoEstiverInativo() throws Exception {
        Veiculo veiculoInativo = new Veiculo(
                veiculoId,
                motoristaId,
                "TST1A23",
                "Modelo Sintetico",
                "Prata",
                4,
                false
        );
        veiculoRepository.save(veiculoInativo);

        Collection collection = couchbaseClientFactory.getDefaultCollection();
        assertFalse(collection.get(veiculoId).contentAsObject().getBoolean("ativo"));

        LocalDate dataPartida = LocalDate.now(appClock).plusDays(2);
        LocalDateTime dataHoraPartida = dataPartida.atTime(9, 30, 0);
        String dataHoraPartidaIso = dataHoraPartida.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String payloadPublicacao = """
                {
                  "veiculoId": "%s",
                  "origem": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Origem, 100",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "%s",
                    "estado": "SP",
                    "endereco": "Av. Sintetica Destino, 200",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "%s",
                  "vagasTotais": 3,
                  "valorPorPassageiro": 45.50
                }
                """.formatted(veiculoId, origemCidadeUnica, destinoCidadeUnica, dataHoraPartidaIso);

        ResultActions resultadoPost = mockMvc.perform(post("/api/viagens")
                .header("Authorization", "Bearer " + tokenJwt)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadPublicacao));

        String corpoPost = resultadoPost.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (corpoPost != null && !corpoPost.isBlank()) {
            try {
                viagemIdCriada = JsonObject.fromJson(corpoPost).getString("id");
            } catch (Exception ignored) {
            }
        }

        resultadoPost
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.erro").value("Entidade improcessável"))
                .andExpect(jsonPath("$.mensagem").value(
                        "O veículo informado está inativo e não pode ser utilizado para publicar viagens."));

        verifyNoInteractions(osrmClient);

        String chaveIdempotencia = PREFIXO_IDEMPOTENCIA_VIAGEM + idempotencyKey.trim().toLowerCase(Locale.ROOT);
        assertFalse(collection.exists(chaveIdempotencia).exists());
        assertNull(recuperarViagemIdDoRegistroIdempotencia(collection, chaveIdempotencia));
        assertNull(viagemIdCriada);

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("origemEstado", "SP")
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("destinoEstado", "SP")
                        .param("dataPartida", dataPartida.toString())
                        .param("vagasMinimas", "1")
                        .param("pagina", "0")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deveOrdenarPorHorarioPartidaEFiltrarPorOrigemDestinoDataEVagasMinimas() throws Exception {
        LocalDate dataAlvo = LocalDate.now(appClock).plusDays(3);
        LocalDateTime horarioMaisCedo = dataAlvo.atTime(8, 15, 0);
        LocalDateTime horarioPoucasVagas = dataAlvo.atTime(10, 30, 0);
        LocalDateTime horarioDuasVagas = dataAlvo.atTime(12, 0, 0);
        LocalDateTime horarioMaisTarde = dataAlvo.atTime(16, 0, 0);
        String sufixoAlternativo = UUID.randomUUID().toString().substring(0, 8);
        String outraOrigemCidade = "OutraOrigemSintetica-" + sufixoAlternativo;
        String outroDestinoCidade = "OutroDestinoSintetico-" + sufixoAlternativo;

        String viagemMaisTardeId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                horarioMaisTarde,
                4,
                "50.00"
        );
        String viagemMaisCedoId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                horarioMaisCedo,
                3,
                "40.00"
        );
        String viagemPoucasVagasId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                horarioPoucasVagas,
                1,
                "35.00"
        );
        String viagemDuasVagasId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                horarioDuasVagas,
                2,
                "37.00"
        );
        String viagemOutraDataId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                dataAlvo.plusDays(1).atTime(9, 0, 0),
                4,
                "45.00"
        );
        String viagemOutroDestinoId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                outroDestinoCidade,
                "RJ",
                dataAlvo.atTime(7, 0, 0),
                4,
                "55.00"
        );
        String viagemOutraOrigemId = publicarViagemSintetica(
                outraOrigemCidade,
                "MG",
                destinoCidadeUnica,
                "SP",
                dataAlvo.atTime(6, 30, 0),
                4,
                "60.00"
        );

        ResultActions buscaIsoladaOutraData = executarPesquisaComRetentativaEsperandoQuantidade(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                dataAlvo.plusDays(1),
                1,
                0,
                10,
                1
        );
        buscaIsoladaOutraData
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(viagemOutraDataId));

        ResultActions buscaIsoladaOutroDestino = executarPesquisaComRetentativaEsperandoQuantidade(
                origemCidadeUnica,
                "SP",
                outroDestinoCidade,
                "RJ",
                dataAlvo,
                1,
                0,
                10,
                1
        );
        buscaIsoladaOutroDestino
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(viagemOutroDestinoId));

        ResultActions buscaIsoladaOutraOrigem = executarPesquisaComRetentativaEsperandoQuantidade(
                outraOrigemCidade,
                "MG",
                destinoCidadeUnica,
                "SP",
                dataAlvo,
                1,
                0,
                10,
                1
        );
        buscaIsoladaOutraOrigem
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(viagemOutraOrigemId));

        ResultActions buscaComUmaVagaMinima = executarPesquisaComRetentativaEsperandoQuantidade(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                dataAlvo,
                1,
                0,
                10,
                4
        );

        buscaComUmaVagaMinima
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(viagemMaisCedoId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioMaisCedo)))
                .andExpect(jsonPath("$[0].vagasDisponiveis").value(3))
                .andExpect(jsonPath("$[1].id").value(viagemPoucasVagasId))
                .andExpect(jsonPath("$[1].dataHoraPartida").value(formatarHorarioRetornado(horarioPoucasVagas)))
                .andExpect(jsonPath("$[1].vagasDisponiveis").value(1))
                .andExpect(jsonPath("$[2].id").value(viagemDuasVagasId))
                .andExpect(jsonPath("$[2].dataHoraPartida").value(formatarHorarioRetornado(horarioDuasVagas)))
                .andExpect(jsonPath("$[2].vagasDisponiveis").value(2))
                .andExpect(jsonPath("$[3].id").value(viagemMaisTardeId))
                .andExpect(jsonPath("$[3].dataHoraPartida").value(formatarHorarioRetornado(horarioMaisTarde)))
                .andExpect(jsonPath("$[3].vagasDisponiveis").value(4));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("origemEstado", "SP")
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("destinoEstado", "SP")
                        .param("dataPartida", dataAlvo.toString())
                        .param("vagasMinimas", "2")
                        .param("pagina", "0")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(viagemMaisCedoId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioMaisCedo)))
                .andExpect(jsonPath("$[1].id").value(viagemDuasVagasId))
                .andExpect(jsonPath("$[1].dataHoraPartida").value(formatarHorarioRetornado(horarioDuasVagas)))
                .andExpect(jsonPath("$[2].id").value(viagemMaisTardeId))
                .andExpect(jsonPath("$[2].dataHoraPartida").value(formatarHorarioRetornado(horarioMaisTarde)));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", "  " + origemCidadeUnica.toUpperCase(Locale.ROOT) + "  ")
                        .param("origemEstado", "sp")
                        .param("destinoCidade", "  " + destinoCidadeUnica.toLowerCase(Locale.ROOT) + "  ")
                        .param("destinoEstado", "sp")
                        .param("dataPartida", dataAlvo.toString())
                        .param("vagasMinimas", "3")
                        .param("pagina", "0")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(viagemMaisCedoId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioMaisCedo)))
                .andExpect(jsonPath("$[0].vagasDisponiveis").value(3))
                .andExpect(jsonPath("$[1].id").value(viagemMaisTardeId))
                .andExpect(jsonPath("$[1].dataHoraPartida").value(formatarHorarioRetornado(horarioMaisTarde)))
                .andExpect(jsonPath("$[1].vagasDisponiveis").value(4));
    }

    @Test
    void devePesquisarComFiltrosOpcionaisEPaginarResultadosDeFormaEstavel() throws Exception {
        LocalDate dataAlvo = LocalDate.now(appClock).plusDays(5);
        LocalDateTime horarioInicio = dataAlvo.atTime(8, 0, 0);
        LocalDateTime horarioMeio = dataAlvo.atTime(11, 30, 0);
        LocalDateTime horarioFim = dataAlvo.atTime(15, 45, 0);

        String viagemMeioId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                horarioMeio,
                3,
                "42.00"
        );
        String viagemFimId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                horarioFim,
                4,
                "48.00"
        );
        String viagemInicioId = publicarViagemSintetica(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                horarioInicio,
                2,
                "38.00"
        );

        ResultActions confirmacaoVisibilidadeCompleta = executarPesquisaComRetentativaEsperandoQuantidade(
                origemCidadeUnica,
                "SP",
                destinoCidadeUnica,
                "SP",
                dataAlvo,
                1,
                0,
                10,
                3
        );

        confirmacaoVisibilidadeCompleta
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(viagemInicioId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioInicio)))
                .andExpect(jsonPath("$[1].id").value(viagemMeioId))
                .andExpect(jsonPath("$[1].dataHoraPartida").value(formatarHorarioRetornado(horarioMeio)))
                .andExpect(jsonPath("$[2].id").value(viagemFimId))
                .andExpect(jsonPath("$[2].dataHoraPartida").value(formatarHorarioRetornado(horarioFim)));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("destinoEstado", "SP")
                        .param("dataPartida", dataAlvo.toString())
                        .param("vagasMinimas", "1")
                        .param("pagina", "0")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(viagemInicioId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioInicio)))
                .andExpect(jsonPath("$[1].id").value(viagemMeioId))
                .andExpect(jsonPath("$[1].dataHoraPartida").value(formatarHorarioRetornado(horarioMeio)))
                .andExpect(jsonPath("$[2].id").value(viagemFimId))
                .andExpect(jsonPath("$[2].dataHoraPartida").value(formatarHorarioRetornado(horarioFim)));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("origemEstado", "SP")
                        .param("dataPartida", dataAlvo.toString())
                        .param("vagasMinimas", "1")
                        .param("pagina", "0")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(viagemInicioId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioInicio)))
                .andExpect(jsonPath("$[1].id").value(viagemMeioId))
                .andExpect(jsonPath("$[1].dataHoraPartida").value(formatarHorarioRetornado(horarioMeio)))
                .andExpect(jsonPath("$[2].id").value(viagemFimId))
                .andExpect(jsonPath("$[2].dataHoraPartida").value(formatarHorarioRetornado(horarioFim)));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("vagasMinimas", "1")
                        .param("pagina", "0")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(viagemInicioId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioInicio)))
                .andExpect(jsonPath("$[1].id").value(viagemMeioId))
                .andExpect(jsonPath("$[1].dataHoraPartida").value(formatarHorarioRetornado(horarioMeio)))
                .andExpect(jsonPath("$[2].id").value(viagemFimId))
                .andExpect(jsonPath("$[2].dataHoraPartida").value(formatarHorarioRetornado(horarioFim)));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("origemEstado", "SP")
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("destinoEstado", "SP")
                        .param("dataPartida", dataAlvo.toString())
                        .param("vagasMinimas", "1")
                        .param("pagina", "0")
                        .param("tamanho", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(viagemInicioId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioInicio)))
                .andExpect(jsonPath("$[1].id").value(viagemMeioId))
                .andExpect(jsonPath("$[1].dataHoraPartida").value(formatarHorarioRetornado(horarioMeio)));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("origemEstado", "SP")
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("destinoEstado", "SP")
                        .param("dataPartida", dataAlvo.toString())
                        .param("vagasMinimas", "1")
                        .param("pagina", "1")
                        .param("tamanho", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(viagemFimId))
                .andExpect(jsonPath("$[0].dataHoraPartida").value(formatarHorarioRetornado(horarioFim)));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenJwt)
                        .param("origemCidade", origemCidadeUnica)
                        .param("origemEstado", "SP")
                        .param("destinoCidade", destinoCidadeUnica)
                        .param("destinoEstado", "SP")
                        .param("dataPartida", dataAlvo.toString())
                        .param("vagasMinimas", "1")
                        .param("pagina", "2")
                        .param("tamanho", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String publicarViagemSintetica(String origemCidade,
                                           String origemEstado,
                                           String destinoCidade,
                                           String destinoEstado,
                                           LocalDateTime dataHoraPartida,
                                           int vagasTotais,
                                           String valorPorPassageiro) throws Exception {
        String idempKeyExtra = UUID.randomUUID().toString();
        String chaveIdempDoc = PREFIXO_IDEMPOTENCIA_VIAGEM + idempKeyExtra.toLowerCase(Locale.ROOT);
        chavesIdempotenciaExtras.add(chaveIdempDoc);

        String dataHoraPartidaIso = dataHoraPartida.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String payload = """
                {
                  "veiculoId": "%s",
                  "origem": {
                    "cidade": "%s",
                    "estado": "%s",
                    "endereco": "Av. Sintetica Origem, 100",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "%s",
                    "estado": "%s",
                    "endereco": "Av. Sintetica Destino, 200",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "%s",
                  "vagasTotais": %d,
                  "valorPorPassageiro": %s
                }
                """.formatted(
                veiculoId,
                origemCidade,
                origemEstado,
                destinoCidade,
                destinoEstado,
                dataHoraPartidaIso,
                vagasTotais,
                valorPorPassageiro
        );

        ResultActions resultadoPost = mockMvc.perform(post("/api/viagens")
                .header("Authorization", "Bearer " + tokenJwt)
                .header("Idempotency-Key", idempKeyExtra)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));

        String corpoPost = resultadoPost.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String idExtra = null;
        if (corpoPost != null && !corpoPost.isBlank()) {
            try {
                idExtra = JsonObject.fromJson(corpoPost).getString("id");
                if (idExtra != null && !idExtra.isBlank()) {
                    viagensIdsExtras.add(idExtra);
                }
            } catch (Exception ignored) {
            }
        }

        resultadoPost.andExpect(status().isCreated());
        assertNotNull(idExtra);

        Collection collection = couchbaseClientFactory.getDefaultCollection();
        assertTrue(collection.exists(chaveIdempDoc).exists());
        assertTrue(collection.exists(idExtra).exists());

        return idExtra;
    }

    private ResultActions executarPesquisaComRetentativaEsperandoQuantidade(String origemCidade,
                                                                            String origemEstado,
                                                                            String destinoCidade,
                                                                            String destinoEstado,
                                                                            LocalDate dataPartida,
                                                                            int vagasMinimas,
                                                                            int pagina,
                                                                            int tamanho,
                                                                            int quantidadeMinimaEsperada) throws Exception {
        ResultActions ultimaResposta = null;
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS_BUSCA; tentativa++) {
            MockHttpServletRequestBuilder requestBuilder = get("/api/viagens")
                    .header("Authorization", "Bearer " + tokenJwt)
                    .param("vagasMinimas", String.valueOf(vagasMinimas))
                    .param("pagina", String.valueOf(pagina))
                    .param("tamanho", String.valueOf(tamanho));

            if (origemCidade != null) {
                requestBuilder.param("origemCidade", origemCidade);
            }
            if (origemEstado != null) {
                requestBuilder.param("origemEstado", origemEstado);
            }
            if (destinoCidade != null) {
                requestBuilder.param("destinoCidade", destinoCidade);
            }
            if (destinoEstado != null) {
                requestBuilder.param("destinoEstado", destinoEstado);
            }
            if (dataPartida != null) {
                requestBuilder.param("dataPartida", dataPartida.toString());
            }

            ultimaResposta = mockMvc.perform(requestBuilder);

            MvcResult mvcResult = ultimaResposta.andReturn();
            if (mvcResult.getResponse().getStatus() == 200) {
                String corpo = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
                if (corpo != null && !corpo.isBlank()) {
                    try {
                        JsonArray array = JsonArray.fromJson(corpo);
                        if (array.size() >= quantidadeMinimaEsperada) {
                            return ultimaResposta;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (tentativa < MAX_TENTATIVAS_BUSCA) {
                Thread.sleep(PAUSA_ENTRE_TENTATIVAS_MS);
            }
        }
        return ultimaResposta;
    }

    private String formatarHorarioRetornado(LocalDateTime dataHora) {
        return dataHora.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private ResultActions executarPesquisaComRetentativaLimitada(LocalDate dataPartida) throws Exception {
        ResultActions ultimaResposta = null;
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS_BUSCA; tentativa++) {
            ultimaResposta = mockMvc.perform(get("/api/viagens")
                    .header("Authorization", "Bearer " + tokenJwt)
                    .param("origemCidade", origemCidadeUnica)
                    .param("origemEstado", "SP")
                    .param("destinoCidade", destinoCidadeUnica)
                    .param("destinoEstado", "SP")
                    .param("dataPartida", dataPartida.toString())
                    .param("vagasMinimas", "2")
                    .param("pagina", "0")
                    .param("tamanho", "10"));

            MvcResult mvcResult = ultimaResposta.andReturn();
            if (mvcResult.getResponse().getStatus() == 200) {
                String corpo = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
                if (corpo != null && !corpo.isBlank()) {
                    try {
                        JsonArray array = JsonArray.fromJson(corpo);
                        if (!array.isEmpty()) {
                            return ultimaResposta;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (tentativa < MAX_TENTATIVAS_BUSCA) {
                Thread.sleep(PAUSA_ENTRE_TENTATIVAS_MS);
            }
        }
        return ultimaResposta;
    }

    private String recuperarViagemIdDoRegistroIdempotencia(Collection collection, String chaveIdempotencia) {
        try {
            GetResult getResult = collection.get(chaveIdempotencia);
            return getResult.contentAsObject().getString("viagemId");
        } catch (Exception ignored) {
            return null;
        }
    }

    private void removerDocumentoSilenciosamente(Collection collection, String docId) {
        if (docId == null || docId.isBlank()) {
            return;
        }
        try {
            collection.remove(docId);
        } catch (DocumentNotFoundException ignored) {
        } catch (Exception ignored) {
        }
    }
}
