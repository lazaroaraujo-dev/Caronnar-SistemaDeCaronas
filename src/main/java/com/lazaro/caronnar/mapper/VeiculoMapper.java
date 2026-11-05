package com.lazaro.caronnar.mapper;

import com.lazaro.caronnar.dto.veiculo.CadastroVeiculoDTO;
import com.lazaro.caronnar.dto.veiculo.VeiculoRespostaDTO;
import com.lazaro.caronnar.model.entities.Veiculo;

public final class VeiculoMapper {

    private VeiculoMapper() {
    }

    public static Veiculo paraEntidade(CadastroVeiculoDTO dto) {
        if (dto == null) {
            return null;
        }

        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(dto.placa());
        veiculo.setModelo(dto.modelo());
        veiculo.setCor(dto.cor());

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
                veiculo.getCor()
        );
    }
}
