-- Conta criada com senha inicial (ex.: administradora): o back-end so libera o restante da API depois da troca.
ALTER TABLE usuarios ADD COLUMN senha_provisoria BOOLEAN NOT NULL DEFAULT FALSE;

-- Refresh tokens: guardamos apenas o hash SHA-256 (o token em si so existe no cliente).
CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMPTZ NOT NULL,
    revogado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens(usuario_id);
CREATE INDEX idx_refresh_tokens_expira ON refresh_tokens(expira_em);
