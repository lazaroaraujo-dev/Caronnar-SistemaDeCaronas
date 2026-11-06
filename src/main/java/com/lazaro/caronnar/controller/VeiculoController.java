package com.lazaro.caronnar.controller;

import com.lazaro.caronnar.config.OpenApiConfig;
import com.lazaro.caronnar.dto.veiculo.CadastroVeiculoDTO;
import com.lazaro.caronnar.dto.veiculo.ResultadoCadastroVeiculoDTO;
import com.lazaro.caronnar.dto.veiculo.VeiculoRespostaDTO;
import com.lazaro.caronnar.exception.IdempotencyKeyInvalidaException;
import com.lazaro.caronnar.exception.ServicoIndisponivelException;
import com.lazaro.caronnar.exception.UsuarioNaoAutenticadoException;
import com.lazaro.caronnar.service.VeiculoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/veiculos")
@Tag(name = "Veículos", description = "Endpoints para gerenciamento de veículos")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class VeiculoController {

    private static final Pattern PADRAO_UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final VeiculoService veiculoService;
    private final boolean cadastroAtivo;

    public VeiculoController(VeiculoService veiculoService,
                             @Value("${app.veiculo.cadastro-ativo:false}") boolean cadastroAtivo) {
        this.veiculoService = veiculoService;
        this.cadastroAtivo = cadastroAtivo;
    }

    /**
     * Cadastra um veículo para o usuário motorista autenticado.
     * O ID do motorista é obtido exclusivamente a partir do "sub" do JWT validado (Authentication#getName()).
     * O cabeçalho Idempotency-Key é obrigatório (UUID v4) para garantir a idempotência estrita.
     */
    @PostMapping
    @Operation(
            summary = "Cadastra um novo veículo para o motorista autenticado",
            description = "Realiza o cadastro atômico do veículo e da reserva de placa canônica usando transações Couchbase. "
                    + "Requer o cabeçalho 'Idempotency-Key' (UUID v4). "
                    + "Retorna 201 Created se o cadastro for novo, ou 200 OK caso a mesma Idempotency-Key já tenha sido processada com sucesso anteriormente."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Veículo cadastrado com sucesso (nova criação).",
                    content = @Content(schema = @Schema(implementation = VeiculoRespostaDTO.class))
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "Requisição idempotente processada com sucesso (veículo previamente cadastrado com a mesma chave).",
                    content = @Content(schema = @Schema(implementation = VeiculoRespostaDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Cabeçalho Idempotency-Key ausente ou inválido (deve ser UUID v4), ou dados do veículo inválidos.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado ou motorista não encontrado.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "A chave de idempotência informada pertence a outro motorista.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Placa informada já está cadastrada para outro veículo no sistema (considerando formatos antigo e Mercosul).",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Chave de idempotência já utilizada com parâmetros ou dados divergentes.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Serviço temporariamente indisponível (cadastro desativado, commit ambíguo ou contenção transitória).",
                    content = @Content
            )
    })
    public ResponseEntity<VeiculoRespostaDTO> cadastrar(
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Identificador único de idempotência no formato UUID v4.",
                    example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
            )
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CadastroVeiculoDTO dto,
            Authentication authentication) {

        if (!cadastroAtivo) {
            throw new ServicoIndisponivelException(
                    "O cadastro de veículos está temporariamente indisponível para manutenção.");
        }

        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UsuarioNaoAutenticadoException("Usuário não autenticado.");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyInvalidaException("O cabeçalho 'Idempotency-Key' é obrigatório.");
        }

        String chaveNormalizada = idempotencyKey.trim().toLowerCase(Locale.ROOT);
        if (!PADRAO_UUID_V4.matcher(chaveNormalizada).matches()) {
            throw new IdempotencyKeyInvalidaException("O cabeçalho 'Idempotency-Key' deve ser um UUID v4 válido.");
        }

        ResultadoCadastroVeiculoDTO resultado = veiculoService.cadastrar(
                dto,
                authentication.getName(),
                chaveNormalizada
        );

        HttpStatus statusHttp = resultado.novoCadastro() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(statusHttp).body(resultado.resposta());
    }
}
