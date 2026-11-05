package com.lazaro.caronnar.controller;

import com.lazaro.caronnar.config.OpenApiConfig;
import com.lazaro.caronnar.dto.usuario.CadastroUsuarioDTO;
import com.lazaro.caronnar.dto.usuario.UsuarioRespostaDTO;
import com.lazaro.caronnar.service.UsuarioService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioRespostaDTO> cadastrar(@RequestBody @Valid CadastroUsuarioDTO dto) {
        UsuarioRespostaDTO resposta = usuarioService.cadastrar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    public ResponseEntity<UsuarioRespostaDTO> buscarPorId(@PathVariable String id) {
        UsuarioRespostaDTO resposta = usuarioService.buscarPorId(id);
        return ResponseEntity.ok(resposta);
    }
}
