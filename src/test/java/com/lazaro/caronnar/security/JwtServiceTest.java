package com.lazaro.caronnar.security;

import com.lazaro.caronnar.model.entities.Usuario;
import com.lazaro.caronnar.model.enums.PapelUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-key-with-at-least-32-bytes";

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private JwtService jwtService;
    private UsuarioAutenticado usuarioAutenticado;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        jwtDecoder = decoder;

        jwtService = new JwtService(jwtEncoder, 60);
        usuarioAutenticado = new UsuarioAutenticado(usuario());
    }

    @Test
    void deveGerarTokenComClaimsDoUsuario() {
        String token = jwtService.gerarToken(usuarioAutenticado);
        Jwt jwt = jwtDecoder.decode(token);

        assertEquals(JwtService.ISSUER, jwt.getClaimAsString("iss"));
        assertEquals("usuario-1", jwt.getSubject());
        assertEquals("usuario@exemplo.com", jwt.getClaimAsString("email"));
        assertEquals("Usuário Teste", jwt.getClaimAsString("nome"));
        assertEquals(Set.of("ROLE_PASSAGEIRO"),
                Set.copyOf(jwt.getClaimAsStringList("papeis")));
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
        assertEquals(3600, jwtService.getExpiracaoEmSegundos());
    }

    @Test
    void deveRejeitarTokenExpirado() {
        String token = tokenExpirado();

        assertThrows(JwtException.class, () -> jwtDecoder.decode(token));
    }

    private String tokenExpirado() {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        Instant expiresAt = Instant.now().minus(Duration.ofHours(1));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtService.ISSUER)
                .subject(usuarioAutenticado.getId())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId("usuario-1");
        usuario.setNome("Usuário Teste");
        usuario.setEmail("usuario@exemplo.com");
        usuario.setSenha("$2a$10$hash-de-teste");
        usuario.setTelefone("11999999999");
        usuario.setPapeis(Set.of(PapelUsuario.PASSAGEIRO));
        usuario.setDataCadastro(LocalDateTime.now());
        return usuario;
    }
}
