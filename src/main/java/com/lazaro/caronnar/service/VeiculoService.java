package com.lazaro.caronnar.service;

import com.lazaro.caronnar.exception.AcessoNegadoException;
import com.lazaro.caronnar.exception.RecursoNaoEncontradoException;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.repository.VeiculoRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;

    public VeiculoService(VeiculoRepository veiculoRepository) {
        this.veiculoRepository = veiculoRepository;
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
}
