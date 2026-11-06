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
import com.lazaro.caronnar.dto.veiculo.CadastroVeiculoDTO;
import com.lazaro.caronnar.dto.veiculo.ResultadoCadastroVeiculoDTO;
import com.lazaro.caronnar.dto.veiculo.VeiculoRespostaDTO;
import com.lazaro.caronnar.exception.AcessoNegadoException;
import com.lazaro.caronnar.exception.ConflitoIdempotenciaException;
import com.lazaro.caronnar.exception.ContencaoConcorrenteException;
import com.lazaro.caronnar.exception.IdempotencyPayloadMismatchException;
import com.lazaro.caronnar.exception.PlacaJaCadastradaException;
import com.lazaro.caronnar.exception.RecursoNaoEncontradoException;
import com.lazaro.caronnar.exception.ServicoIndisponivelException;
import com.lazaro.caronnar.exception.TransacaoAmbiguaException;
import com.lazaro.caronnar.exception.UsuarioNaoAutenticadoException;
import com.lazaro.caronnar.mapper.VeiculoMapper;
import com.lazaro.caronnar.model.entities.PlacaVeiculo;
import com.lazaro.caronnar.model.entities.RegistroIdempotencia;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.repository.UsuarioRepository;
import com.lazaro.caronnar.repository.VeiculoRepository;
import com.lazaro.caronnar.util.IdempotencyFingerprintUtil;
import com.lazaro.caronnar.util.PlacaNormalizador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class VeiculoService {

    private static final Logger logger = LoggerFactory.getLogger(VeiculoService.class);

    private final VeiculoRepository veiculoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CouchbaseClientFactory couchbaseClientFactory;
    private final DurabilityLevel durabilityLevel;

    public VeiculoService(VeiculoRepository veiculoRepository,
                          UsuarioRepository usuarioRepository,
                          CouchbaseClientFactory couchbaseClientFactory,
                          @Value("${app.couchbase.transaction.durability-level:MAJORITY_AND_PERSIST_TO_ACTIVE}") String durabilityLevelStr) {
        this.veiculoRepository = veiculoRepository;
        this.usuarioRepository = usuarioRepository;
        this.couchbaseClientFactory = couchbaseClientFactory;
        this.durabilityLevel = parseDurabilityLevel(durabilityLevelStr);
    }

    /**
     * Cadastra um novo veículo garantindo atomicamente a unicidade da placa e idempotência estrita via Couchbase Transactions.
     *
     * @param dto            Dados do veículo enviados pelo cliente
     * @param motoristaId    Identificador do motorista autenticado (do JWT)
     * @param idempotencyKey Chave de idempotência obrigatória (UUID v4)
     * @return Resultado contendo o DTO de resposta e indicador de novo cadastro
     */
    public ResultadoCadastroVeiculoDTO cadastrar(CadastroVeiculoDTO dto, String motoristaId, String idempotencyKey) {
        if (dto == null) {
            throw new IllegalArgumentException("Os dados do veículo não podem ser nulos.");
        }
        if (motoristaId == null || motoristaId.isBlank()) {
            throw new IllegalArgumentException("O identificador do motorista é obrigatório.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("A chave de idempotência é obrigatória.");
        }

        // Validação prévia da existência do motorista
        final boolean motoristaExiste;
        try {
            motoristaExiste = usuarioRepository.existsById(motoristaId);
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Indisponibilidade temporária ao verificar existência do motorista {}: {}", motoristaId, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária de comunicação com o Couchbase. Repita a requisição com o mesmo Idempotency-Key.", e);
            }
            logger.error("Erro não transitório ao verificar existência do motorista {}: {}", motoristaId, e.getMessage(), e);
            throw e;
        }
        if (!motoristaExiste) {
            throw new UsuarioNaoAutenticadoException("Usuário motorista não encontrado no sistema.");
        }

        String chavePlaca = PlacaNormalizador.gerarChaveCanonica(dto.placa());
        String placaNormalizada = chavePlaca.substring("placa::".length());
        String fingerprintCalculado = IdempotencyFingerprintUtil.calcularFingerprint(
                motoristaId,
                placaNormalizada,
                dto.modelo(),
                dto.cor(),
                dto.capacidadePassageiros()
        );

        String idempKeyNormalizada = idempotencyKey.trim().toLowerCase(Locale.ROOT);
        String chaveIdempotencia = "idempotency::" + idempKeyNormalizada;

        Collection collection = couchbaseClientFactory.getDefaultCollection();
        Cluster cluster = couchbaseClientFactory.getCluster();

        // 1. Verificação prévia por KV lookup de repetição com mesma Idempotency-Key
        try {
            GetResult getResult = collection.get(chaveIdempotencia);
            JsonObject idempJson = getResult.contentAsObject();
            logger.info("Chave de idempotência {} localizada previamente. Validando e recuperando cadastro.", idempKeyNormalizada);
            return validarERecuperarCadastroExistente(idempJson, motoristaId, fingerprintCalculado);
        } catch (DocumentNotFoundException e) {
            // Chave de idempotência ainda não registrada; prossegue para a transação atômica
        } catch (DecodingFailureException e) {
            logger.error("Erro de decodificação no documento de idempotência {}: {}", chaveIdempotencia, e.getMessage(), e);
            throw new IllegalStateException("Falha irrecuperável ao decodificar registro de idempotência existente.", e);
        } catch (TimeoutException | RequestCanceledException e) {
            logger.warn("Timeout ou falha de comunicação na consulta prévia da chave de idempotência {}: {}", chaveIdempotencia, e.getMessage());
            throw new ServicoIndisponivelException(
                    "Indisponibilidade temporária de comunicação com o Couchbase. Repita a requisição com o mesmo Idempotency-Key.", e);
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Falha transitória no Couchbase na consulta prévia da chave de idempotência {}: {}", chaveIdempotencia, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Falha temporária no Couchbase durante a verificação de idempotência. Repita a requisição com o mesmo Idempotency-Key.", e);
            }
            if (contemCausa(e, DecodingFailureException.class)) {
                logger.error("Erro de decodificação ao recuperar cadastro existente para a chave {}: {}", chaveIdempotencia, e.getMessage(), e);
                throw new IllegalStateException("Falha irrecuperável ao decodificar dados do cadastro existente.", e);
            }
            logger.error("Erro não transitório no Couchbase na consulta prévia da chave {}: {}", chaveIdempotencia, e.getMessage(), e);
            throw e;
        }

        // 2. Preparação dos documentos para inserção atômica
        String veiculoId = UUID.randomUUID().toString();
        String modeloNormalizado = dto.modelo().trim();
        String corNormalizada = dto.cor().trim();
        Integer capacidade = dto.capacidadePassageiros();

        JsonObject docIdemp = JsonObject.create()
                .put("_class", RegistroIdempotencia.class.getName())
                .put("id", chaveIdempotencia)
                .put("idempotencyKey", idempKeyNormalizada)
                .put("motoristaId", motoristaId)
                .put("veiculoId", veiculoId)
                .put("placaCanonica", placaNormalizada)
                .put("requestFingerprint", fingerprintCalculado)
                .put("dataCriacao", LocalDateTime.now().toString());

        JsonObject docPlaca = JsonObject.create()
                .put("_class", PlacaVeiculo.class.getName())
                .put("id", chavePlaca)
                .put("veiculoId", veiculoId)
                .put("motoristaId", motoristaId)
                .put("idempotencyKey", idempKeyNormalizada)
                .put("dataRegistro", LocalDateTime.now().toString());

        JsonObject docVeiculo = JsonObject.create()
                .put("_class", Veiculo.class.getName())
                .put("id", veiculoId)
                .put("motoristaId", motoristaId)
                .put("placa", placaNormalizada)
                .put("modelo", modeloNormalizado)
                .put("cor", corNormalizada)
                .put("capacidadePassageiros", capacidade)
                .put("ativo", true);

        TransactionOptions options = TransactionOptions.transactionOptions()
                .durabilityLevel(this.durabilityLevel);

        // 3. Execução da transação ACID no Couchbase
        try {
            cluster.transactions().run(ctx -> {
                // Inserção da chave de idempotência
                try {
                    ctx.insert(collection, chaveIdempotencia, docIdemp);
                } catch (DocumentExistsException e) {
                    throw new ConflitoIdempotenciaException("Chave de idempotência já registrada.", e);
                }

                // Inserção da reserva determinística da placa
                try {
                    ctx.insert(collection, chavePlaca, docPlaca);
                } catch (DocumentExistsException e) {
                    throw new PlacaJaCadastradaException(
                            "A placa informada já está cadastrada para outro veículo.", e);
                }

                // Inserção do documento do veículo
                ctx.insert(collection, veiculoId, docVeiculo);
            }, options);

            Veiculo veiculoCriado = new Veiculo(
                    veiculoId,
                    motoristaId,
                    placaNormalizada,
                    modeloNormalizado,
                    corNormalizada,
                    capacidade,
                    true
            );

            logger.info("Veículo {} cadastrado com sucesso para o motorista {} com placa {}",
                    veiculoId, motoristaId, placaNormalizada);
            return new ResultadoCadastroVeiculoDTO(VeiculoMapper.paraResposta(veiculoCriado), true);

        } catch (TransactionCommitAmbiguousException e) {
            logger.warn("Commit ambíguo durante o cadastro do veículo para o motorista {} e placa {}: {}",
                    motoristaId, placaNormalizada, e.getMessage());
            throw new TransacaoAmbiguaException(
                    "A confirmação da transação é ambígua devido a timeout ou instabilidade de rede. "
                            + "Repita a requisição utilizando o mesmo cabeçalho Idempotency-Key.", e);

        } catch (TransactionExpiredException e) {
            logger.warn("Transação expirada (timeout geral de retries) para o motorista {} e placa {}: {}",
                    motoristaId, placaNormalizada, e.getMessage());
            throw new ContencaoConcorrenteException(
                    "Tempo limite esgotado devido a contenção temporária no Couchbase. Repita a requisição com o mesmo Idempotency-Key.", e);

        } catch (TransactionFailedException e) {
            // 1. Duplicidade real de placa confirmada pela chave determinística placa::
            if (contemCausa(e, PlacaJaCadastradaException.class)) {
                logger.info("Cadastro rejeitado por duplicidade de placa: {} (canônica: {})", dto.placa(), placaNormalizada);
                throw new PlacaJaCadastradaException(
                        "A placa " + dto.placa() + " já está cadastrada para outro veículo no sistema.");
            }

            // 2. Colisão na chave de idempotência (duas requisições disputando a mesma chave)
            if (contemCausa(e, ConflitoIdempotenciaException.class)) {
                logger.info("Conflito na chave de idempotência {}. Recuperando resultado da transação concorrente.", idempKeyNormalizada);
                return recuperarResultadoConcorrente(collection, chaveIdempotencia, motoristaId, fingerprintCalculado);
            }

            // 3. Contenção transitória no Couchbase (CAS mismatch, write-write conflict, locks concorrentes)
            if (ehContencaoTransitoria(e)) {
                logger.warn("Falha por contenção transitória no Couchbase para o motorista {}: {}", motoristaId, e.getMessage());
                throw new ContencaoConcorrenteException(
                        "Operação não pôde ser concluída devido à contenção concorrente no Couchbase. "
                                + "Repita a requisição com o mesmo Idempotency-Key.", e);
            }

            // 4. Falhas de comunicação / timeout / cancelamento de requisição
            if (ehFalhaComunicacaoOuTimeout(e)) {
                logger.warn("Falha de comunicação ou timeout no Couchbase para o motorista {}: {}", motoristaId, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Comunicação com o Couchbase indisponível ou expirada temporariamente. "
                                + "Repita a requisição com o mesmo Idempotency-Key.", e);
            }

            // 5. Falhas definitivas ou irrecuperáveis de configuração/infraestrutura
            logger.error("Falha definitiva e não transitória na transação Couchbase para o motorista {}: {}", motoristaId, e.getMessage(), e);
            throw new IllegalStateException(
                    "Falha irrecuperável na transação do Couchbase: " + e.getMessage(), e);
        }
    }

    public Veiculo buscarEValidarProprietario(String veiculoId, String motoristaId) {
        Veiculo veiculo = veiculoRepository.findById(veiculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Veículo não encontrado com o ID: " + veiculoId));

        if (motoristaId == null || !Objects.equals(veiculo.getMotoristaId(), motoristaId)) {
            throw new AcessoNegadoException("O veículo informado não pertence ao motorista.");
        }

        return veiculo;
    }

    public VeiculoRespostaDTO buscarPorIdEValidarProprietario(String veiculoId, String motoristaId) {
        Veiculo veiculo = buscarEValidarProprietario(veiculoId, motoristaId);
        return VeiculoMapper.paraResposta(veiculo);
    }

    private ResultadoCadastroVeiculoDTO validarERecuperarCadastroExistente(JsonObject idempJson,
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

        String veiculoId = idempJson.getString("veiculoId");
        if (veiculoId == null || veiculoId.isBlank()) {
            throw new IllegalStateException("Registro de idempotência inconsistente: identificador do veículo ausente.");
        }

        final Veiculo veiculo;
        try {
            veiculo = veiculoRepository.findById(veiculoId)
                    .orElseThrow(() -> new VeiculoNaoVisivelException(
                            "Registro de idempotência encontrado, mas o veículo correspondente ainda não está disponível. "
                                    + "Repita a requisição com a mesma Idempotency-Key."));
        } catch (VeiculoNaoVisivelException e) {
            throw e;
        } catch (CouchbaseException | DataAccessException e) {
            if (ehIndisponibilidadeTransitoriaLeitura(e)) {
                logger.warn("Indisponibilidade temporária ao consultar o veículo {} no Couchbase: {}", veiculoId, e.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária ao consultar o veículo no Couchbase. Repita a requisição com o mesmo Idempotency-Key.", e);
            }
            if (contemCausa(e, DecodingFailureException.class)) {
                logger.error("Erro de decodificação ao consultar o veículo {}: {}", veiculoId, e.getMessage(), e);
                throw new IllegalStateException("Falha irrecuperável ao decodificar documento do veículo existente.", e);
            }
            logger.error("Erro não transitório ao consultar o veículo {}: {}", veiculoId, e.getMessage(), e);
            throw e;
        }

        return new ResultadoCadastroVeiculoDTO(VeiculoMapper.paraResposta(veiculo), false);
    }

    private ResultadoCadastroVeiculoDTO recuperarResultadoConcorrente(Collection collection,
                                                                       String chaveIdempotencia,
                                                                       String motoristaId,
                                                                       String fingerprintCalculado) {
        int[] pausasMs = {50, 100, 150};
        for (int pausa : pausasMs) {
            try {
                Thread.sleep(pausa);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrompida durante a espera de recuperação concorrente da chave {}", chaveIdempotencia);
                throw new ContencaoConcorrenteException(
                        "Operação interrompida enquanto aguardava a conclusão da requisição concorrente. Repita a requisição com o mesmo Idempotency-Key.", ie);
            }

            try {
                GetResult getResult = collection.get(chaveIdempotencia);
                JsonObject idempJson = getResult.contentAsObject();
                return validarERecuperarCadastroExistente(idempJson, motoristaId, fingerprintCalculado);
            } catch (DocumentNotFoundException notFound) {
                // Apenas a ausência do documento indica que a transação concorrente vencedora ainda não concluiu o commit
                logger.debug("Chave de idempotência {} ainda não encontrada na tentativa. Aguardando commit da vencedora.", chaveIdempotencia);
            } catch (VeiculoNaoVisivelException veiculoNaoVisivel) {
                logger.debug("Chave de idempotência {} localizada, mas documento do veículo ainda não visível na tentativa. Aguardando commit da vencedora.", chaveIdempotencia);
            } catch (AcessoNegadoException | IdempotencyPayloadMismatchException regraNegocioEx) {
                // Violações de posse da chave ou divergência de payload não devem ser mascaradas
                throw regraNegocioEx;
            } catch (DecodingFailureException decEx) {
                logger.error("Erro de decodificação no documento de idempotência {}: {}", chaveIdempotencia, decEx.getMessage(), decEx);
                throw new IllegalStateException("Falha irrecuperável ao decodificar registro de idempotência existente.", decEx);
            } catch (TimeoutException | RequestCanceledException netEx) {
                logger.warn("Timeout ou falha de comunicação durante a recuperação da chave de idempotência {}: {}", chaveIdempotencia, netEx.getMessage());
                throw new ServicoIndisponivelException(
                        "Indisponibilidade temporária de comunicação com o Couchbase durante a verificação de concorrência. Repita a requisição com o mesmo Idempotency-Key.", netEx);
            } catch (CouchbaseException | DataAccessException cbEx) {
                if (ehIndisponibilidadeTransitoriaLeitura(cbEx)) {
                    logger.warn("Falha temporária no Couchbase na recuperação da chave {}: {}", chaveIdempotencia, cbEx.getMessage());
                    throw new ServicoIndisponivelException(
                            "Falha temporária no Couchbase durante a verificação de concorrência. Repita a requisição com o mesmo Idempotency-Key.", cbEx);
                }
                if (contemCausa(cbEx, DecodingFailureException.class)) {
                    logger.error("Erro de decodificação na recuperação da chave {}: {}", chaveIdempotencia, cbEx.getMessage(), cbEx);
                    throw new IllegalStateException("Falha irrecuperável ao decodificar dados do cadastro existente.", cbEx);
                }
                logger.error("Erro não transitório no Couchbase na recuperação da chave {}: {}", chaveIdempotencia, cbEx.getMessage(), cbEx);
                throw cbEx;
            }
        }

        // Se após as 3 tentativas o documento ainda não estiver disponível, nunca presume rollback.
        logger.warn("Resultado da requisição concorrente para a chave {} não confirmado após as tentativas.", chaveIdempotencia);
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
                            + "DurabilityLevel.NONE não é permitido para transações do cadastro de veículos.");
        }
        return level;
    }

    private static class VeiculoNaoVisivelException extends ContencaoConcorrenteException {
        public VeiculoNaoVisivelException(String mensagem) {
            super(mensagem);
        }
    }
}
