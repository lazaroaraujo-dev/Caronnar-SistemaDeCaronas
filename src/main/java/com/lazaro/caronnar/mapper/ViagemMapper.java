package com.lazaro.caronnar.mapper;

import com.couchbase.client.java.json.JsonObject;
import com.lazaro.caronnar.dto.rota.CalculoRotaDTO;
import com.lazaro.caronnar.dto.viagem.CadastroViagemDTO;
import com.lazaro.caronnar.dto.viagem.LocalizacaoDTO;
import com.lazaro.caronnar.dto.viagem.VeiculoSnapshotDTO;
import com.lazaro.caronnar.dto.viagem.ViagemRespostaDTO;
import com.lazaro.caronnar.model.embedded.Localizacao;
import com.lazaro.caronnar.model.embedded.VeiculoSnapshot;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.model.entities.Viagem;
import com.lazaro.caronnar.model.enums.StatusViagem;
import org.springframework.data.couchbase.core.convert.CouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class ViagemMapper {

    private ViagemMapper() {
    }

    public static Localizacao paraLocalizacao(LocalizacaoDTO dto) {
        if (dto == null) {
            return null;
        }
        return new Localizacao(
                dto.cidade() != null ? dto.cidade().trim() : null,
                dto.estado() != null ? dto.estado().trim().toUpperCase(Locale.ROOT) : null,
                dto.endereco() != null ? dto.endereco().trim() : null,
                dto.latitude(),
                dto.longitude()
        );
    }

    public static LocalizacaoDTO paraLocalizacaoDTO(Localizacao localizacao) {
        if (localizacao == null) {
            return null;
        }
        return new LocalizacaoDTO(
                localizacao.getCidade(),
                localizacao.getEstado(),
                localizacao.getEndereco(),
                localizacao.getLatitude(),
                localizacao.getLongitude()
        );
    }

    public static VeiculoSnapshot paraVeiculoSnapshot(Veiculo veiculo) {
        if (veiculo == null) {
            return null;
        }
        return new VeiculoSnapshot(
                veiculo.getPlaca(),
                veiculo.getModelo(),
                veiculo.getCor()
        );
    }

    public static VeiculoSnapshotDTO paraVeiculoSnapshotDTO(VeiculoSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new VeiculoSnapshotDTO(
                snapshot.getPlaca(),
                snapshot.getModelo(),
                snapshot.getCor()
        );
    }

    public static LocalDateTime normalizarParaMilissegundos(LocalDateTime dataHora) {
        if (dataHora == null) {
            return null;
        }
        return dataHora.truncatedTo(ChronoUnit.MILLIS);
    }

    public static Viagem paraNovaEntidade(CadastroViagemDTO dto,
                                          String viagemId,
                                          String motoristaId,
                                          Veiculo veiculo,
                                          CalculoRotaDTO calculoRota,
                                          LocalDateTime dataCriacao) {
        if (dto == null || veiculo == null || calculoRota == null) {
            throw new IllegalArgumentException("Os dados para construção da viagem não podem ser nulos.");
        }

        Viagem viagem = new Viagem();
        viagem.setId(viagemId);
        viagem.setVersion(null);
        viagem.setMotoristaId(motoristaId);
        viagem.setVeiculoId(veiculo.getId());
        viagem.setVeiculoSnapshot(paraVeiculoSnapshot(veiculo));
        viagem.setOrigem(paraLocalizacao(dto.origem()));
        viagem.setDestino(paraLocalizacao(dto.destino()));
        viagem.setDataHoraPartida(normalizarParaMilissegundos(dto.dataHoraPartida()));
        viagem.setVagasTotais(dto.vagasTotais());
        viagem.setVagasDisponiveis(dto.vagasTotais());
        viagem.setValorPorPassageiro(dto.valorPorPassageiro());
        viagem.setDistanciaKm(calculoRota.distanciaKm());
        viagem.setDuracaoMinutos(calculoRota.duracaoMinutos());
        viagem.setStatus(StatusViagem.AGENDADA);
        viagem.setDataCriacao(normalizarParaMilissegundos(dataCriacao));

        return viagem;
    }

    public static JsonObject paraJsonObjectCompatisComCouchbaseConverter(Viagem viagem,
                                                                          CouchbaseConverter couchbaseConverter) {
        if (viagem == null || couchbaseConverter == null) {
            throw new IllegalArgumentException("A viagem e o conversor do Couchbase são obrigatórios.");
        }
        CouchbaseDocument target = new CouchbaseDocument(viagem.getId());
        couchbaseConverter.write(viagem, target);
        if (viagem.getValorPorPassageiro() != null) {
            target.put("valorPorPassageiro", viagem.getValorPorPassageiro().toPlainString());
        }
        return JsonObject.from(target.export());
    }

    public static ViagemRespostaDTO paraResposta(Viagem viagem) {
        if (viagem == null) {
            return null;
        }
        return new ViagemRespostaDTO(
                viagem.getId(),
                viagem.getMotoristaId(),
                viagem.getVeiculoId(),
                paraVeiculoSnapshotDTO(viagem.getVeiculoSnapshot()),
                paraLocalizacaoDTO(viagem.getOrigem()),
                paraLocalizacaoDTO(viagem.getDestino()),
                viagem.getDataHoraPartida(),
                viagem.getVagasTotais(),
                viagem.getVagasDisponiveis(),
                viagem.getValorPorPassageiro(),
                viagem.getDistanciaKm(),
                viagem.getDuracaoMinutos(),
                viagem.getStatus(),
                viagem.getDataCriacao()
        );
    }
}
