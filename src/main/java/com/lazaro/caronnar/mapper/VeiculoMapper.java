package com.lazaro.caronnar.mapper;

import com.lazaro.caronnar.dto.veiculo.CadastroVeiculoDTO;
import com.lazaro.caronnar.dto.veiculo.VeiculoRespostaDTO;
import com.lazaro.caronnar.model.entities.Veiculo;
import com.lazaro.caronnar.util.PlacaNormalizador;

public final class VeiculoMapper {

    private VeiculoMapper() {
    }

    public static Veiculo paraEntidade(CadastroVeiculoDTO dto) {
        if (dto == null) {
            return null;
        }

        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(PlacaNormalizador.normalizar(dto.placa()));
        veiculo.setModelo(dto.modelo());
        veiculo.setCor(dto.cor());
        veiculo.setCapacidadePassageiros(dto.capacidadePassageiros());

        return veiculo;
    }

    public static VeiculoRespostaDTO paraResposta(Veiculo veiculo) {
        if (veiculo == null) {
            return null;
        }

        return new VeiculoRespostaDTO(
                veiculo.getId(),
                veiculo.getPlaca(),
                veiculo.getModelo(),
                veiculo.getCor(),
                veiculo.getCapacidadePassageiros(),
                veiculo.getAtivo()
        );
    }
}
