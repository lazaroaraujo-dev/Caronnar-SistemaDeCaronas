package com.lazaro.caronnar.controller;

import com.lazaro.caronnar.dto.auth.LoginRequisicaoDTO;
import com.lazaro.caronnar.dto.auth.LoginRespostaDTO;
import com.lazaro.caronnar.security.JwtService;
import com.lazaro.caronnar.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AutenticacaoController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginRespostaDTO> login(@RequestBody @Valid LoginRequisicaoDTO dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.senha())
        );

        UsuarioAutenticado usuario = (UsuarioAutenticado) authentication.getPrincipal();

        Set<String> papeis = usuario.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        String token = jwtService.gerarToken(usuario);

        LoginRespostaDTO resposta = new LoginRespostaDTO(
                token,
                "Bearer",
                jwtService.getExpiracaoEmSegundos(),
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                papeis
        );

        return ResponseEntity.ok(resposta);
    }
}
