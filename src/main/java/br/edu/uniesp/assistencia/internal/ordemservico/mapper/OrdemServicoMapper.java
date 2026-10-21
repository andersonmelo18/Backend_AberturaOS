package br.edu.uniesp.assistencia.internal.ordemservico.mapper;

import br.edu.uniesp.assistencia.internal.cliente.entity.ClienteEntity;
import br.edu.uniesp.assistencia.internal.equipamento.entity.EquipamentoEntity;
import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoRequest;
import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoResponse;
import br.edu.uniesp.assistencia.internal.ordemservico.entity.OrdemServicoEntity;

public final class OrdemServicoMapper {

    private OrdemServicoMapper() {
    }

    public static OrdemServicoEntity paraEntidade(CriarOrdemServicoRequest request,
                                                  ClienteEntity cliente,
                                                  EquipamentoEntity equipamento) {
        return new OrdemServicoEntity(
                cliente,
                equipamento,
                request.descricaoProblema()
        );
    }

    public static CriarOrdemServicoResponse paraResposta(OrdemServicoEntity os) {
        return new CriarOrdemServicoResponse(
                os.getId(),
                os.getNumeroProtocolo(),
                os.getStatus(),
                os.getDataAbertura()
        );
    }
}
