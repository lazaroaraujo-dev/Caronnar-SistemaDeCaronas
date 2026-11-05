package com.lazaro.caronnar.repository;

import com.lazaro.caronnar.model.entities.Viagem;
import org.springframework.data.couchbase.repository.CouchbaseRepository;

public interface ViagemRepository extends CouchbaseRepository<Viagem, String> {
}
