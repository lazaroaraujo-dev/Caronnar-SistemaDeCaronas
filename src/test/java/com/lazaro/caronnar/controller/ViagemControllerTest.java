package com.lazaro.caronnar.controller;

import com.lazaro.caronnar.config.SecurityConfig;
import com.lazaro.caronnar.dto.viagem.CadastroViagemDTO;
import com.lazaro.caronnar.dto.viagem.FiltroBuscaViagemDTO;
import com.lazaro.caronnar.dto.viagem.LocalizacaoDTO;
import com.lazaro.caronnar.dto.viagem.ResultadoPublicacaoViagemDTO;
import com.lazaro.caronnar.dto.viagem.VeiculoSnapshotDTO;
import com.lazaro.caronnar.dto.viagem.ViagemRespostaDTO;
import com.lazaro.caronnar.exception.AcessoNegadoException;
import com.lazaro.caronnar.exception.GlobalExceptionHandler;
import com.lazaro.caronnar.exception.IdempotencyKeyInvalidaException;
import com.lazaro.caronnar.exception.IdempotencyPayloadMismatchException;
import com.lazaro.caronnar.exception.RecursoNaoEncontradoException;
import com.lazaro.caronnar.exception.RegraNegocioViagemException;
import com.lazaro.caronnar.exception.ServicoIndisponivelException;
import com.lazaro.caronnar.model.entities.Usuario;
import com.lazaro.caronnar.model.enums.PapelUsuario;
import com.lazaro.caronnar.model.enums.StatusViagem;
import com.lazaro.caronnar.security.AutenticacaoService;
import com.lazaro.caronnar.security.JwtAuthenticationEntryPoint;
import com.lazaro.caronnar.security.JwtService;
import com.lazaro.caronnar.security.UsuarioAutenticado;
import com.lazaro.caronnar.service.ViagemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ViagemController.class,
        properties = {
                "security.jwt.secret=test-jwt-secret-key-with-at-least-32-bytes",
                "security.jwt.expiration-minutes=60"
        }
)
@Import({
        SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtService.class,
        GlobalExceptionHandler.class
})
class ViagemControllerTest {

    private static final String MOTORISTA_ID = "motorista-123";
    private static final String VEICULO_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String VIAGEM_ID = "9c42e8a1-4b11-4562-b3fc-2c963f66afa6";
    private static final String IDEMPOTENCY_KEY = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ViagemService viagemService;

    @MockitoBean
    private AutenticacaoService autenticacaoService;

    private String tokenValido;

    @BeforeEach
    void setUp() {
        Usuario usuario = new Usuario();
        usuario.setId(MOTORISTA_ID);
        usuario.setNome("Motorista Teste");
        usuario.setEmail("motorista@exemplo.com");
        usuario.setSenha("senha-hash");
        usuario.setTelefone("11999999999");
        usuario.setPapeis(Set.of(PapelUsuario.PASSAGEIRO, PapelUsuario.MOTORISTA));
        usuario.setDataCadastro(LocalDateTime.of(2026, 11, 1, 10, 0));

        tokenValido = jwtService.gerarToken(new UsuarioAutenticado(usuario));
    }

    @Test
    void deveRetornar201CreatedQuandoPublicacaoForNovaComUsuarioAutenticado() throws Exception {
        ViagemRespostaDTO respostaEsperada = criarViagemRespostaPadrao();
        when(viagemService.publicar(any(CadastroViagemDTO.class), any(Authentication.class), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new ResultadoPublicacaoViagemDTO(respostaEsperada, true));

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criarJsonCadastroValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(VIAGEM_ID))
                .andExpect(jsonPath("$.motoristaId").value(MOTORISTA_ID))
                .andExpect(jsonPath("$.veiculoId").value(VEICULO_ID))
                .andExpect(jsonPath("$.veiculoSnapshot.placa").value("BRA2E19"))
                .andExpect(jsonPath("$.veiculoSnapshot.modelo").value("Honda Civic"))
                .andExpect(jsonPath("$.veiculoSnapshot.cor").value("Preto"))
                .andExpect(jsonPath("$.origem.cidade").value("São Paulo"))
                .andExpect(jsonPath("$.destino.cidade").value("Campinas"))
                .andExpect(jsonPath("$.vagasTotais").value(3))
                .andExpect(jsonPath("$.vagasDisponiveis").value(3))
                .andExpect(jsonPath("$.valorPorPassageiro").value(45.50))
                .andExpect(jsonPath("$.distanciaKm").value(95.40))
                .andExpect(jsonPath("$.duracaoMinutos").value(72))
                .andExpect(jsonPath("$.status").value("AGENDADA"));

        ArgumentCaptor<CadastroViagemDTO> dtoCaptor = ArgumentCaptor.forClass(CadastroViagemDTO.class);
        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        ArgumentCaptor<String> idempCaptor = ArgumentCaptor.forClass(String.class);

        verify(viagemService).publicar(dtoCaptor.capture(), authCaptor.capture(), idempCaptor.capture());

        CadastroViagemDTO dtoRecebido = dtoCaptor.getValue();
        assertEquals(VEICULO_ID, dtoRecebido.veiculoId());
        assertEquals("São Paulo", dtoRecebido.origem().cidade());
        assertEquals("Campinas", dtoRecebido.destino().cidade());
        assertEquals(LocalDateTime.of(2026, 12, 1, 8, 30), dtoRecebido.dataHoraPartida());
        assertEquals(3, dtoRecebido.vagasTotais());
        assertEquals(new BigDecimal("45.50"), dtoRecebido.valorPorPassageiro());

        Authentication authRecebida = authCaptor.getValue();
        assertNotNull(authRecebida);
        assertEquals(MOTORISTA_ID, authRecebida.getName());
        assertEquals(IDEMPOTENCY_KEY, idempCaptor.getValue());
    }

    @Test
    void deveRetornar200OkQuandoPublicacaoForRepeticaoIdempotente() throws Exception {
        ViagemRespostaDTO respostaEsperada = criarViagemRespostaPadrao();
        when(viagemService.publicar(any(CadastroViagemDTO.class), any(Authentication.class), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new ResultadoPublicacaoViagemDTO(respostaEsperada, false));

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criarJsonCadastroValido()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(VIAGEM_ID))
                .andExpect(jsonPath("$.motoristaId").value(MOTORISTA_ID))
                .andExpect(jsonPath("$.veiculoId").value(VEICULO_ID));
    }

    @Test
    void deveIgnorarMotoristaIdEnviadoPeloClienteNoCorpoEUsarIdentidadeDoTokenJwt() throws Exception {
        ViagemRespostaDTO respostaEsperada = criarViagemRespostaPadrao();
        when(viagemService.publicar(any(CadastroViagemDTO.class), any(Authentication.class), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new ResultadoPublicacaoViagemDTO(respostaEsperada, true));

        String jsonComMotoristaImpostor = """
                {
                  "motoristaId": "motorista-impostor-999",
                  "veiculoId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "origem": {
                    "cidade": "São Paulo",
                    "estado": "SP",
                    "endereco": "Av. Paulista, 1000",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "Campinas",
                    "estado": "SP",
                    "endereco": "Av. Francisco Glicério, 500",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "2026-12-01T08:30:00",
                  "vagasTotais": 3,
                  "valorPorPassageiro": 45.50
                }
                """;

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonComMotoristaImpostor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motoristaId").value(MOTORISTA_ID));

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(viagemService).publicar(any(CadastroViagemDTO.class), authCaptor.capture(), eq(IDEMPOTENCY_KEY));
        assertEquals(MOTORISTA_ID, authCaptor.getValue().getName());
    }

    @Test
    void deveRetornar400QuandoIdempotencyKeyEstiverAusenteOuInvalida() throws Exception {
        when(viagemService.publicar(any(CadastroViagemDTO.class), any(Authentication.class), eq(null)))
                .thenThrow(new IdempotencyKeyInvalidaException("O cabeçalho 'Idempotency-Key' é obrigatório."));

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criarJsonCadastroValido()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Requisição inválida"))
                .andExpect(jsonPath("$.mensagem").value("O cabeçalho 'Idempotency-Key' é obrigatório."));
    }

    @Test
    void deveRetornar400QuandoCorpoDaPublicacaoViolarBeanValidation() throws Exception {
        String jsonInvalido = """
                {
                  "veiculoId": "",
                  "origem": {
                    "cidade": "São Paulo",
                    "estado": "SP",
                    "endereco": "Av. Paulista, 1000",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "Campinas",
                    "estado": "SP",
                    "endereco": "Av. Francisco Glicério, 500",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "2026-12-01T08:30:00",
                  "vagasTotais": 9,
                  "valorPorPassageiro": 45.50
                }
                """;

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Validação falhou"))
                .andExpect(jsonPath("$.campos.veiculoId").exists())
                .andExpect(jsonPath("$.campos.vagasTotais").exists());

        verifyNoInteractions(viagemService);
    }

    @Test
    void deveRetornar403QuandoVeiculoOuChavePertencerAOutroMotorista() throws Exception {
        when(viagemService.publicar(any(CadastroViagemDTO.class), any(Authentication.class), eq(IDEMPOTENCY_KEY)))
                .thenThrow(new AcessoNegadoException("O veículo informado não pertence ao motorista."));

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criarJsonCadastroValido()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("Acesso negado"))
                .andExpect(jsonPath("$.mensagem").value("O veículo informado não pertence ao motorista."));
    }

    @Test
    void deveRetornar422QuandoChaveIdempotenteForReutilizadaComPayloadDivergente() throws Exception {
        when(viagemService.publicar(any(CadastroViagemDTO.class), any(Authentication.class), eq(IDEMPOTENCY_KEY)))
                .thenThrow(new IdempotencyPayloadMismatchException(
                        "A chave de idempotência já foi utilizada com parâmetros divergentes desta requisição."));

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criarJsonCadastroValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.erro").value("Entidade improcessável"));
    }

    @Test
    void deveRetornar503QuandoPublicacaoEstiverDesativadaOuServicoIndisponivel() throws Exception {
        when(viagemService.publicar(any(CadastroViagemDTO.class), any(Authentication.class), eq(IDEMPOTENCY_KEY)))
                .thenThrow(new ServicoIndisponivelException(
                        "A publicação de viagens está temporariamente indisponível para manutenção."));

        mockMvc.perform(post("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criarJsonCadastroValido()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.erro").value("Serviço indisponível"));
    }

    @Test
    void deveRetornar200OkComViagemRespostaDtoQuandoViagemExistirNaBuscaPorId() throws Exception {
        ViagemRespostaDTO respostaEsperada = criarViagemRespostaPadrao();
        when(viagemService.buscarPorId(VIAGEM_ID)).thenReturn(respostaEsperada);

        mockMvc.perform(get("/api/viagens/{id}", VIAGEM_ID)
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(VIAGEM_ID))
                .andExpect(jsonPath("$.motoristaId").value(MOTORISTA_ID))
                .andExpect(jsonPath("$.veiculoId").value(VEICULO_ID))
                .andExpect(jsonPath("$.veiculoSnapshot.placa").value("BRA2E19"))
                .andExpect(jsonPath("$.origem.cidade").value("São Paulo"))
                .andExpect(jsonPath("$.destino.cidade").value("Campinas"))
                .andExpect(jsonPath("$.vagasTotais").value(3))
                .andExpect(jsonPath("$.vagasDisponiveis").value(3))
                .andExpect(jsonPath("$.valorPorPassageiro").value(45.50))
                .andExpect(jsonPath("$.status").value("AGENDADA"));

        verify(viagemService).buscarPorId(VIAGEM_ID);
    }

    @Test
    void deveRetornar404NotFoundQuandoViagemNaoExistirNaBuscaPorId() throws Exception {
        when(viagemService.buscarPorId("viagem-inexistente"))
                .thenThrow(new RecursoNaoEncontradoException("Viagem não encontrada com o ID: viagem-inexistente"));

        mockMvc.perform(get("/api/viagens/{id}", "viagem-inexistente")
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro").value("Não encontrado"))
                .andExpect(jsonPath("$.mensagem").value("Viagem não encontrada com o ID: viagem-inexistente"));
    }

    @Test
    void devePesquisarViagensComFiltrosCompletosEConverterDataPartidaIsoCorretamente() throws Exception {
        ViagemRespostaDTO respostaEsperada = criarViagemRespostaPadrao();
        when(viagemService.pesquisarViagens(any(FiltroBuscaViagemDTO.class)))
                .thenReturn(List.of(respostaEsperada));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .param("origemCidade", "São Paulo")
                        .param("origemEstado", "SP")
                        .param("destinoCidade", "Campinas")
                        .param("destinoEstado", "SP")
                        .param("dataPartida", "2026-12-01")
                        .param("vagasMinimas", "2")
                        .param("pagina", "1")
                        .param("tamanho", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(VIAGEM_ID))
                .andExpect(jsonPath("$[0].origem.cidade").value("São Paulo"))
                .andExpect(jsonPath("$[0].destino.cidade").value("Campinas"));

        ArgumentCaptor<FiltroBuscaViagemDTO> filtroCaptor = ArgumentCaptor.forClass(FiltroBuscaViagemDTO.class);
        verify(viagemService).pesquisarViagens(filtroCaptor.capture());

        FiltroBuscaViagemDTO filtroRecebido = filtroCaptor.getValue();
        assertEquals("São Paulo", filtroRecebido.origemCidade());
        assertEquals("SP", filtroRecebido.origemEstado());
        assertEquals("Campinas", filtroRecebido.destinoCidade());
        assertEquals("SP", filtroRecebido.destinoEstado());
        assertEquals(LocalDate.of(2026, 12, 1), filtroRecebido.dataPartida());
        assertEquals(2, filtroRecebido.vagasMinimas());
        assertEquals(1, filtroRecebido.pagina());
        assertEquals(15, filtroRecebido.tamanho());
    }

    @Test
    void deveAplicarValoresPadraoDeVagasMinimasPaginaETamanhoNaPesquisaSemParametros() throws Exception {
        when(viagemService.pesquisarViagens(any(FiltroBuscaViagemDTO.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        ArgumentCaptor<FiltroBuscaViagemDTO> filtroCaptor = ArgumentCaptor.forClass(FiltroBuscaViagemDTO.class);
        verify(viagemService).pesquisarViagens(filtroCaptor.capture());

        FiltroBuscaViagemDTO filtroRecebido = filtroCaptor.getValue();
        assertNull(filtroRecebido.origemCidade());
        assertNull(filtroRecebido.origemEstado());
        assertNull(filtroRecebido.destinoCidade());
        assertNull(filtroRecebido.destinoEstado());
        assertNull(filtroRecebido.dataPartida());
        assertEquals(1, filtroRecebido.vagasMinimas());
        assertEquals(0, filtroRecebido.pagina());
        assertEquals(10, filtroRecebido.tamanho());
    }

    @Test
    void deveRetornar400BadRequestQuandoParametrosDePesquisaTiveremFormatoInvalido() throws Exception {
        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .param("pagina", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Requisição inválida"))
                .andExpect(jsonPath("$.mensagem").value("O parâmetro 'pagina' possui formato ou tipo inválido."));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .param("vagasMinimas", "texto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Requisição inválida"))
                .andExpect(jsonPath("$.mensagem").value("O parâmetro 'vagasMinimas' possui formato ou tipo inválido."));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .param("dataPartida", "2026-99-99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Requisição inválida"))
                .andExpect(jsonPath("$.mensagem").value("O parâmetro 'dataPartida' possui formato ou tipo inválido."));

        verifyNoInteractions(viagemService);
    }

    @Test
    void deveRetornar422QuandoParametrosNumericosDePesquisaViolaremRegrasDoServico() throws Exception {
        when(viagemService.pesquisarViagens(any(FiltroBuscaViagemDTO.class)))
                .thenThrow(new RegraNegocioViagemException(
                        "A quantidade mínima de vagas para pesquisa deve estar entre 1 e 8."));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer " + tokenValido)
                        .param("vagasMinimas", "9"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.erro").value("Entidade improcessável"));
    }

    @Test
    void deveRetornar401UnauthorizedQuandoRequisicoesNaoPossuiremTokenJwtOuTokenForInvalido() throws Exception {
        mockMvc.perform(post("/api/viagens")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criarJsonCadastroValido()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Não autorizado"));

        mockMvc.perform(get("/api/viagens/{id}", VIAGEM_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Não autorizado"));

        mockMvc.perform(get("/api/viagens"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Não autorizado"));

        mockMvc.perform(get("/api/viagens")
                        .header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Não autorizado"));

        verifyNoInteractions(viagemService);
    }

    private ViagemRespostaDTO criarViagemRespostaPadrao() {
        return new ViagemRespostaDTO(
                VIAGEM_ID,
                MOTORISTA_ID,
                VEICULO_ID,
                new VeiculoSnapshotDTO("BRA2E19", "Honda Civic", "Preto"),
                new LocalizacaoDTO("São Paulo", "SP", "Av. Paulista, 1000", -23.561414, -46.655881),
                new LocalizacaoDTO("Campinas", "SP", "Av. Francisco Glicério, 500", -22.90556, -47.06083),
                LocalDateTime.of(2026, 12, 1, 8, 30),
                3,
                3,
                new BigDecimal("45.50"),
                95.40,
                72,
                StatusViagem.AGENDADA,
                LocalDateTime.of(2026, 11, 6, 1, 0)
        );
    }

    private String criarJsonCadastroValido() {
        return """
                {
                  "veiculoId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "origem": {
                    "cidade": "São Paulo",
                    "estado": "SP",
                    "endereco": "Av. Paulista, 1000",
                    "latitude": -23.561414,
                    "longitude": -46.655881
                  },
                  "destino": {
                    "cidade": "Campinas",
                    "estado": "SP",
                    "endereco": "Av. Francisco Glicério, 500",
                    "latitude": -22.90556,
                    "longitude": -47.06083
                  },
                  "dataHoraPartida": "2026-12-01T08:30:00",
                  "vagasTotais": 3,
                  "valorPorPassageiro": 45.50
                }
                """;
    }
}
