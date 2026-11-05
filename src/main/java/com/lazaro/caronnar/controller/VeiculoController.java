package com.lazaro.caronnar.controller;

import com.lazaro.caronnar.config.OpenApiConfig;
import com.lazaro.caronnar.dto.veiculo.CadastroVeiculoDTO;
import com.lazaro.caronnar.dto.veiculo.VeiculoRespostaDTO;
import com.lazaro.caronnar.exception.ServicoIndisponivelException;
import com.lazaro.caronnar.service.VeiculoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/veiculos")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class VeiculoController {

    private final VeiculoService veiculoService;
    private final boolean cadastroAtivo;

    public VeiculoController(VeiculoService veiculoService,
                             @Value("${app.veiculo.cadastro-ativo:false}") boolean cadastroAtivo) {
        this.veiculoService = veiculoService;
        this.cadastroAtivo = cadastroAtivo;
    }

    /**
     * Cadastra um veículo para o usuário autenticado.
     * O ID do motorista é o "sub" do JWT validado (Authentication#getName()),
     * nunca um valor enviado pelo cliente.
     */
    @PostMapping
    public ResponseEntity<VeiculoRespostaDTO> cadastrar(@RequestBody @Valid CadastroVeiculoDTO dto,
                                                        Authentication authentication) {
        if (!cadastroAtivo) {
            throw new ServicoIndisponivelException(
                    "O cadastro de veículos está temporariamente indisponível para manutenção.");
        }
        VeiculoRespostaDTO resposta = veiculoService.cadastrar(dto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }
}
