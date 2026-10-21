package br.edu.uniesp.assistencia;

import br.edu.uniesp.assistencia.internal.cliente.repository.ClienteRepository;
import br.edu.uniesp.assistencia.internal.equipamento.repository.EquipamentoRepository;
import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoRequest;
import br.edu.uniesp.assistencia.internal.ordemservico.entity.OrdemServicoEntity;
import br.edu.uniesp.assistencia.internal.ordemservico.service.OrdemServicoService;
import br.edu.uniesp.assistencia.internal.ordemservico.service.ProtocoloService;
import br.edu.uniesp.assistencia.shared.exception.RegraNegocioException;
import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AssistenciaUniespApplicationTests {

    private static EmbeddedPostgres postgres;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrdemServicoService ordemServicoService;

    @Autowired
    private ProtocoloService protocoloService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private EquipamentoRepository equipamentoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mvc;
    private Long clienteId;
    private Long equipamentoId;

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry properties) throws IOException {
        postgres = EmbeddedPostgres.start();
        properties.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
    }

    @AfterAll
    static void fecharBanco() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.execute("TRUNCATE clientes, protocolos_ordem RESTART IDENTITY CASCADE");
        clienteId = jdbc.queryForObject("""
                INSERT INTO clientes (nome, cpf, email, ativo)
                VALUES ('Ana', '52998224725', 'ana@example.com', true) RETURNING id
                """, Long.class);
        equipamentoId = jdbc.queryForObject("""
                INSERT INTO equipamentos (tipo, marca, modelo, cliente_id)
                VALUES ('Notebook', 'Dell', 'Inspiron', ?) RETURNING id
                """, Long.class, clienteId);
    }

    @Test
    void abrirOrdemComHistorico() throws Exception {
        var inicio = LocalDateTime.now();
        var resultado = mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(clienteId, equipamentoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.numeroProtocolo", matchesPattern("OS-[0-9]{8}-[0-9A-F]{4}")))
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.dataAbertura").isNotEmpty())
                .andReturn();

        String resposta = resultado.getResponse().getContentAsString();
        String data = JsonPath.read(resposta, "$.dataAbertura");
        var abertura = LocalDateTime.parse(data);
        assertFalse(abertura.isBefore(inicio));
        assertFalse(abertura.isAfter(LocalDateTime.now()));
        assertEquals(1L, contar("ordens_servico"));
        assertEquals(1L, contar("historico_status"));
        var historico = jdbc.queryForMap("SELECT * FROM historico_status");
        assertNull(historico.get("status_anterior"));
        assertEquals("RECEBIDA", historico.get("novo_status"));
        assertEquals("SISTEMA", historico.get("responsavel"));
        assertEquals("Abertura da ordem de serviço.", historico.get("observacao"));
        Number id = JsonPath.read(resposta, "$.id");
        assertEquals(id.longValue(), ((Number) historico.get("ordem_servico_id")).longValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"clienteId\":null,\"equipamentoId\":null,\"descricaoProblema\":null}"})
    void rejeitarCamposNulos(String payload) throws Exception {
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erros.clienteId").exists())
                .andExpect(jsonPath("$.erros.equipamentoId").exists())
                .andExpect(jsonPath("$.erros.descricaoProblema").exists());
        verificarSemOrdem();
    }

    @Test
    void rejeitarDescricaoEmBranco() throws Exception {
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(clienteId, equipamentoId).replace("Notebook não liga", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.descricaoProblema").exists());
        verificarSemOrdem();
    }

    @Test
    void rejeitarClienteInexistente() throws Exception {
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(999L, equipamentoId)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.recurso").value("Cliente"));
        verificarSemOrdem();
    }

    @Test
    void rejeitarEquipamentoInexistente() throws Exception {
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(clienteId, 999L)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.recurso").value("Equipamento"));
        verificarSemOrdem();
    }

    @Test
    void rejeitarClienteInativo() throws Exception {
        jdbc.update("UPDATE clientes SET ativo = false WHERE id = ?", clienteId);
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(clienteId, equipamentoId)))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Não é permitido abrir Ordem de Serviço para um cliente inativo."));
        verificarSemOrdem();
    }

    @Test
    void rejeitarEquipamentoDeOutroCliente() throws Exception {
        Long outroCliente = jdbc.queryForObject("""
                INSERT INTO clientes (nome, cpf, email, ativo)
                VALUES ('Bruno', '11144477735', 'bruno@example.com', true) RETURNING id
                """, Long.class);
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(outroCliente, equipamentoId)))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("O equipamento informado não pertence ao cliente indicado."));
        verificarSemOrdem();
    }

    @Test
    void protegerRegrasDiretamenteNaEntidade() {
        var cliente = clienteRepository.findById(clienteId).orElseThrow();
        var equipamento = equipamentoRepository.findById(equipamentoId).orElseThrow();
        cliente.inativar();
        assertThrows(RegraNegocioException.class, () -> new OrdemServicoEntity(cliente, equipamento, "Defeito"));
        cliente.ativar();
        equipamento.associarCliente(null);
        assertThrows(RegraNegocioException.class, () -> new OrdemServicoEntity(cliente, equipamento, "Defeito"));
    }

    @Test
    void gerarProtocolosUnicosEmAberturasSimultaneas() throws Exception {
        var request = new CriarOrdemServicoRequest(clienteId, equipamentoId, "Notebook não liga");
        var tarefas = IntStream.range(0, 20)
                .mapToObj(i -> (Callable<String>) () -> ordemServicoService.abrirOrdemServico(request).numeroProtocolo())
                .toList();
        var protocolos = new HashSet<String>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var resultado : executor.invokeAll(tarefas)) {
                assertTrue(protocolos.add(resultado.get()));
            }
        }
        assertEquals(20L, contar("ordens_servico"));
        assertEquals(20L, contar("historico_status"));
    }

    @Test
    void reiniciarSequenciaEmOutroDia() {
        var transacao = new TransactionTemplate(transactionManager);
        var data = LocalDate.of(2026, 10, 15);
        assertEquals("OS-20261015-0000", transacao.execute(status -> protocoloService.gerar(data)));
        assertEquals("OS-20261015-0001", transacao.execute(status -> protocoloService.gerar(data)));
        assertEquals("OS-20261016-0000", transacao.execute(status -> protocoloService.gerar(data.plusDays(1))));
    }

    @Test
    void rejeitarEsgotamentoDosQuatroDigitos() throws Exception {
        jdbc.update("INSERT INTO protocolos_ordem (data, ultimo_numero) VALUES (?, 65535)", LocalDate.now());
        mvc.perform(post("/ordens").contentType(MediaType.APPLICATION_JSON)
                        .content(payload(clienteId, equipamentoId)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value("Limite diário de protocolos atingido."));
        assertEquals(0L, contar("ordens_servico"));
        assertEquals(0L, contar("historico_status"));
    }

    @Test
    void desfazerOrdemHistoricoEProtocoloNaFalhaDaTransacao() {
        var transacao = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transacao.executeWithoutResult(status -> {
            ordemServicoService.abrirOrdemServico(new CriarOrdemServicoRequest(clienteId, equipamentoId, "Defeito"));
            throw new IllegalStateException("Falha na transação");
        }));
        verificarSemOrdem();
    }

    private String payload(Long cliente, Long equipamento) {
        return """
                {"clienteId": %d, "equipamentoId": %d, "descricaoProblema": "Notebook não liga"}
                """.formatted(cliente, equipamento);
    }

    private long contar(String tabela) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class);
    }

    private void verificarSemOrdem() {
        assertEquals(0L, contar("ordens_servico"));
        assertEquals(0L, contar("historico_status"));
        assertEquals(0L, contar("protocolos_ordem"));
    }
}
