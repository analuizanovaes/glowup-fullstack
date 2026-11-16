ALTER TABLE refresh_tokens ADD COLUMN rotacionado_em TIMESTAMP;
ALTER TABLE refresh_tokens ADD COLUMN token_substituto_hash VARCHAR(64);
