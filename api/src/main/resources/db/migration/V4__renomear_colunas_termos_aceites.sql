-- GlowUp | V4: nomes de coluna do aceite dos Termos de Uso (RD04) conforme o combinado com a equipe.
-- Mantem a tabela separada termos_aceites (historico por versao); a V1 nao e alterada para preservar o checksum do Flyway.
-- Sem BEGIN/COMMIT: o Flyway ja executa cada migration em uma transacao.
ALTER TABLE termos_aceites RENAME COLUMN data_hora_aceite TO data_aceite_termos;
ALTER TABLE termos_aceites RENAME COLUMN versao_documento TO versao_termos;
