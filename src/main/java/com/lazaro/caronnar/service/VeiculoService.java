package com.lazaro.caronnar.service;

import com.lazaro.caronnar.dto.veiculo.CadastroVeiculoDTO;
import com.lazaro.caronnar.dto.veiculo.VeiculoRespostaDTO;
import com.lazaro.caronnar.exception.AcessoNegadoException;
import com.lazaro.caronnar.exception.RecursoNaoEncontradoException;
import com.lazaro.caronnar.mapper.VeiculoMapper;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.repository.VeiculoRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;

    public VeiculoService(VeiculoRepository veiculoRepository) {
        this.veiculoRepository = veiculoRepository;
    }

    public VeiculoRespostaDTO cadastrar(CadastroVeiculoDTO dto, String motoristaId) {
        if (dto == null) {
            throw new IllegalArgumentException("Os dados do veículo não podem ser nulos.");
        }
        if (motoristaId == null || motoristaId.isBlank()) {
            throw new IllegalArgumentException("O identificador do motorista é obrigatório.");
        }

        Veiculo veiculo = VeiculoMapper.paraEntidade(dto);
        veiculo.setId(UUID.randomUUID().toString());
        veiculo.setMotoristaId(motoristaId);

        Veiculo salvo = veiculoRepository.save(veiculo);

        return VeiculoMapper.paraResposta(salvo);
    }

    public Veiculo buscarEValidarProprietario(String veiculoId, String motoristaId) {
        Veiculo veiculo = veiculoRepository.findById(veiculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Veículo não encontrado com o ID: " + veiculoId));

        if (motoristaId == null || !Objects.equals(veiculo.getMotoristaId(), motoristaId)) {
            throw new AcessoNegadoException("O veículo informado não pertence ao motorista.");
        }

        return veiculo;
    }

    public VeiculoRespostaDTO buscarPorIdEValidarProprietario(String veiculoId, String motoristaId) {
        Veiculo veiculo = buscarEValidarProprietario(veiculoId, motoristaId);
        return VeiculoMapper.paraResposta(veiculo);
    }
}
