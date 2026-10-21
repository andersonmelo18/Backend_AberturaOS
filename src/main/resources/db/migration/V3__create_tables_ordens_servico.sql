CREATE TABLE tecnicos (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    telefone VARCHAR(20),
    especialidade VARCHAR(100),
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL
);

CREATE TABLE pecas (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    preco NUMERIC(10, 2) NOT NULL,
    quantidade_estoque INTEGER NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE ordens_servico (
    id BIGSERIAL PRIMARY KEY,
    numero_protocolo VARCHAR(30) NOT NULL UNIQUE,
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    equipamento_id BIGINT NOT NULL REFERENCES equipamentos(id),
    tecnico_id BIGINT REFERENCES tecnicos(id),
    status VARCHAR(30) NOT NULL,
    descricao_problema TEXT NOT NULL,
    data_abertura TIMESTAMP NOT NULL,
    data_finalizacao TIMESTAMP,
    data_entrega TIMESTAMP
);

CREATE TABLE historico_status (
    id BIGSERIAL PRIMARY KEY,
    ordem_servico_id BIGINT NOT NULL REFERENCES ordens_servico(id),
    status_anterior VARCHAR(30),
    novo_status VARCHAR(30) NOT NULL,
    data_alteracao TIMESTAMP NOT NULL,
    responsavel VARCHAR(150) NOT NULL,
    observacao VARCHAR(500)
);

CREATE TABLE diagnosticos (
    id BIGSERIAL PRIMARY KEY,
    ordem_servico_id BIGINT NOT NULL UNIQUE REFERENCES ordens_servico(id),
    tecnico_id BIGINT NOT NULL REFERENCES tecnicos(id),
    descricao_tecnica TEXT NOT NULL,
    solucao_proposta TEXT NOT NULL,
    data_registro TIMESTAMP NOT NULL
);

CREATE TABLE orcamentos (
    id BIGSERIAL PRIMARY KEY,
    ordem_servico_id BIGINT NOT NULL UNIQUE REFERENCES ordens_servico(id),
    valor_mao_de_obra NUMERIC(10, 2) NOT NULL,
    valor_pecas NUMERIC(10, 2) NOT NULL,
    valor_total NUMERIC(10, 2) NOT NULL,
    data_criacao TIMESTAMP NOT NULL,
    data_expiracao TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    data_resposta TIMESTAMP,
    justificativa_cliente TEXT
);

CREATE TABLE itens_orcamento (
    id BIGSERIAL PRIMARY KEY,
    orcamento_id BIGINT NOT NULL REFERENCES orcamentos(id),
    peca_id BIGINT NOT NULL REFERENCES pecas(id),
    quantidade INTEGER NOT NULL,
    preco_unitario NUMERIC(10, 2) NOT NULL,
    valor_total_item NUMERIC(10, 2) NOT NULL
);

CREATE TABLE os_pecas (
    id BIGSERIAL PRIMARY KEY,
    ordem_servico_id BIGINT NOT NULL REFERENCES ordens_servico(id),
    peca_id BIGINT NOT NULL REFERENCES pecas(id),
    quantidade INTEGER NOT NULL,
    preco_unitario_historico NUMERIC(10, 2) NOT NULL,
    data_registro TIMESTAMP NOT NULL
);

CREATE TABLE anexos (
    id BIGSERIAL PRIMARY KEY,
    ordem_servico_id BIGINT NOT NULL REFERENCES ordens_servico(id),
    nome_original VARCHAR(255) NOT NULL,
    nome_arquivo_storage VARCHAR(255) NOT NULL UNIQUE,
    tipo_conteudo VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    caminho_storage VARCHAR(500) NOT NULL,
    data_upload TIMESTAMP NOT NULL
);
