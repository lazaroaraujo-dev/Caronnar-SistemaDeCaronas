package com.lazaro.caronnar.repository;

import com.lazaro.caronnar.model.entities.Viagem;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ViagemRepository extends CouchbaseRepository<Viagem, String> {

    @Query("SELECT META(v).id AS __id, META(v).cas AS __cas, v.* "
            + "FROM #{#n1ql.bucket} v "
            + "WHERE v.`_class` = 'com.lazaro.caronnar.model.entities.Viagem' "
            + "AND v.status = $status "
            + "AND v.dataHoraPartida >= $inicioInclusivoEpochMillis "
            + "AND ($fimExclusivoEpochMillis IS NULL OR v.dataHoraPartida < $fimExclusivoEpochMillis) "
            + "AND v.vagasDisponiveis >= $vagasMinimas "
            + "AND ($origemEstadoNormalizado IS NULL OR LOWER(v.origem.estado) = $origemEstadoNormalizado) "
            + "AND ($origemCidadeNormalizada IS NULL OR LOWER(v.origem.cidade) = $origemCidadeNormalizada) "
            + "AND ($destinoEstadoNormalizado IS NULL OR LOWER(v.destino.estado) = $destinoEstadoNormalizado) "
            + "AND ($destinoCidadeNormalizada IS NULL OR LOWER(v.destino.cidade) = $destinoCidadeNormalizada) "
            + "ORDER BY v.dataHoraPartida ASC "
            + "LIMIT $tamanho OFFSET $offset")
    List<Viagem> buscarViagensDisponiveis(
            @Param("status") String status,
            @Param("inicioInclusivoEpochMillis") long inicioInclusivoEpochMillis,
            @Param("fimExclusivoEpochMillis") Long fimExclusivoEpochMillis,
            @Param("vagasMinimas") int vagasMinimas,
            @Param("origemCidadeNormalizada") String origemCidadeNormalizada,
            @Param("origemEstadoNormalizado") String origemEstadoNormalizado,
            @Param("destinoCidadeNormalizada") String destinoCidadeNormalizada,
            @Param("destinoEstadoNormalizado") String destinoEstadoNormalizado,
            @Param("tamanho") int tamanho,
            @Param("offset") long offset
    );
}
