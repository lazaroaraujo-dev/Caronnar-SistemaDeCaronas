package com.lazaro.caronnar;

import com.lazaro.caronnar.model.entities.Usuario;
import com.lazaro.caronnar.model.enums.PapelUsuario;
import com.lazaro.caronnar.repository.UsuarioRepository;
import com.lazaro.caronnar.security.JwtService;
import com.lazaro.caronnar.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.jwt.secret=test-jwt-secret-key-with-at-least-32-bytes",
        "security.jwt.expiration-minutes=60"
})
@AutoConfigureMockMvc
class AutenticacaoHttpTest {

    private static final String EMAIL = "usuario@exemplo.com";
    private static final String SENHA = "SenhaSegura#2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId("usuario-1");
        usuario.setNome("Usuário Teste");
        usuario.setEmail(EMAIL);
        usuario.setSenha(passwordEncoder.encode(SENHA));
        usuario.setTelefone("11999999999");
        usuario.setPapeis(Set.of(PapelUsuario.PASSAGEIRO));
        usuario.setDataCadastro(LocalDateTime.now());

        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
    }

    @Test
    void loginComCredenciaisValidasRetornaToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "usuario@exemplo.com",
                                  "senha": "SenhaSegura#2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEmSegundos").value(3600))
                .andExpect(jsonPath("$.id").value("usuario-1"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.papeis[0]").value("ROLE_PASSAGEIRO"))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void loginComCredenciaisInvalidasRetorna401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "usuario@exemplo.com",
                                  "senha": "SenhaErrada#2026"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Não autorizado"));
    }

    @Test
    void endpointProtegidoSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/usuarios/usuario-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoComTokenValidoRetorna200() throws Exception {
        when(usuarioRepository.findById("usuario-1")).thenReturn(Optional.of(usuario));

        String token = jwtService.gerarToken(new UsuarioAutenticado(usuario));

        mockMvc.perform(get("/api/usuarios/usuario-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("usuario-1"))
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mockMvc.perform(get("/api/usuarios/usuario-1")
                        .header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value(
                        "Autenticação necessária ou token inválido."));
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        String token = tokenExpirado();

        mockMvc.perform(get("/api/usuarios/usuario-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String tokenExpirado() {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        Instant expiresAt = Instant.now().minus(Duration.ofHours(1));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtService.ISSUER)
                .subject(usuario.getId())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void cadastroPublicoContinuaAcessivel() throws Exception {
        when(usuarioRepository.existsByEmail("novo@exemplo.com")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Novo Usuário",
                                  "email": "novo@exemplo.com",
                                  "senha": "OutraSenha#2026",
                                  "telefone": "11888888888"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("novo@exemplo.com"));
    }

    @Test
    void documentacaoSwaggerContinuaAcessivel() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
