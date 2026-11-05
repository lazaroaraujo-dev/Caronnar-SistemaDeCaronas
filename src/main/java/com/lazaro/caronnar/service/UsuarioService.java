package com.lazaro.caronnar.service;

import com.lazaro.caronnar.dto.usuario.CadastroUsuarioDTO;
import com.lazaro.caronnar.dto.usuario.UsuarioRespostaDTO;
import com.lazaro.caronnar.exception.EmailJaCadastradoException;
import com.lazaro.caronnar.exception.RecursoNaoEncontradoException;
import com.lazaro.caronnar.exception.SenhaInvalidaException;
import com.lazaro.caronnar.model.entities.Usuario;
import com.lazaro.caronnar.model.enums.PapelUsuario;
import com.lazaro.caronnar.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UsuarioService {

    private static final int LIMITE_BYTES_BCRYPT = 72;

    private static final Set<String> SENHAS_COMUNS = Set.of(
            "1234",
            "12345678",
            "password",
            "qwerty123"
    );

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UsuarioRespostaDTO cadastrar(CadastroUsuarioDTO dto) {
        validarSenha(dto.senha());
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new EmailJaCadastradoException(dto.email());
        }

        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setSenha(passwordEncoder.encode(dto.senha()));
        usuario.setTelefone(dto.telefone());
        usuario.setPapeis(Set.of(PapelUsuario.PASSAGEIRO, PapelUsuario.MOTORISTA));
        usuario.setDataCadastro(LocalDateTime.now());

        Usuario salvo = usuarioRepository.save(usuario);

        return toRespostaDTO(salvo);
    }

    public UsuarioRespostaDTO buscarPorId(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuário não encontrado com o ID: " + id));

        return toRespostaDTO(usuario);
    }

    private void validarSenha(String senha) {
        if (senha.getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES_BCRYPT) {
            throw new SenhaInvalidaException(
                    "A senha é muito longa: o limite é de 72 bytes quando codificada em UTF-8.");
        }

        if (SENHAS_COMUNS.contains(senha.toLowerCase(Locale.ROOT))) {
            throw new SenhaInvalidaException(
                    "A senha informada é muito comum. Escolha uma senha mais segura.");
        }
    }

    private UsuarioRespostaDTO toRespostaDTO(Usuario usuario) {
        return new UsuarioRespostaDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getPapeis(),
                usuario.getDataCadastro()
        );
    }
}
