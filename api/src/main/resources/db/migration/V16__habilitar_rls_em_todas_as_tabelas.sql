-- Habilita Row Level Security (RLS) em todas as tabelas do schema public.
--
-- Por quê: no Supabase, tabelas do schema public podem ser expostas pela Data API (REST/PostgREST) a quem tiver a
-- anon key. Com RLS ligado e SEM nenhuma policy, esses papéis (anon/authenticated) não enxergam nem alteram nada.
--
-- A API do GlowUp conecta direto ao PostgreSQL com o papel dono das tabelas (postgres), que ignora RLS; por isso
-- nada muda para a aplicação. Não crie policies: todo acesso aos dados passa pela API.
--
-- Em um PostgreSQL local (sem Supabase) a migration também roda sem efeito prático para a aplicação.
-- ATENÇÃO: tabelas criadas em migrations futuras precisam repetir o ENABLE ROW LEVEL SECURITY.
DO $$
DECLARE
    tabela record;
BEGIN
    FOR tabela IN
        SELECT tablename FROM pg_tables
        WHERE schemaname = 'public'
          AND tableowner = current_user
          -- A tabela interna do Flyway não deve ser alterada: o Flyway mantém um lock de leitura
          -- nela enquanto migra e o ALTER TABLE (que pede lock exclusivo) causaria deadlock.
          AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tabela.tablename);
    END LOOP;
END
$$;

-- Defesa em profundidade: se os papéis do Supabase existirem, tira também os privilégios diretos nas tabelas.
DO $$
DECLARE
    papel text;
BEGIN
    FOREACH papel IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = papel) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', papel);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', papel);
        END IF;
    END LOOP;
END
$$;
