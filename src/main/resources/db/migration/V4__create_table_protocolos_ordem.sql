CREATE TABLE protocolos_ordem (
    data DATE PRIMARY KEY,
    ultimo_numero INTEGER NOT NULL CHECK (ultimo_numero BETWEEN 0 AND 65535)
);
