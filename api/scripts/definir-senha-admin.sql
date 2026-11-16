-- =====================================================================================================
-- GlowUp: define a senha da administradora como  Glow@12345  (a mesma senha inicial do arquivo .env).
--
-- QUANDO USAR: a conta da administradora só é criada com a senha do .env se ainda NÃO existir no banco.
-- Se ela já existe com outra senha, rode este script uma vez para voltar a entrar com Glow@12345.
--
-- COMO RODAR:
--   psql -U postgres -d glowup -f api/scripts/definir-senha-admin.sql
-- (ou cole no Query Tool do pgAdmin conectado ao banco "glowup").
--
-- Depois de entrar, quem quiser uma senha mais forte pode trocá-la em Meu Perfil.
-- =====================================================================================================

UPDATE usuarios
SET senha_hash = '$2b$12$iVWPehaKQkEpw9zJQsv44emgbBdE.p2pRSsG6wkRVknL/2vXb55iW',
    updated_at = CURRENT_TIMESTAMP
WHERE role = 'ADMIN';

SELECT email, role FROM usuarios WHERE role = 'ADMIN';
