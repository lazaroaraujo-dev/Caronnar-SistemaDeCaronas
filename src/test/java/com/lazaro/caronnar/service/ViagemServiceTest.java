package com.lazaro.caronnar.service;

import com.couchbase.client.core.error.CasMismatchException;
import com.couchbase.client.core.error.DocumentExistsException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.core.error.TimeoutException;
import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import com.couchbase.client.java.transactions.TransactionResult;
import com.couchbase.client.java.transactions.Transactions;
import com.couchbase.client.java.transactions.config.TransactionOptions;
import com.couchbase.client.java.transactions.error.TransactionCommitAmbiguousException;
import com.couchbase.client.java.transactions.error.TransactionExpiredException;
import com.couchbase.client.java.transactions.error.TransactionFailedException;
import com.lazaro.caronnar.dto.rota.CalculoRotaDTO;
import com.lazaro.caronnar.dto.viagem.CadastroViagemDTO;
import com.lazaro.caronnar.dto.viagem.FiltroBuscaViagemDTO;
import com.lazaro.caronnar.dto.viagem.LocalizacaoDTO;
import com.lazaro.caronnar.dto.viagem.ResultadoPublicacaoViagemDTO;
import com.lazaro.caronnar.dto.viagem.ViagemRespostaDTO;
import com.lazaro.caronnar.exception.AcessoNegadoException;
import com.lazaro.caronnar.exception.ContencaoConcorrenteException;
import com.lazaro.caronnar.exception.CoordenadasInvalidasException;
import com.lazaro.caronnar.exception.DataPartidaInvalidaException;
import com.lazaro.caronnar.exception.IdempotencyKeyInvalidaException;
import com.lazaro.caronnar.exception.IdempotencyPayloadMismatchException;
import com.lazaro.caronnar.exception.RecursoNaoEncontradoException;
import com.lazaro.caronnar.exception.RegraNegocioViagemException;
import com.lazaro.caronnar.exception.RespostaExternaInvalidaException;
import com.lazaro.caronnar.exception.RotaNaoEncontradaException;
import com.lazaro.caronnar.exception.ServicoIndisponivelException;
import com.lazaro.caronnar.exception.TransacaoAmbiguaException;
import com.lazaro.caronnar.exception.UsuarioNaoAutenticadoException;
import com.lazaro.caronnar.model.embedded.Localizacao;
import com.lazaro.caronnar.model.embedded.VeiculoSnapshot;
import com.lazaro.caronnar.model.entities.RegistroIdempotenciaViagem;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.model.entities.Viagem;
import com.lazaro.caronnar.model.enums.StatusViagem;
import com.lazaro.caronnar.repository.UsuarioRepository;
import com.lazaro.caronnar.repository.ViagemRepository;
import com.lazaro.caronnar.util.IdempotencyFingerprintUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.core.convert.CouchbaseCustomConversions;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViagemServiceTest {

    private static final ZoneId ZONA_SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Instant INSTANTE_FIXO = Instant.parse("2026-11-05T15:00:00Z");
    private static final String MOTORISTA_ID = "motorista-123";
    private static final String OUTRO_MOTORISTA_ID = "motorista-999";
    private static final String VEICULO_ID = "veiculo-456";
    private static final String IDEMPOTENCY_KEY = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d";
    private static final String CHAVE_IDEMPOTENCIA_COMPLETA = "idempotency::viagem::" + IDEMPOTENCY_KEY;

    @Mock
    private ViagemRepository viagemRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private VeiculoService veiculoService;

    @Mock
    private RotaService rotaService;

    @Mock
    private CouchbaseClientFactory couchbaseClientFactory;

    @Mock
    private Collection collection;

    @Mock
    private Cluster cluster;

    @Mock
    private Transactions transactions;

    @Mock
    private TransactionAttemptContext attemptContext;

    private TimeZone fusoOriginalJvm;
    private Clock clockFixo;
    private MappingCouchbaseConverter couchbaseConverter;
    private ViagemService viagemService;

    @BeforeEach
    void setUp() {
        fusoOriginalJvm = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));

        clockFixo = Clock.fixed(INSTANTE_FIXO, ZONA_SAO_PAULO);

        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbaseCustomConversions customConversions = new CouchbaseCustomConversions(Collections.emptyList());
        couchbaseConverter = new MappingCouchbaseConverter(mappingContext, null, customConversions);
        couchbaseConverter.afterPropertiesSet();

        lenient().when(couchbaseClientFactory.getDefaultCollection()).thenReturn(collection);
        lenient().when(couchbaseClientFactory.getCluster()).thenReturn(cluster);
        lenient().when(cluster.transactions()).thenReturn(transactions);

        RotaService validadorRotaReal = new RotaService(null);
        lenient().doAnswer(invocation -> {
            Localizacao origem = invocation.getArgument(0);
            Localizacao destino = invocation.getArgument(1);
            validadorRotaReal.validarLocalizacoes(origem, destino);
            return null;
        }).when(rotaService).validarLocalizacoes(any(), any());

        viagemService = new ViagemService(
                viagemRepository,
                usuarioRepository,
                veiculoService,
                rotaService,
                couchbaseClientFactory,
                couchbaseConverter,
                clockFixo,
                true,
                "MAJORITY_AND_PERSIST_TO_ACTIVE"
        );
    }

    @AfterEach
    void tearDown() {
        if (fusoOriginalJvm != null) {
            TimeZone.setDefault(fusoOriginalJvm);
        }
    }

    @Test
    void deveImpedirPublicacaoQuandoFlagDesativada() {
        ViagemService serviceDesativado = new ViagemService(
                viagemRepository,
                usuarioRepository,
                veiculoService,
                rotaService,
                couchbaseClientFactory,
                couchbaseConverter,
                clockFixo,
                false,
                "MAJORITY_AND_PERSIST_TO_ACTIVE"
        );

        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));

        assertThrows(
                ServicoIndisponivelException.class,
                () -> serviceDesativado.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        Authentication auth = mock(Authentication.class);
        assertThrows(
                ServicoIndisponivelException.class,
                () -> serviceDesativado.publicar(dto, auth, IDEMPOTENCY_KEY)
        );

        verifyNoInteractions(usuarioRepository, veiculoService, rotaService, couchbaseClientFactory, viagemRepository);
    }

    @Test
    void devePublicarViagemComSucessoObtendoMotoristaDaIdentidadeAutenticada() {
        CadastroViagemDTO dto = criarDtoValido(4, LocalDateTime.of(2026, 11, 10, 8, 30));
        Veiculo veiculoAtivo = criarVeiculoPadrao(4, true);
        CalculoRotaDTO calculoRota = new CalculoRotaDTO(95.40, 72);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("  " + MOTORISTA_ID + "  ");

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenThrow(new DocumentNotFoundException(null));
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID)).thenReturn(veiculoAtivo);
        when(rotaService.calcularRota(any(Localizacao.class), any(Localizacao.class))).thenReturn(calculoRota);

        when(transactions.run(any(), any(TransactionOptions.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionAttemptContext> lambda = invocation.getArgument(0);
            lambda.accept(attemptContext);
            return mock(TransactionResult.class);
        });

        ResultadoPublicacaoViagemDTO resultado = viagemService.publicar(dto, auth, "  " + IDEMPOTENCY_KEY.toUpperCase() + " ");

        assertNotNull(resultado);
        assertTrue(resultado.novaPublicacao());
        assertNotNull(resultado.resposta());
        assertNotNull(resultado.resposta().id());
        assertEquals(MOTORISTA_ID, resultado.resposta().motoristaId());
        assertEquals(VEICULO_ID, resultado.resposta().veiculoId());
        assertEquals("BRA2E19", resultado.resposta().veiculoSnapshot().placa());
        assertEquals("Honda Civic", resultado.resposta().veiculoSnapshot().modelo());
        assertEquals("Preto", resultado.resposta().veiculoSnapshot().cor());
        assertEquals("São Paulo", resultado.resposta().origem().cidade());
        assertEquals("SP", resultado.resposta().origem().estado());
        assertEquals("Campinas", resultado.resposta().destino().cidade());
        assertEquals("SP", resultado.resposta().destino().estado());
        assertEquals(LocalDateTime.of(2026, 11, 10, 8, 30), resultado.resposta().dataHoraPartida());
        assertEquals(4, resultado.resposta().vagasTotais());
        assertEquals(4, resultado.resposta().vagasDisponiveis());
        assertEquals(new BigDecimal("45.50"), resultado.resposta().valorPorPassageiro());
        assertEquals(95.40, resultado.resposta().distanciaKm());
        assertEquals(72, resultado.resposta().duracaoMinutos());
        assertEquals(StatusViagem.AGENDADA, resultado.resposta().status());
        assertEquals(LocalDateTime.now(clockFixo), resultado.resposta().dataCriacao());

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonObject> docCaptor = ArgumentCaptor.forClass(JsonObject.class);
        verify(attemptContext, times(2)).insert(eq(collection), idCaptor.capture(), docCaptor.capture());

        assertEquals(CHAVE_IDEMPOTENCIA_COMPLETA, idCaptor.getAllValues().get(0));
        JsonObject docIdempInserido = docCaptor.getAllValues().get(0);
        assertEquals(RegistroIdempotenciaViagem.class.getName(), docIdempInserido.getString("_class"));
        assertEquals(MOTORISTA_ID, docIdempInserido.getString("motoristaId"));
        assertEquals(resultado.resposta().id(), docIdempInserido.getString("viagemId"));

        assertEquals(resultado.resposta().id(), idCaptor.getAllValues().get(1));
        JsonObject docViagemInserido = docCaptor.getAllValues().get(1);
        assertEquals(Viagem.class.getName(), docViagemInserido.getString("_class"));
        assertEquals("45.50", docViagemInserido.getString("valorPorPassageiro"));
    }

    @Test
    void deveRejeitarQuandoVeiculoNaoExiste() {
        CadastroViagemDTO dto = criarDtoValido(2, LocalDateTime.of(2026, 11, 10, 8, 30));

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenThrow(new DocumentNotFoundException(null));
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID))
                .thenThrow(new RecursoNaoEncontradoException("Veículo não encontrado com o ID: " + VEICULO_ID));

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRejeitarQuandoVeiculoPertenceAOutroMotorista() {
        CadastroViagemDTO dto = criarDtoValido(2, LocalDateTime.of(2026, 11, 10, 8, 30));

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenThrow(new DocumentNotFoundException(null));
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID))
                .thenThrow(new AcessoNegadoException("O veículo informado não pertence ao motorista."));

        assertThrows(
                AcessoNegadoException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRejeitarQuandoVeiculoEstaInativo() {
        CadastroViagemDTO dto = criarDtoValido(2, LocalDateTime.of(2026, 11, 10, 8, 30));
        Veiculo veiculoInativo = criarVeiculoPadrao(4, false);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenThrow(new DocumentNotFoundException(null));
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID)).thenReturn(veiculoInativo);

        assertThrows(
                RegraNegocioViagemException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRejeitarQuandoDataHoraPartidaForPassadaOuIgualAoInstanteAtual() {
        LocalDateTime agoraNoClock = LocalDateTime.now(clockFixo);
        CadastroViagemDTO dtoIgualAgora = criarDtoValido(2, agoraNoClock);
        CadastroViagemDTO dtoPassado = criarDtoValido(2, agoraNoClock.minusMinutes(1));
        Veiculo veiculoAtivo = criarVeiculoPadrao(4, true);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenThrow(new DocumentNotFoundException(null));
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID)).thenReturn(veiculoAtivo);

        assertThrows(
                DataPartidaInvalidaException.class,
                () -> viagemService.publicar(dtoIgualAgora, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );
        assertThrows(
                DataPartidaInvalidaException.class,
                () -> viagemService.publicar(dtoPassado, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRejeitarQuandoQuantidadeDeVagasForInvalidaOuExcederCapacidadeDoVeiculo() {
        CadastroViagemDTO dtoZeroVagas = criarDtoValido(0, LocalDateTime.of(2026, 11, 10, 8, 30));
        assertThrows(
                RegraNegocioViagemException.class,
                () -> viagemService.publicar(dtoZeroVagas, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        CadastroViagemDTO dtoNoveVagas = criarDtoValido(9, LocalDateTime.of(2026, 11, 10, 8, 30));
        assertThrows(
                RegraNegocioViagemException.class,
                () -> viagemService.publicar(dtoNoveVagas, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );
        verifyNoInteractions(usuarioRepository, veiculoService);

        CadastroViagemDTO dtoAcimaDaCapacidade = criarDtoValido(5, LocalDateTime.of(2026, 11, 10, 8, 30));
        Veiculo veiculoCapacidade4 = criarVeiculoPadrao(4, true);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenThrow(new DocumentNotFoundException(null));
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID)).thenReturn(veiculoCapacidade4);

        assertThrows(
                RegraNegocioViagemException.class,
                () -> viagemService.publicar(dtoAcimaDaCapacidade, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRetornarViagemOriginalSemChamarOsrmQuandoMesmaChaveEMesmoPayloadPeloMesmoMotorista() {
        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));
        String fingerprint = IdempotencyFingerprintUtil.calcularFingerprintViagem(MOTORISTA_ID, dto);
        String viagemIdExistente = "viagem-original-001";

        GetResult getResult = mock(GetResult.class);
        JsonObject idempJson = JsonObject.create()
                .put("motoristaId", MOTORISTA_ID)
                .put("viagemId", viagemIdExistente)
                .put("requestFingerprint", fingerprint);
        when(getResult.contentAsObject()).thenReturn(idempJson);

        Viagem viagemExistente = criarViagemPersistida(viagemIdExistente, dto);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenReturn(getResult);
        when(viagemRepository.findById(viagemIdExistente)).thenReturn(Optional.of(viagemExistente));

        ResultadoPublicacaoViagemDTO resultado = viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY);

        assertNotNull(resultado);
        assertFalse(resultado.novaPublicacao());
        assertEquals(viagemIdExistente, resultado.resposta().id());
        assertEquals(MOTORISTA_ID, resultado.resposta().motoristaId());

        verifyNoInteractions(veiculoService);
        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRejeitarCom422SemChamarOsrmQuandoMesmaChaveEPayloadDiferente() {
        CadastroViagemDTO dtoOriginal = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));
        CadastroViagemDTO dtoDivergente = criarDtoValido(2, LocalDateTime.of(2026, 11, 10, 8, 30));
        String fingerprintOriginal = IdempotencyFingerprintUtil.calcularFingerprintViagem(MOTORISTA_ID, dtoOriginal);

        GetResult getResult = mock(GetResult.class);
        JsonObject idempJson = JsonObject.create()
                .put("motoristaId", MOTORISTA_ID)
                .put("viagemId", "viagem-original-001")
                .put("requestFingerprint", fingerprintOriginal);
        when(getResult.contentAsObject()).thenReturn(idempJson);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenReturn(getResult);

        assertThrows(
                IdempotencyPayloadMismatchException.class,
                () -> viagemService.publicar(dtoDivergente, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        verifyNoInteractions(veiculoService, viagemRepository);
        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRejeitarCom403SemChamarOsrmQuandoChavePertenceAOutroMotorista() {
        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));
        String fingerprint = IdempotencyFingerprintUtil.calcularFingerprintViagem(OUTRO_MOTORISTA_ID, dto);

        GetResult getResult = mock(GetResult.class);
        JsonObject idempJson = JsonObject.create()
                .put("motoristaId", OUTRO_MOTORISTA_ID)
                .put("viagemId", "viagem-outro-001")
                .put("requestFingerprint", fingerprint);
        when(getResult.contentAsObject()).thenReturn(idempJson);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenReturn(getResult);

        assertThrows(
                AcessoNegadoException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY)
        );

        verifyNoInteractions(veiculoService, viagemRepository);
        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void devePropagarExcecoesDoOsrmSemIniciarTransacaoNoCouchbase() {
        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));
        Veiculo veiculoAtivo = criarVeiculoPadrao(4, true);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenThrow(new DocumentNotFoundException(null));
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID)).thenReturn(veiculoAtivo);

        when(rotaService.calcularRota(any(Localizacao.class), any(Localizacao.class)))
                .thenThrow(new RotaNaoEncontradaException("Sem rota"))
                .thenThrow(new CoordenadasInvalidasException("Coordenadas inválidas"))
                .thenThrow(new RespostaExternaInvalidaException("Resposta OSRM inválida"))
                .thenThrow(new ServicoIndisponivelException("OSRM indisponível"));

        assertThrows(RotaNaoEncontradaException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(RespostaExternaInvalidaException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(ServicoIndisponivelException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY));

        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveTratarCommitAmbiguoETempoExpiradoEContencaoTransitoriaPermitindoNovaTentativaComMesmaChave() {
        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));
        Veiculo veiculoAtivo = criarVeiculoPadrao(4, true);
        CalculoRotaDTO calculoRota = new CalculoRotaDTO(95.40, 72);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA)).thenThrow(new DocumentNotFoundException(null));
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID)).thenReturn(veiculoAtivo);
        when(rotaService.calcularRota(any(Localizacao.class), any(Localizacao.class))).thenReturn(calculoRota);

        TransactionCommitAmbiguousException commitAmbiguous = mock(TransactionCommitAmbiguousException.class);
        TransactionExpiredException expired = mock(TransactionExpiredException.class);
        TransactionFailedException casFailed = mock(TransactionFailedException.class);
        when(casFailed.getCause()).thenReturn(new CasMismatchException(null));
        TransactionFailedException timeoutFailed = mock(TransactionFailedException.class);
        when(timeoutFailed.getCause()).thenReturn(mock(TimeoutException.class));

        when(transactions.run(any(), any(TransactionOptions.class)))
                .thenThrow(commitAmbiguous)
                .thenThrow(expired)
                .thenThrow(casFailed)
                .thenThrow(timeoutFailed);

        assertThrows(TransacaoAmbiguaException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(ContencaoConcorrenteException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(ContencaoConcorrenteException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(ServicoIndisponivelException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void deveRecuperarResultadoConcorrenteQuandoColisaoNaChaveDeIdempotenciaOcorrerDuranteATransacao() {
        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));
        Veiculo veiculoAtivo = criarVeiculoPadrao(4, true);
        CalculoRotaDTO calculoRota = new CalculoRotaDTO(95.40, 72);
        String fingerprint = IdempotencyFingerprintUtil.calcularFingerprintViagem(MOTORISTA_ID, dto);
        String viagemIdVencedora = "viagem-vencedora-777";
        Viagem viagemVencedora = criarViagemPersistida(viagemIdVencedora, dto);

        GetResult getResultConcorrente = mock(GetResult.class);
        JsonObject idempJson = JsonObject.create()
                .put("motoristaId", MOTORISTA_ID)
                .put("viagemId", viagemIdVencedora)
                .put("requestFingerprint", fingerprint);
        when(getResultConcorrente.contentAsObject()).thenReturn(idempJson);

        when(usuarioRepository.existsById(MOTORISTA_ID)).thenReturn(true);
        when(collection.get(CHAVE_IDEMPOTENCIA_COMPLETA))
                .thenThrow(new DocumentNotFoundException(null))
                .thenReturn(getResultConcorrente);
        when(veiculoService.buscarEValidarProprietario(VEICULO_ID, MOTORISTA_ID)).thenReturn(veiculoAtivo);
        when(rotaService.calcularRota(any(Localizacao.class), any(Localizacao.class))).thenReturn(calculoRota);
        when(viagemRepository.findById(viagemIdVencedora)).thenReturn(Optional.of(viagemVencedora));

        when(transactions.run(any(), any(TransactionOptions.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionAttemptContext> lambda = invocation.getArgument(0);
            when(attemptContext.insert(eq(collection), eq(CHAVE_IDEMPOTENCIA_COMPLETA), any(JsonObject.class)))
                    .thenThrow(new DocumentExistsException(null));
            try {
                lambda.accept(attemptContext);
            } catch (RuntimeException ex) {
                TransactionFailedException txFailed = mock(TransactionFailedException.class);
                when(txFailed.getCause()).thenReturn(ex);
                throw txFailed;
            }
            return null;
        });

        ResultadoPublicacaoViagemDTO resultado = viagemService.publicar(dto, MOTORISTA_ID, IDEMPOTENCY_KEY);

        assertNotNull(resultado);
        assertFalse(resultado.novaPublicacao());
        assertEquals(viagemIdVencedora, resultado.resposta().id());
    }

    @Test
    void deveRejeitarIdempotencyKeyInvalidaENivelDeDurabilidadeInseguro() {
        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));

        assertThrows(IdempotencyKeyInvalidaException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, "   "));
        assertThrows(IdempotencyKeyInvalidaException.class,
                () -> viagemService.publicar(dto, MOTORISTA_ID, "chave-nao-uuid-v4"));

        assertThrows(IllegalStateException.class, () -> new ViagemService(
                viagemRepository,
                usuarioRepository,
                veiculoService,
                rotaService,
                couchbaseClientFactory,
                couchbaseConverter,
                clockFixo,
                true,
                DurabilityLevel.NONE.name()
        ));
    }

    @Test
    void deveRejeitarCoordenadasNulasEOrigemOuDestinoNulosAntesDeAcessarCouchbaseEOsrm() {
        LocalDateTime partida = LocalDateTime.of(2026, 11, 10, 8, 30);
        LocalizacaoDTO locValida = new LocalizacaoDTO("São Paulo", "SP", "Av. Paulista, 1000", -23.561414, -46.655881);

        CadastroViagemDTO semOrigem = new CadastroViagemDTO(VEICULO_ID, null, locValida, partida, 3, new BigDecimal("45.50"));
        CadastroViagemDTO semDestino = new CadastroViagemDTO(VEICULO_ID, locValida, null, partida, 3, new BigDecimal("45.50"));
        CadastroViagemDTO latOrigemNula = criarDtoComCoordenadas(null, -46.655881, -22.90556, -47.06083);
        CadastroViagemDTO lonOrigemNula = criarDtoComCoordenadas(-23.561414, null, -22.90556, -47.06083);
        CadastroViagemDTO latDestinoNula = criarDtoComCoordenadas(-23.561414, -46.655881, null, -47.06083);
        CadastroViagemDTO lonDestinoNula = criarDtoComCoordenadas(-23.561414, -46.655881, -22.90556, null);

        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(semOrigem, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(semDestino, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(latOrigemNula, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(lonOrigemNula, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(latDestinoNula, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(lonDestinoNula, MOTORISTA_ID, IDEMPOTENCY_KEY));

        verifyNoInteractions(usuarioRepository, veiculoService, viagemRepository);
        verify(collection, never()).get(any());
        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void deveRejeitarCoordenadasNaoFinitasAntesDeAcessarCouchbaseEOsrm(double valorNaoFinito) {
        CadastroViagemDTO latOrigemInvalida = criarDtoComCoordenadas(valorNaoFinito, -46.655881, -22.90556, -47.06083);
        CadastroViagemDTO lonOrigemInvalida = criarDtoComCoordenadas(-23.561414, valorNaoFinito, -22.90556, -47.06083);
        CadastroViagemDTO latDestinoInvalida = criarDtoComCoordenadas(-23.561414, -46.655881, valorNaoFinito, -47.06083);
        CadastroViagemDTO lonDestinoInvalida = criarDtoComCoordenadas(-23.561414, -46.655881, -22.90556, valorNaoFinito);

        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(latOrigemInvalida, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(lonOrigemInvalida, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(latDestinoInvalida, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(lonDestinoInvalida, MOTORISTA_ID, IDEMPOTENCY_KEY));

        verifyNoInteractions(usuarioRepository, veiculoService, viagemRepository);
        verify(collection, never()).get(any());
        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRejeitarLatitudeELongitudeForaDosLimitesECoordenadasIdenticasAntesDeAcessarCouchbaseEOsrm() {
        CadastroViagemDTO latMenorQueMin = criarDtoComCoordenadas(-90.0001, -46.655881, -22.90556, -47.06083);
        CadastroViagemDTO latMaiorQueMax = criarDtoComCoordenadas(90.0001, -46.655881, -22.90556, -47.06083);
        CadastroViagemDTO latDestinoFora = criarDtoComCoordenadas(-23.561414, -46.655881, 95.0, -47.06083);
        CadastroViagemDTO lonMenorQueMin = criarDtoComCoordenadas(-23.561414, -180.0001, -22.90556, -47.06083);
        CadastroViagemDTO lonMaiorQueMax = criarDtoComCoordenadas(-23.561414, 180.0001, -22.90556, -47.06083);
        CadastroViagemDTO lonDestinoFora = criarDtoComCoordenadas(-23.561414, -46.655881, -22.90556, -181.0);
        CadastroViagemDTO origemDestinoIdenticos = criarDtoComCoordenadas(-23.561414, -46.655881, -23.561414, -46.655881);

        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(latMenorQueMin, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(latMaiorQueMax, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(latDestinoFora, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(lonMenorQueMin, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(lonMaiorQueMax, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(lonDestinoFora, MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(CoordenadasInvalidasException.class,
                () -> viagemService.publicar(origemDestinoIdenticos, MOTORISTA_ID, IDEMPOTENCY_KEY));

        verifyNoInteractions(usuarioRepository, veiculoService, viagemRepository);
        verify(collection, never()).get(any());
        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveRejeitarCidadeEstadoOuEnderecoAusentesOuEmBrancoAntesDeAcessarCouchbaseEOsrm() {
        LocalDateTime partida = LocalDateTime.of(2026, 11, 10, 8, 30);
        LocalizacaoDTO destinoValido = new LocalizacaoDTO("Campinas", "SP", "Av. Francisco Glicério, 500", -22.90556, -47.06083);
        LocalizacaoDTO semCidade = new LocalizacaoDTO("   ", "SP", "Av. Paulista, 1000", -23.561414, -46.655881);
        LocalizacaoDTO semEstado = new LocalizacaoDTO("São Paulo", null, "Av. Paulista, 1000", -23.561414, -46.655881);
        LocalizacaoDTO semEndereco = new LocalizacaoDTO("São Paulo", "SP", "", -23.561414, -46.655881);

        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.publicar(
                        new CadastroViagemDTO(VEICULO_ID, semCidade, destinoValido, partida, 3, new BigDecimal("45.50")),
                        MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.publicar(
                        new CadastroViagemDTO(VEICULO_ID, semEstado, destinoValido, partida, 3, new BigDecimal("45.50")),
                        MOTORISTA_ID, IDEMPOTENCY_KEY));
        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.publicar(
                        new CadastroViagemDTO(VEICULO_ID, semEndereco, destinoValido, partida, 3, new BigDecimal("45.50")),
                        MOTORISTA_ID, IDEMPOTENCY_KEY));

        verifyNoInteractions(usuarioRepository, veiculoService, viagemRepository);
        verify(collection, never()).get(any());
        verify(rotaService, never()).calcularRota(any(), any());
        verify(transactions, never()).run(any(), any(TransactionOptions.class));
    }

    @Test
    void deveConsultarViagemPorIdComSucesso() {
        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));
        Viagem viagem = criarViagemPersistida("viagem-101", dto);

        when(viagemRepository.findById("viagem-101")).thenReturn(Optional.of(viagem));

        ViagemRespostaDTO resposta = viagemService.buscarPorId("  viagem-101  ");

        assertNotNull(resposta);
        assertEquals("viagem-101", resposta.id());
        assertEquals(MOTORISTA_ID, resposta.motoristaId());
        assertEquals(VEICULO_ID, resposta.veiculoId());
        assertEquals(3, resposta.vagasDisponiveis());
        assertEquals(new BigDecimal("45.50"), resposta.valorPorPassageiro());
    }

    @Test
    void deveLancarRecursoNaoEncontradoQuandoViagemIdNaoExistirOuEstiverEmBranco() {
        when(viagemRepository.findById("viagem-inexistente")).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> viagemService.buscarPorId(null));
        assertThrows(RecursoNaoEncontradoException.class,
                () -> viagemService.buscarPorId("   "));
        assertThrows(RecursoNaoEncontradoException.class,
                () -> viagemService.buscarPorId("viagem-inexistente"));
    }

    @Test
    void deveLancarServicoIndisponivelQuandoCouchbaseFalharNaConsultaPorId() {
        when(viagemRepository.findById("viagem-101")).thenThrow(mock(TimeoutException.class));

        assertThrows(ServicoIndisponivelException.class,
                () -> viagemService.buscarPorId("viagem-101"));
    }

    @Test
    void devePesquisarViagensEmDataFuturaUsandoIntervaloInicioInclusivoEProximoDiaExclusivoENormalizandoTextos() {
        LocalDate dataFutura = LocalDate.of(2026, 11, 10);
        long inicioEsperado = dataFutura.atStartOfDay(ZONA_SAO_PAULO).toInstant().toEpochMilli();
        long fimExclusivoEsperado = dataFutura.plusDays(1).atStartOfDay(ZONA_SAO_PAULO).toInstant().toEpochMilli();

        CadastroViagemDTO dto = criarDtoValido(3, LocalDateTime.of(2026, 11, 10, 8, 30));
        Viagem viagemDisponivel = criarViagemPersistida("viagem-futura-1", dto);

        when(viagemRepository.buscarViagensDisponiveis(
                eq(StatusViagem.AGENDADA.name()),
                eq(inicioEsperado),
                eq(fimExclusivoEsperado),
                eq(2),
                eq("são paulo"),
                eq("sp"),
                eq("campinas"),
                eq("sp"),
                eq(10),
                eq(20L)
        )).thenReturn(List.of(viagemDisponivel));

        FiltroBuscaViagemDTO filtro = new FiltroBuscaViagemDTO(
                "  São Paulo ",
                " SP ",
                " Campinas  ",
                " sp ",
                dataFutura,
                2,
                2,
                10
        );

        List<ViagemRespostaDTO> resultado = viagemService.pesquisarViagens(filtro);

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertEquals("viagem-futura-1", resultado.getFirst().id());
    }

    @Test
    void devePesquisarViagensParaHojeUsandoInstanteAtualDoAppClockComoLimiteInferiorInclusivo() {
        LocalDate hoje = LocalDate.now(clockFixo);
        long agoraEpochMillis = LocalDateTime.now(clockFixo)
                .atZone(ZONA_SAO_PAULO)
                .toInstant()
                .toEpochMilli();
        long amanhaInicioEpochMillis = hoje.plusDays(1)
                .atStartOfDay(ZONA_SAO_PAULO)
                .toInstant()
                .toEpochMilli();

        CadastroViagemDTO dtoHojeMaisTarde = criarDtoValido(2, LocalDateTime.now(clockFixo).plusHours(2));
        Viagem viagemHoje = criarViagemPersistida("viagem-hoje-1", dtoHojeMaisTarde);

        when(viagemRepository.buscarViagensDisponiveis(
                eq(StatusViagem.AGENDADA.name()),
                eq(agoraEpochMillis),
                eq(amanhaInicioEpochMillis),
                eq(1),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(10),
                eq(0L)
        )).thenReturn(List.of(viagemHoje));

        FiltroBuscaViagemDTO filtroHoje = new FiltroBuscaViagemDTO(
                null,
                "   ",
                null,
                "",
                hoje,
                0,
                10
        );

        List<ViagemRespostaDTO> resultado = viagemService.pesquisarViagens(filtroHoje);

        assertEquals(1, resultado.size());
        assertEquals("viagem-hoje-1", resultado.getFirst().id());
    }

    @Test
    void devePesquisarViagensSemDataInformadaAPartirDoInstanteAtualESemLimiteSuperior() {
        long agoraEpochMillis = LocalDateTime.now(clockFixo)
                .atZone(ZONA_SAO_PAULO)
                .toInstant()
                .toEpochMilli();

        when(viagemRepository.buscarViagensDisponiveis(
                eq(StatusViagem.AGENDADA.name()),
                eq(agoraEpochMillis),
                eq(null),
                eq(3),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(15),
                eq(0L)
        )).thenReturn(Collections.emptyList());

        FiltroBuscaViagemDTO filtroSemData = new FiltroBuscaViagemDTO(
                null, null, null, null, null, 3, 0, 15
        );

        List<ViagemRespostaDTO> resultado = viagemService.pesquisarViagens(filtroSemData);

        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void deveDelegarCriteriosDeElegibilidadeEPaginacaoIntegralmenteAoRepositorioMantendoPaginaCompleta() {
        LocalDateTime agora = LocalDateTime.now(clockFixo);
        long agoraEpochMillis = agora.atZone(ZONA_SAO_PAULO).toInstant().toEpochMilli();

        Viagem viagem1 = criarViagemPersistida("viagem-pag-1", criarDtoValido(3, agora.plusHours(3)));
        Viagem viagem2 = criarViagemPersistida("viagem-pag-2", criarDtoValido(4, agora.plusHours(4)));

        when(viagemRepository.buscarViagensDisponiveis(
                eq(StatusViagem.AGENDADA.name()),
                eq(agoraEpochMillis),
                eq(null),
                eq(2),
                eq("são paulo"),
                eq("sp"),
                eq("campinas"),
                eq("sp"),
                eq(2),
                eq(6L)
        )).thenReturn(List.of(viagem1, viagem2));

        FiltroBuscaViagemDTO filtro = new FiltroBuscaViagemDTO(
                "São Paulo", "SP", "Campinas", "SP", null, 2, 3, 2
        );

        List<ViagemRespostaDTO> resultado = viagemService.pesquisarViagens(filtro);

        assertEquals(2, resultado.size());
        assertEquals("viagem-pag-1", resultado.get(0).id());
        assertEquals("viagem-pag-2", resultado.get(1).id());
        verify(viagemRepository).buscarViagensDisponiveis(
                StatusViagem.AGENDADA.name(),
                agoraEpochMillis,
                null,
                2,
                "são paulo",
                "sp",
                "campinas",
                "sp",
                2,
                6L
        );
    }

    @Test
    void deveRejeitarPesquisaComDataPassadaOuVagasMinimasOuPaginacaoInvalidas() {
        LocalDate ontem = LocalDate.now(clockFixo).minusDays(1);
        LocalDate amanha = LocalDate.now(clockFixo).plusDays(1);

        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.pesquisarViagens(null));
        assertThrows(DataPartidaInvalidaException.class,
                () -> viagemService.pesquisarViagens(
                        new FiltroBuscaViagemDTO(null, null, null, null, ontem, 1, 0, 10)));
        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.pesquisarViagens(
                        new FiltroBuscaViagemDTO(null, null, null, null, amanha, 0, 0, 10)));
        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.pesquisarViagens(
                        new FiltroBuscaViagemDTO(null, null, null, null, amanha, 9, 0, 10)));
        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.pesquisarViagens(
                        new FiltroBuscaViagemDTO(null, null, null, null, amanha, 1, -1, 10)));
        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.pesquisarViagens(
                        new FiltroBuscaViagemDTO(null, null, null, null, amanha, 1, 0, 0)));
        assertThrows(RegraNegocioViagemException.class,
                () -> viagemService.pesquisarViagens(
                        new FiltroBuscaViagemDTO(null, null, null, null, amanha, 1, 0, 51)));

        verifyNoInteractions(viagemRepository);
    }

    @Test
    void deveLancarServicoIndisponivelQuandoCouchbaseFalharNaPesquisaDeViagens() {
        when(viagemRepository.buscarViagensDisponiveis(
                any(), any(Long.class), any(), eq(1), any(), any(), any(), any(), eq(10), eq(0L)
        )).thenThrow(mock(TimeoutException.class));

        FiltroBuscaViagemDTO filtro = new FiltroBuscaViagemDTO(
                null, null, null, null, null, 1, 0, 10
        );

        assertThrows(ServicoIndisponivelException.class,
                () -> viagemService.pesquisarViagens(filtro));
    }

    private CadastroViagemDTO criarDtoComCoordenadas(Double latOrigem,
                                                     Double lonOrigem,
                                                     Double latDestino,
                                                     Double lonDestino) {
        LocalizacaoDTO origem = new LocalizacaoDTO(
                "São Paulo",
                "SP",
                "Av. Paulista, 1000",
                latOrigem,
                lonOrigem
        );
        LocalizacaoDTO destino = new LocalizacaoDTO(
                "Campinas",
                "SP",
                "Av. Francisco Glicério, 500",
                latDestino,
                lonDestino
        );
        return new CadastroViagemDTO(
                VEICULO_ID,
                origem,
                destino,
                LocalDateTime.of(2026, 11, 10, 8, 30),
                3,
                new BigDecimal("45.50")
        );
    }

    private CadastroViagemDTO criarDtoValido(int vagasTotais, LocalDateTime dataHoraPartida) {
        LocalizacaoDTO origem = new LocalizacaoDTO(
                "São Paulo",
                "SP",
                "Av. Paulista, 1000",
                -23.561414,
                -46.655881
        );
        LocalizacaoDTO destino = new LocalizacaoDTO(
                "Campinas",
                "SP",
                "Av. Francisco Glicério, 500",
                -22.90556,
                -47.06083
        );
        return new CadastroViagemDTO(
                VEICULO_ID,
                origem,
                destino,
                dataHoraPartida,
                vagasTotais,
                new BigDecimal("45.50")
        );
    }

    private Veiculo criarVeiculoPadrao(int capacidadePassageiros, boolean ativo) {
        return new Veiculo(
                VEICULO_ID,
                MOTORISTA_ID,
                "BRA2E19",
                "Honda Civic",
                "Preto",
                capacidadePassageiros,
                ativo
        );
    }

    private Viagem criarViagemPersistida(String viagemId, CadastroViagemDTO dto) {
        return new Viagem(
                viagemId,
                1L,
                MOTORISTA_ID,
                VEICULO_ID,
                new VeiculoSnapshot("BRA2E19", "Honda Civic", "Preto"),
                new Localizacao("São Paulo", "SP", "Av. Paulista, 1000", -23.561414, -46.655881),
                new Localizacao("Campinas", "SP", "Av. Francisco Glicério, 500", -22.90556, -47.06083),
                dto.dataHoraPartida(),
                dto.vagasTotais(),
                dto.vagasTotais(),
                dto.valorPorPassageiro(),
                95.40,
                72,
                StatusViagem.AGENDADA,
                LocalDateTime.now(clockFixo)
        );
    }
}
