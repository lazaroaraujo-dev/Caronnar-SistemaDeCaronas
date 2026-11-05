package com.lazaro.caronnar.security;

import com.lazaro.caronnar.model.entities.Usuario;
import com.lazaro.caronnar.model.enums.PapelUsuario;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class UsuarioAutenticado implements UserDetails, CredentialsContainer {

    private final String id;
    private final String nome;
    private final String email;
    private String senha;
    private final Set<GrantedAuthority> authorities;

    public UsuarioAutenticado(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("O usuário não pode ser nulo.");
        }

        this.id = usuario.getId();
        this.nome = usuario.getNome();
        this.email = usuario.getEmail();
        this.senha = usuario.getSenha();

        Set<PapelUsuario> papeis = usuario.getPapeis();
        if (papeis == null || papeis.isEmpty()) {
            this.authorities = Collections.emptySet();
        } else {
            this.authorities = papeis.stream()
                    .filter(papel -> papel != null)
                    .map(papel -> new SimpleGrantedAuthority("ROLE_" + papel.name()))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.senha = null;
    }
}
