-- RD03 / CDC art. 49: o arrependimento aprova a devolução mesmo com lacre violado. A constraint original exigia
-- produto_lacrado = TRUE para APROVADA/RECEBIDA e derrubava essa aprovação. Agora só exige que o lacre tenha sido avaliado.
ALTER TABLE devolucoes DROP CONSTRAINT IF EXISTS ck_devolucao_lacre;
ALTER TABLE devolucoes ADD CONSTRAINT ck_devolucao_lacre
    CHECK (status NOT IN ('APROVADA', 'RECEBIDA') OR produto_lacrado IS NOT NULL);

-- O arrependimento passa a ser declarado pela cliente (campo explícito), e não deduzido do texto do motivo.
ALTER TABLE devolucoes ADD COLUMN arrependimento BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE devolucoes SET arrependimento = TRUE
WHERE motivo ILIKE '%arrependimento%' OR motivo ILIKE '%art. 49%' OR motivo ILIKE '%artigo 49%';
