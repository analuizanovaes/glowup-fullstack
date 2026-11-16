CREATE TABLE configuracoes_sistema (
    chave VARCHAR(255) PRIMARY KEY,
    valor VARCHAR(255) NOT NULL
);

-- Insere o estado inicial para a aplicação não travar
INSERT INTO configuracoes_sistema (chave, valor) VALUES ('MODO_MANUTENCAO', 'false');
