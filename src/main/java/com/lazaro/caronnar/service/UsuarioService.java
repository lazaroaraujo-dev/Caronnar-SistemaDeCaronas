package com.lazaro.caronnar.service;

import com.lazaro.caronnar.dto.usuario.CadastroUsuarioDTO;
import com.lazaro.caronnar.dto.usuario.UsuarioRespostaDTO;
import com.lazaro.caronnar.exception.EmailJaCadastradoException;
import com.lazaro.caronnar.exception.RecursoNaoEncontradoException;
import com.lazaro.caronnar.model.entities.Usuario;
import com.lazaro.caronnar.model.enums.PapelUsuario;
import com.lazaro.caronnar.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UsuarioRespostaDTO cadastrar(CadastroUsuarioDTO dto) {
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new EmailJaCadastradoException(dto.email());
        }

        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setSenha(passwordEncoder.encode(dto.senha()));
        usuario.setTelefone(dto.telefone());
        usuario.setPapeis(Set.of(PapelUsuario.PASSAGEIRO));
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
