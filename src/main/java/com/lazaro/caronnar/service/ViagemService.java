package com.lazaro.caronnar.service;

import com.couchbase.client.core.error.CasMismatchException;
import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.core.error.DecodingFailureException;
import com.couchbase.client.core.error.DocumentExistsException;
import com.couchbase.client.core.error.DocumentLockedException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.core.error.DurabilityAmbiguousException;
import com.couchbase.client.core.error.DurableWriteInProgressException;
import com.couchbase.client.core.error.RequestCanceledException;
import com.couchbase.client.core.error.TemporaryFailureException;
import com.couchbase.client.core.error.TimeoutException;
import com.couchbase.client.core.error.transaction.ActiveTransactionRecordFullException;
import com.couchbase.client.core.error.transaction.AttemptExpiredException;
import com.couchbase.client.core.error.transaction.ConcurrentOperationsDetectedOnSameDocumentException;
import com.couchbase.client.core.error.transaction.DocumentAlreadyInTransactionException;
import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
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
import com.lazaro.caronnar.exception.ConflitoIdempotenciaException;
import com.lazaro.caronnar.exception.ContencaoConcorrenteException;
import com.lazaro.caronnar.exception.CoordenadasInvalidasException;
import com.lazaro.caronnar.exception.DataPartidaInvalidaException;
import com.lazaro.caronnar.exception.IdempotencyKeyInvalidaException;
import com.lazaro.caronnar.exception.IdempotencyPayloadMismatchException;
import com.lazaro.caronnar.exception.RecursoNaoEncontradoException;
import com.lazaro.caronnar.exception.RegraNegocioViagemException;
import com.lazaro.caronnar.exception.ServicoIndisponivelException;
import com.lazaro.caronnar.exception.TransacaoAmbiguaException;
import com.lazaro.caronnar.exception.UsuarioNaoAutenticadoException;
import com.lazaro.caronnar.mapper.ViagemMapper;
import com.lazaro.caronnar.model.embedded.Localizacao;
import com.lazaro.caronnar.model.entities.RegistroIdempotenciaViagem;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.model.entities.Viagem;
import com.lazaro.caronnar.model.enums.StatusViagem;
import com.lazaro.caronnar.repository.UsuarioRepository;
import com.lazaro.caronnar.repository.ViagemRepository;
import com.lazaro.caronnar.util.IdempotencyFingerprintUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.core.convert.CouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ViagemService {

    public static final int MINIMO_VAGAS_PASSAGEIROS = 1;
    public static final int MAXIMO_VAGAS_PASSAGEIROS = 8;
    public static final int TAMANHO_PAGINA_PADRAO = 10;
    public static final int TAMANHO_PAGINA_MAXIMO = 50;

    private static final Logger logger = LoggerFactory.getLogger(ViagemService.class);
    private static final String PREFIXO_CHAVE_IDEMPOTENCIA_VIAGEM = "idempotency::viagem::";
    private static final Pattern PADRAO_UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final ViagemRepository viagemRepository;
    private final UsuarioRepository usuarioRepository;
    private final VeiculoService veiculoService;
    private final RotaService rotaService;
    private final CouchbaseClientFactory couchbaseClientFactory;
    private final CouchbaseConverter couchbaseConverter;
    private final Clock appClock;
    private final boolean publicacaoAtiva;
    private final DurabilityLevel durabilityLevel;

    public ViagemService(ViagemRepository viagemRepository,
                         UsuarioRepository usuarioRepository,
                         VeiculoService veiculoService,
                         RotaService rotaService,
                         CouchbaseClientFactory couchbaseClientFactory,
                         CouchbaseConverter couchbaseConverter,
                         Clock appClock,
                         @Value("${app.viagem.publicacao-ativa:false}") boolean publicacaoAtiva,
                         @Value("${app.couchbase.transaction.durability-level:MAJORITY_AND_PERSIST_TO_ACTIVE}") String durabilityLevelStr) {
        this.viagemRepository = viagemRepository;
        this.usuarioRepository = usuarioRepository;
        this.veiculoService = veiculoService;
        this.rotaService = rotaService;
        this.couchbaseClientFactory = couchbaseClientFactory;
        this.couchbaseConverter = couchbaseConverter;
        this.appClock = appClock;
        this.publicacaoAtiva = publicacaoAtiva;
        this.durabilityLevel = parseDurabilityLevel(durabilityLevelStr);
    }

    public ResultadoPublicacaoViagemDTO publicar(CadastroViagemDTO dto,
                                                 Authentication authentication,
                                                 String idempotencyKey) {
        if (!publicacaoAtiva) {
            throw new ServicoIndisponivelException(
                    "A publicação de viagens está temporariamente indisponível para manutenção.");
        }
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UsuarioNaoAutenticadoException("Usuário motorista não autenticado.");
        }
        return publicar(dto, authentication.getName().trim(), idempotencyKey);
    }

    public ResultadoPublicacaoViagemDTO publicar(CadastroViagemDTO dto,
                                                 String motoristaId,
                                                 String idempotencyKey) {
        if (!publicacaoAtiva) {
            throw new ServicoIndisponivelException(
                    "A publicação de viagens está temporariamente indisponível para manutenção.");
        }
        if (motoristaId == null || motoristaId.isBlank()) {
            throw new UsuarioNaoAutenticadoException("Usuário motorista não autenticado.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyInvalidaException("O cabeçalho 'Idempotency-Key' é obrigatório.");
        }

        String motoristaIdNormalizado = motoristaId.trim();
        String idempKeyNormalizada = idempotencyKey.trim().toLowerCase(Locale.ROOT);
        if (!PADRAO_UUID_V4.matcher(idempKeyNormalizada).matches()) {
            throw new IdempotencyKeyInvalidaException("O cabeçalho 'Idempotency-Key' deve ser um UUID v4 válido.");
        }

        if (dto == null) {
            throw new RegraNegocioViagemException("Os dados da viagem são obrigatórios.");
        }
        validarCamposObrigatoriosBasicos(dto);

        Localizacao origem = ViagemMapper.paraLocalizacao(dto.origem());
        Localizacao destino = ViagemMapper.paraLocalizacao(dto.destino());
        rotaService.validarLocalizacoes(origem, destino);
        validarCamposTextuaisLocalizacao(dto.origem(), "origem");
        validarCamposTextuaisLocalizacao(dto.destino(), "destino");

        String fingerprintCalculado = IdempotencyFingerprintUtil.calcularFingerprintViagem(motoristaIdNormalizado, dto);
        String chaveIdempotencia = PREFIXO_CHAVE_IDEMPOTENCIA_VIAGEM + idempKeyNormalizada;

        validarExistenciaMotorista(motoristaIdNormalizado);

        Collection collection = couchbaseClientFactory.getDefaultCollection();
        Cluster cluster = couchbaseClientFactory.getCluster();

        ResultadoPublicacaoViagemDTO resultadoExistente = consultarIdempotenciaPrevia(
                collection, chaveIdempotencia, idempKeyNormalizada, motoristaIdNormalizado, fingerprintCalculado);
        if (resultadoExistente != null) {
            return resultadoExistente;
        }

        Veiculo veiculo = validarVeiculoECapacidade(dto, motoristaIdNormalizado);
        validarDataHoraPartidaEValor(dto);

        CalculoRotaDTO calculoRota = rotaService.calcularRota(origem, destino);

        String viagemId = UUID.randomUUID().toString();
        LocalDateTime dataCriacao = ViagemMapper.normalizarParaMilissegundos(LocalDateTime.now(appClock));

        Viagem novaViagem = ViagemMapper.paraNovaEntidade(
                dto,
                viagemId,
                motoristaIdNormalizado,
                veiculo,
                calculoRota,
                dataCriacao
        );

        JsonObject docViagem = ViagemMapper.paraJsonObjectCompatisComCouchbaseConverter(novaViagem, couchbaseConverter);
        JsonObject docIdemp = criarDocumentoIdempotencia(
                chaveIdempotencia,
                idempKeyNormalizada,
                motoristaIdNormalizado,
                viagemId,
                fingerprintCalculado,
                dataCriacao
        );

        TransactionOptions options = TransactionOptions.transactionOptions()
                .durabilityLevel(this.durabilityLevel);

        try {
            cluster.transactions().run(ctx -> {
                try {
                    ctx.insert(collection, chaveIdempotencia, docIdemp);
                } catch (DocumentExistsException e) {
                    throw new ConflitoIdempotenciaException("Chave de idempotência já registrada.", e);
                }

                ctx.insert(collection, viagemId, docViagem);
            }, options);

            logger.info("Viagem {} publicada com sucesso para o motorista {} com o veículo {}",
                    viagemId, motoristaIdNormalizado, veiculo.getId());
            return new ResultadoPublicacaoViagemDTO(ViagemMapper.paraResposta(novaViagem), true);

        } catch (TransactionCommitAmbiguousException e) {
            logger.warn("Commit ambíguo durante a publicação da viagem para o motorista {}: {}",
                    motoristaIdNormalizado, e.getMessage());
            throw new TransacaoAmbiguaException(
                    "A confirmação da transação é ambígua devido a timeout ou instabilidade de rede. "
                            + "Repita a requisição utilizando o mesmo cabeçalho Idempotency-Key.", e);

        } catch (TransactionExpiredException e) {
            logger.warn("Transação expirada durante a publicação da viagem para o motorista {}: {}",
                    motoristaIdNormalizado, e.getMessage());
            throw new ContencaoConcorrenteException(
                    "Tempo limite esgotado devido a contenção temporária no Couchbase. "
                            + "Repita a requisição com o mesmo Idempotency-Key.", e);

        } catch (TransactionFailedException e) {
            if (contemCausa(e, ConflitoIdempotenciaException.class)) {
                logger.info("Conflito na chave de idempotência {}. Recuperando resultado da publicação concorrente.",
                        idempKeyNormalizada);
                return recuperarResultadoConcorrente(
                        collection, chaveIdempotencia, motoristaIdNormalizado, fingerprintCalculado);
            }

            if (ehContencaoTransitoria(e)) {
                logger.warn("Falha por contenção transitória no Couchbase ao publicar viagem para o motorista {}: {}",
                        motoristaIdNormalizado, e.getMessage());
                throw new ContencaoConcorrenteException(
                        "Operação não pôde ser concluída devido à contenção concorrente no Couchbase. "
                                + "Repita a requisição com o mesmo Idempotency-Key.", e);
            }

            if (ehFalhaComunicacaoOuTimeout(e)) {
                logger.warn("Falha de comunicação ou timeout no Couchbase ao publicar viagem para o motorista {}: {}",
                        motoristaIdNormalizado, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Comunicação com o Couchbase indisponível ou expirada temporariamente. "
                                + "Repita a requisição com o mesmo Idempotency-Key.", e);
            }

            logger.error("Falha definitiva e não transitória na transação Couchbase para o motorista {}: {}",
                    motoristaIdNormalizado, e.getMessage(), e);
            throw new IllegalStateException(
                    "Falha irrecuperável na transação do Couchbase: " + e.getMessage(), e);
        }
    }

    public ViagemRespostaDTO buscarPorId(String id) {
        if (id == null || id.isBlank()) {
            throw new RecursoNaoEncontradoException("Viagem não encontrada.");
        }
        String idNormalizado = id.trim();
        final Viagem viagem;
        try {
            viagem = viagemRepository.findById(idNormalizado)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Viagem não encontrada com o ID: " + idNormalizado));
        } catch (RecursoNaoEncontradoException e) {
            throw e;
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Indisponibilidade temporária ao consultar a viagem {}: {}",
                        idNormalizado, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária ao consultar a viagem no Couchbase.", e);
            }
            if (contemCausa(e, DecodingFailureException.class)) {
                logger.error("Erro de decodificação ao consultar a viagem {}: {}",
                        idNormalizado, e.getMessage(), e);
                throw new IllegalStateException("Falha irrecuperável ao decodificar documento da viagem.", e);
            }
            logger.error("Erro não transitório ao consultar a viagem {}: {}",
                    idNormalizado, e.getMessage(), e);
            throw e;
        }
        return ViagemMapper.paraResposta(viagem);
    }

    public List<ViagemRespostaDTO> pesquisarViagens(FiltroBuscaViagemDTO filtro) {
        if (filtro == null) {
            throw new RegraNegocioViagemException("Os parâmetros de pesquisa da viagem são obrigatórios.");
        }

        int vagasMinimas = filtro.vagasMinimas() != null ? filtro.vagasMinimas() : MINIMO_VAGAS_PASSAGEIROS;
        if (vagasMinimas < MINIMO_VAGAS_PASSAGEIROS || vagasMinimas > MAXIMO_VAGAS_PASSAGEIROS) {
            throw new RegraNegocioViagemException(
                    "A quantidade mínima de vagas para pesquisa deve estar entre "
                            + MINIMO_VAGAS_PASSAGEIROS + " e " + MAXIMO_VAGAS_PASSAGEIROS + ".");
        }

        int pagina = filtro.pagina();
        if (pagina < 0) {
            throw new RegraNegocioViagemException("O índice da página não pode ser negativo.");
        }

        int tamanho = filtro.tamanho();
        if (tamanho < 1 || tamanho > TAMANHO_PAGINA_MAXIMO) {
            throw new RegraNegocioViagemException(
                    "O tamanho da página deve estar entre 1 e " + TAMANHO_PAGINA_MAXIMO + ".");
        }

        LocalDateTime agora = ViagemMapper.normalizarParaMilissegundos(LocalDateTime.now(appClock));
        LocalDate hoje = LocalDate.now(appClock);
        LocalDate dataPartida = filtro.dataPartida();

        final long inicioInclusivoEpochMillis;
        final Long fimExclusivoEpochMillis;

        if (dataPartida != null) {
            if (dataPartida.isBefore(hoje)) {
                throw new DataPartidaInvalidaException(
                        "A data de partida para pesquisa não pode ser anterior à data atual no fuso America/Sao_Paulo.");
            }
            LocalDateTime inicioDia = dataPartida.atStartOfDay();
            LocalDateTime inicioProximoDia = dataPartida.plusDays(1).atStartOfDay();
            LocalDateTime inicioEfetivo = dataPartida.isEqual(hoje) && agora.isAfter(inicioDia)
                    ? agora
                    : inicioDia;

            inicioInclusivoEpochMillis = inicioEfetivo.atZone(appClock.getZone()).toInstant().toEpochMilli();
            fimExclusivoEpochMillis = inicioProximoDia.atZone(appClock.getZone()).toInstant().toEpochMilli();
        } else {
            inicioInclusivoEpochMillis = agora.atZone(appClock.getZone()).toInstant().toEpochMilli();
            fimExclusivoEpochMillis = null;
        }

        String origemCidadeNormalizada = normalizarFiltroTextoMinusculo(filtro.origemCidade());
        String origemEstadoNormalizado = normalizarFiltroTextoMinusculo(filtro.origemEstado());
        String destinoCidadeNormalizada = normalizarFiltroTextoMinusculo(filtro.destinoCidade());
        String destinoEstadoNormalizado = normalizarFiltroTextoMinusculo(filtro.destinoEstado());
        long offset = (long) pagina * tamanho;

        final List<Viagem> viagens;
        try {
            viagens = viagemRepository.buscarViagensDisponiveis(
                    StatusViagem.AGENDADA.name(),
                    inicioInclusivoEpochMillis,
                    fimExclusivoEpochMillis,
                    vagasMinimas,
                    origemCidadeNormalizada,
                    origemEstadoNormalizado,
                    destinoCidadeNormalizada,
                    destinoEstadoNormalizado,
                    tamanho,
                    offset
            );
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Indisponibilidade temporária ao pesquisar viagens no Couchbase: {}", e.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária ao pesquisar viagens no Couchbase.", e);
            }
            if (contemCausa(e, DecodingFailureException.class)) {
                logger.error("Erro de decodificação ao pesquisar viagens: {}", e.getMessage(), e);
                throw new IllegalStateException("Falha irrecuperável ao decodificar documentos de viagens.", e);
            }
            logger.error("Erro não transitório ao pesquisar viagens no Couchbase: {}", e.getMessage(), e);
            throw e;
        }

        if (viagens == null || viagens.isEmpty()) {
            return List.of();
        }

        return viagens.stream()
                .filter(Objects::nonNull)
                .map(ViagemMapper::paraResposta)
                .toList();
    }

    private String normalizarFiltroTextoMinusculo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    private void validarCamposObrigatoriosBasicos(CadastroViagemDTO dto) {
        if (dto.veiculoId() == null || dto.veiculoId().isBlank()) {
            throw new RegraNegocioViagemException("O identificador do veículo é obrigatório.");
        }
        if (dto.origem() == null || dto.destino() == null) {
            throw new CoordenadasInvalidasException("A localização de origem e a de destino são obrigatórias.");
        }
        if (dto.dataHoraPartida() == null) {
            throw new DataPartidaInvalidaException(
                    "A data e hora de partida são obrigatórias e devem ser futuras no fuso America/Sao_Paulo.");
        }
        if (dto.vagasTotais() == null
                || dto.vagasTotais() < MINIMO_VAGAS_PASSAGEIROS
                || dto.vagasTotais() > MAXIMO_VAGAS_PASSAGEIROS) {
            throw new RegraNegocioViagemException(
                    "A quantidade de vagas oferecidas deve estar entre "
                            + MINIMO_VAGAS_PASSAGEIROS + " e " + MAXIMO_VAGAS_PASSAGEIROS + ".");
        }
        if (dto.valorPorPassageiro() == null || dto.valorPorPassageiro().compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraNegocioViagemException("O valor por passageiro não pode ser negativo.");
        }
    }

    private void validarCamposTextuaisLocalizacao(LocalizacaoDTO loc, String ponto) {
        if (loc.cidade() == null || loc.cidade().isBlank()) {
            throw new RegraNegocioViagemException("A cidade de " + ponto + " é obrigatória.");
        }
        if (loc.estado() == null || loc.estado().isBlank()) {
            throw new RegraNegocioViagemException("O estado de " + ponto + " é obrigatório.");
        }
        if (loc.endereco() == null || loc.endereco().isBlank()) {
            throw new RegraNegocioViagemException("O endereço de " + ponto + " é obrigatório.");
        }
    }

    private void validarExistenciaMotorista(String motoristaId) {
        final boolean motoristaExiste;
        try {
            motoristaExiste = usuarioRepository.existsById(motoristaId);
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Indisponibilidade temporária ao verificar existência do motorista {}: {}",
                        motoristaId, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária de comunicação com o Couchbase. Repita a requisição com o mesmo Idempotency-Key.", e);
            }
            logger.error("Erro não transitório ao verificar existência do motorista {}: {}",
                    motoristaId, e.getMessage(), e);
            throw e;
        }
        if (!motoristaExiste) {
            throw new UsuarioNaoAutenticadoException("Usuário motorista não encontrado no sistema.");
        }
    }

    private ResultadoPublicacaoViagemDTO consultarIdempotenciaPrevia(Collection collection,
                                                                     String chaveIdempotencia,
                                                                     String idempKeyNormalizada,
                                                                     String motoristaId,
                                                                     String fingerprintCalculado) {
        try {
            GetResult getResult = collection.get(chaveIdempotencia);
            JsonObject idempJson = getResult.contentAsObject();
            logger.info("Chave de idempotência de viagem {} localizada previamente. Validando e recuperando publicação.",
                    idempKeyNormalizada);
            return validarERecuperarPublicacaoExistente(idempJson, motoristaId, fingerprintCalculado);
        } catch (DocumentNotFoundException e) {
            return null;
        } catch (DecodingFailureException e) {
            logger.error("Erro de decodificação no documento de idempotência {}: {}",
                    chaveIdempotencia, e.getMessage(), e);
            throw new IllegalStateException("Falha irrecuperável ao decodificar registro de idempotência existente.", e);
        } catch (TimeoutException | RequestCanceledException e) {
            logger.warn("Timeout ou falha de comunicação na consulta prévia da chave de idempotência {}: {}",
                    chaveIdempotencia, e.getMessage());
            throw new ServicoIndisponivelException(
                    "Indisponibilidade temporária de comunicação com o Couchbase. Repita a requisição com o mesmo Idempotency-Key.", e);
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Falha transitória no Couchbase na consulta prévia da chave de idempotência {}: {}",
                        chaveIdempotencia, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Falha temporária no Couchbase durante a verificação de idempotência. Repita a requisição com o mesmo Idempotency-Key.", e);
            }
            if (contemCausa(e, DecodingFailureException.class)) {
                logger.error("Erro de decodificação ao recuperar publicação existente para a chave {}: {}",
                        chaveIdempotencia, e.getMessage(), e);
                throw new IllegalStateException("Falha irrecuperável ao decodificar dados da publicação existente.", e);
            }
            logger.error("Erro não transitório no Couchbase na consulta prévia da chave {}: {}",
                    chaveIdempotencia, e.getMessage(), e);
            throw e;
        }
    }

    private Veiculo validarVeiculoECapacidade(CadastroViagemDTO dto, String motoristaId) {
        if (dto.veiculoId() == null || dto.veiculoId().isBlank()) {
            throw new RegraNegocioViagemException("O identificador do veículo é obrigatório.");
        }

        final Veiculo veiculo;
        try {
            veiculo = veiculoService.buscarEValidarProprietario(dto.veiculoId().trim(), motoristaId);
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Indisponibilidade temporária ao consultar o veículo {} para o motorista {}: {}",
                        dto.veiculoId(), motoristaId, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária ao consultar o veículo no Couchbase. Repita a requisição com o mesmo Idempotency-Key.", e);
            }
            throw e;
        }

        if (!Boolean.TRUE.equals(veiculo.getAtivo())) {
            throw new RegraNegocioViagemException(
                    "O veículo informado está inativo e não pode ser utilizado para publicar viagens.");
        }

        Integer vagasTotais = dto.vagasTotais();
        Integer capacidadePassageiros = veiculo.getCapacidadePassageiros();
        if (vagasTotais == null
                || vagasTotais < MINIMO_VAGAS_PASSAGEIROS
                || vagasTotais > MAXIMO_VAGAS_PASSAGEIROS) {
            throw new RegraNegocioViagemException(
                    "A quantidade de vagas oferecidas deve estar entre "
                            + MINIMO_VAGAS_PASSAGEIROS + " e " + MAXIMO_VAGAS_PASSAGEIROS + ".");
        }
        if (capacidadePassageiros == null || vagasTotais > capacidadePassageiros) {
            throw new RegraNegocioViagemException(
                    "A quantidade de vagas oferecidas (" + vagasTotais
                            + ") excede a capacidade de passageiros do veículo (" + capacidadePassageiros + ").");
        }

        return veiculo;
    }

    private void validarDataHoraPartidaEValor(CadastroViagemDTO dto) {
        LocalDateTime dataHoraPartida = ViagemMapper.normalizarParaMilissegundos(dto.dataHoraPartida());
        LocalDateTime agora = ViagemMapper.normalizarParaMilissegundos(LocalDateTime.now(appClock));

        if (dataHoraPartida == null || !dataHoraPartida.isAfter(agora)) {
            throw new DataPartidaInvalidaException(
                    "A data e hora de partida devem ser estritamente futuras no fuso America/Sao_Paulo.");
        }

        BigDecimal valorPorPassageiro = dto.valorPorPassageiro();
        if (valorPorPassageiro == null || valorPorPassageiro.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraNegocioViagemException("O valor por passageiro não pode ser negativo.");
        }
    }

    private JsonObject criarDocumentoIdempotencia(String chaveIdempotencia,
                                                  String idempKeyNormalizada,
                                                  String motoristaId,
                                                  String viagemId,
                                                  String fingerprintCalculado,
                                                  LocalDateTime dataCriacao) {
        RegistroIdempotenciaViagem registro = new RegistroIdempotenciaViagem(
                chaveIdempotencia,
                idempKeyNormalizada,
                motoristaId,
                viagemId,
                fingerprintCalculado,
                dataCriacao
        );
        CouchbaseDocument target = new CouchbaseDocument(chaveIdempotencia);
        couchbaseConverter.write(registro, target);
        return JsonObject.from(target.export());
    }

    private ResultadoPublicacaoViagemDTO validarERecuperarPublicacaoExistente(JsonObject idempJson,
                                                                              String motoristaId,
                                                                              String fingerprintEsperado) {
        String donoChave = idempJson.getString("motoristaId");
        if (!Objects.equals(donoChave, motoristaId)) {
            throw new AcessoNegadoException("A chave de idempotência informada pertence a outro motorista.");
        }

        String fingerprintArmazenado = idempJson.getString("requestFingerprint");
        if (!Objects.equals(fingerprintArmazenado, fingerprintEsperado)) {
            throw new IdempotencyPayloadMismatchException(
                    "A chave de idempotência já foi utilizada com parâmetros divergentes desta requisição.");
        }

        String viagemId = idempJson.getString("viagemId");
        if (viagemId == null || viagemId.isBlank()) {
            throw new IllegalStateException("Registro de idempotência inconsistente: identificador da viagem ausente.");
        }

        final Viagem viagem;
        try {
            viagem = viagemRepository.findById(viagemId)
                    .orElseThrow(() -> new ViagemNaoVisivelException(
                            "Registro de idempotência encontrado, mas a viagem correspondente ainda não está disponível. "
                                    + "Repita a requisição com a mesma Idempotency-Key."));
        } catch (ViagemNaoVisivelException e) {
            throw e;
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Indisponibilidade temporária ao consultar a viagem {} no Couchbase: {}",
                        viagemId, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária ao consultar a viagem no Couchbase. Repita a requisição com o mesmo Idempotency-Key.", e);
            }
            if (contemCausa(e, DecodingFailureException.class)) {
                logger.error("Erro de decodificação ao consultar a viagem {}: {}",
                        viagemId, e.getMessage(), e);
                throw new IllegalStateException("Falha irrecuperável ao decodificar documento da viagem existente.", e);
            }
            logger.error("Erro não transitório ao consultar a viagem {}: {}",
                    viagemId, e.getMessage(), e);
            throw e;
        }

        return new ResultadoPublicacaoViagemDTO(ViagemMapper.paraResposta(viagem), false);
    }

    private ResultadoPublicacaoViagemDTO recuperarResultadoConcorrente(Collection collection,
                                                                       String chaveIdempotencia,
                                                                       String motoristaId,
                                                                       String fingerprintCalculado) {
        int[] pausasMs = {50, 100, 150};
        for (int pausa : pausasMs) {
            try {
                Thread.sleep(pausa);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrompida durante a espera de recuperação concorrente da chave {}",
                        chaveIdempotencia);
                throw new ContencaoConcorrenteException(
                        "Operação interrompida enquanto aguardava a conclusão da requisição concorrente. "
                                + "Repita a requisição com o mesmo Idempotency-Key.", ie);
            }

            try {
                GetResult getResult = collection.get(chaveIdempotencia);
                JsonObject idempJson = getResult.contentAsObject();
                return validarERecuperarPublicacaoExistente(idempJson, motoristaId, fingerprintCalculado);
            } catch (DocumentNotFoundException notFound) {
                logger.debug("Chave de idempotência {} ainda não encontrada na tentativa. Aguardando commit da vencedora.",
                        chaveIdempotencia);
            } catch (ViagemNaoVisivelException viagemNaoVisivel) {
                logger.debug("Chave de idempotência {} localizada, mas documento da viagem ainda não visível na tentativa. Aguardando commit da vencedora.",
                        chaveIdempotencia);
            } catch (AcessoNegadoException | IdempotencyPayloadMismatchException regraNegocioEx) {
                throw regraNegocioEx;
            } catch (DecodingFailureException decEx) {
                logger.error("Erro de decodificação no documento de idempotência {}: {}",
                        chaveIdempotencia, decEx.getMessage(), decEx);
                throw new IllegalStateException("Falha irrecuperável ao decodificar registro de idempotência existente.", decEx);
            } catch (TimeoutException | RequestCanceledException netEx) {
                logger.warn("Timeout ou falha de comunicação durante a recuperação da chave de idempotência {}: {}",
                        chaveIdempotencia, netEx.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária de comunicação com o Couchbase durante a verificação de concorrência. "
                                + "Repita a requisição com o mesmo Idempotency-Key.", netEx);
            } catch (CouchbaseException | DataAccessException cbEx) {
                if (ehIndisponibilidadeTransitoriaLeitura(cbEx)) {
                    logger.warn("Falha temporária no Couchbase na recuperação da chave {}: {}",
                            chaveIdempotencia, cbEx.getMessage());
                    throw new ServicoIndisponivelException(
                            "Falha temporária no Couchbase durante a verificação de concorrência. "
                                    + "Repita a requisição com o mesmo Idempotency-Key.", cbEx);
                }
                if (contemCausa(cbEx, DecodingFailureException.class)) {
                    logger.error("Erro de decodificação na recuperação da chave {}: {}",
                            chaveIdempotencia, cbEx.getMessage(), cbEx);
                    throw new IllegalStateException("Falha irrecuperável ao decodificar dados da publicação existente.", cbEx);
                }
                logger.error("Erro não transitório no Couchbase na recuperação da chave {}: {}",
                        chaveIdempotencia, cbEx.getMessage(), cbEx);
                throw cbEx;
            }
        }

        logger.warn("Resultado da requisição concorrente para a chave {} não confirmado após as tentativas.",
                chaveIdempotencia);
        throw new ContencaoConcorrenteException(
                "A requisição concorrente com este Idempotency-Key ainda não concluiu a persistência. "
                        + "Repita a operação em instantes com a mesma chave para obter o resultado final sem duplicidade.");
    }

    private boolean ehContencaoTransitoria(Throwable e) {
        return contemCausa(e, ConcurrentOperationsDetectedOnSameDocumentException.class)
                || contemCausa(e, DocumentAlreadyInTransactionException.class)
                || contemCausa(e, CasMismatchException.class)
                || contemCausa(e, TemporaryFailureException.class)
                || contemCausa(e, DurableWriteInProgressException.class)
                || contemCausa(e, DocumentLockedException.class)
                || contemCausa(e, ActiveTransactionRecordFullException.class)
                || contemCausa(e, AttemptExpiredException.class);
    }

    private boolean ehFalhaComunicacaoOuTimeout(Throwable e) {
        return contemCausa(e, TimeoutException.class)
                || contemCausa(e, RequestCanceledException.class)
                || contemCausa(e, DurabilityAmbiguousException.class);
    }

    private boolean ehIndisponibilidadeTransitoriaLeitura(Throwable e) {
        if (contemCausa(e, DecodingFailureException.class)) {
            return false;
        }
        return ehFalhaComunicacaoOuTimeout(e)
                || ehContencaoTransitoria(e)
                || contemCausa(e, TransientDataAccessException.class)
                || contemCausa(e, DataAccessResourceFailureException.class);
    }

    private boolean contemCausa(Throwable throwable, Class<? extends Throwable> tipoEsperado) {
        Throwable atual = throwable;
        while (atual != null) {
            if (tipoEsperado.isInstance(atual)) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    private static DurabilityLevel parseDurabilityLevel(String val) {
        if (val == null || val.isBlank()) {
            return DurabilityLevel.MAJORITY_AND_PERSIST_TO_ACTIVE;
        }
        final DurabilityLevel level;
        try {
            level = DurabilityLevel.valueOf(val.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Configuração inválida para app.couchbase.transaction.durability-level (COUCHBASE_TRANSACTION_DURABILITY): '"
                            + val + "'. Valores válidos: " + Arrays.toString(DurabilityLevel.values()), e);
        }
        if (level == DurabilityLevel.NONE) {
            throw new IllegalStateException(
                    "Configuração inválida para app.couchbase.transaction.durability-level (COUCHBASE_TRANSACTION_DURABILITY): "
                            + "DurabilityLevel.NONE não é permitido para transações de publicação de viagens.");
        }
        return level;
    }

    private static class ViagemNaoVisivelException extends ContencaoConcorrenteException {
        public ViagemNaoVisivelException(String mensagem) {
            super(mensagem);
        }
    }
}
