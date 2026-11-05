package com.lazaro.caronnar.repository;

import com.lazaro.caronnar.model.entities.Veiculo;
import org.springframework.data.couchbase.repository.CouchbaseRepository;

public interface VeiculoRepository extends CouchbaseRepository<Veiculo, String> {
}
