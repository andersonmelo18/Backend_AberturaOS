package br.edu.uniesp.assistencia.internal.ordemservico.service;

import br.edu.uniesp.assistencia.shared.exception.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class ProtocoloService {

    private final JdbcClient jdbcClient;

    @Transactional(propagation = Propagation.MANDATORY)
    public String gerar(LocalDate data) {
        int numero = jdbcClient.sql("""
                INSERT INTO protocolos_ordem (data, ultimo_numero) VALUES (:data, 0)
                ON CONFLICT (data) DO UPDATE
                SET ultimo_numero = protocolos_ordem.ultimo_numero + 1
                WHERE protocolos_ordem.ultimo_numero < 65535
                RETURNING ultimo_numero
                """)
                .param("data", data)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new RegraNegocioException("Limite diário de protocolos atingido."));
        return "OS-" + data.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04X", numero);
    }
}
