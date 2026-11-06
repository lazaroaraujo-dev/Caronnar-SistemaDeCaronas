package com.lazaro.caronnar.controller;

import com.lazaro.caronnar.config.OpenApiConfig;
import com.lazaro.caronnar.dto.viagem.CadastroViagemDTO;
import com.lazaro.caronnar.dto.viagem.FiltroBuscaViagemDTO;
import com.lazaro.caronnar.dto.viagem.ResultadoPublicacaoViagemDTO;
import com.lazaro.caronnar.dto.viagem.ViagemRespostaDTO;
import com.lazaro.caronnar.service.ViagemService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/viagens")
@Tag(name = "Viagens", description = "Endpoints para publicação e consulta de viagens")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class ViagemController {

    private final ViagemService viagemService;

    public ViagemController(ViagemService viagemService) {
        this.viagemService = viagemService;
    }

    @PostMapping
    @Operation(
            summary = "Publica uma nova viagem para o motorista autenticado",
            description = "Realiza a publicação atômica e idempotente da viagem calculando a rota via OSRM e persistindo via transações Couchbase. "
                    + "Requer o cabeçalho 'Idempotency-Key' (UUID v4). "
                    + "Retorna 201 Created se a publicação for nova, ou 200 OK caso a mesma Idempotency-Key já tenha sido processada com sucesso anteriormente."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Viagem publicada com sucesso (nova publicação).",
                    content = @Content(schema = @Schema(implementation = ViagemRespostaDTO.class))
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "Requisição idempotente processada com sucesso (viagem previamente publicada com a mesma chave).",
                    content = @Content(schema = @Schema(implementation = ViagemRespostaDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Cabeçalho Idempotency-Key ausente ou inválido (deve ser UUID v4), coordenadas inválidas, data de partida inválida ou dados malformados.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado ou motorista não encontrado.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "O veículo informado ou a chave de idempotência pertence a outro motorista.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Veículo informado não encontrado.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Violação de regra de negócio, rota não encontrada ou chave de idempotência reutilizada com parâmetros divergentes.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Resposta inválida do serviço externo de roteamento (OSRM).",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Serviço temporariamente indisponível (publicação desativada, OSRM/Couchbase indisponível, commit ambíguo ou contenção transitória).",
                    content = @Content
            )
    })
    public ResponseEntity<ViagemRespostaDTO> publicar(
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Identificador único de idempotência no formato UUID v4.",
                    example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
            )
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CadastroViagemDTO dto,
            Authentication authentication) {

        ResultadoPublicacaoViagemDTO resultado = viagemService.publicar(dto, authentication, idempotencyKey);
        HttpStatus statusHttp = resultado.novaPublicacao() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(statusHttp).body(resultado.resposta());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Busca os detalhes de uma viagem pelo seu identificador",
            description = "Retorna os dados completos da viagem correspondente ao ID informado."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Viagem encontrada com sucesso.",
                    content = @Content(schema = @Schema(implementation = ViagemRespostaDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Viagem não encontrada.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Indisponibilidade temporária ao consultar o Couchbase.",
                    content = @Content
            )
    })
    public ResponseEntity<ViagemRespostaDTO> buscarPorId(@PathVariable String id) {
        ViagemRespostaDTO resposta = viagemService.buscarPorId(id);
        return ResponseEntity.ok(resposta);
    }

    @GetMapping
    @Operation(
            summary = "Pesquisa viagens agendadas e disponíveis com filtros e paginação",
            description = "Retorna a lista de viagens agendadas futuras com vagas disponíveis de acordo com os filtros informados, ordenadas por data e hora de partida crescente."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Pesquisa realizada com sucesso."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Data de partida anterior à data atual ou parâmetro de consulta em formato inválido.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário não autenticado.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Parâmetros de vagas mínimas ou paginação fora dos limites permitidos.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Indisponibilidade temporária ao pesquisar viagens no Couchbase.",
                    content = @Content
            )
    })
    public ResponseEntity<List<ViagemRespostaDTO>> pesquisar(
            @RequestParam(required = false) String origemCidade,
            @RequestParam(required = false) String origemEstado,
            @RequestParam(required = false) String destinoCidade,
            @RequestParam(required = false) String destinoEstado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataPartida,
            @RequestParam(defaultValue = "1") Integer vagasMinimas,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanho) {

        FiltroBuscaViagemDTO filtro = new FiltroBuscaViagemDTO(
                origemCidade,
                origemEstado,
                destinoCidade,
                destinoEstado,
                dataPartida,
                vagasMinimas,
                pagina,
                tamanho
        );

        List<ViagemRespostaDTO> viagens = viagemService.pesquisarViagens(filtro);
        return ResponseEntity.ok(viagens);
    }
}
