package com.lazaro.caronnar.repository;

import com.lazaro.caronnar.model.entities.Usuario;
import org.springframework.data.couchbase.repository.CouchbaseRepository;

import java.util.Optional;

public interface UsuarioRepository extends CouchbaseRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}