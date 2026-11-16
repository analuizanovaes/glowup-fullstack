# Configuração de ambiente, nuvem e chaves de API

Nenhuma chave real é commitada. O código lê tudo por `${VARIAVEL}` no `application.properties`; os valores reais ficam no arquivo `.env` local (ignorado pelo Git) e no ambiente de execução.

## 1. Ambiente local

1. Em `api/`, copie `.env.example` para `.env` e preencha as variáveis. Não versione o `.env`.
2. O Spring Boot não lê `.env` sozinho. Escolha uma forma:
   - **VS Code:** crie `.vscode/launch.json` (a pasta é ignorada pelo Git) com `"envFile": "${workspaceFolder}/api/.env"` na configuração Java.
   - **PowerShell (pasta `api`):**
     ```powershell
     Get-Content .env | Where-Object { $_ -match '^\s*[^#\s]' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }
     ./mvnw spring-boot:run
     ```
3. **Defina o perfil:** `SPRING_PROFILES_ACTIVE=local` no `.env`. Sem ele a API trata o ambiente como produção e se recusa a iniciar com o `JWT_SECRET` padrão ou com `CORS_ALLOWED_ORIGINS=*`. Para depurar, ligue `SWAGGER_ENABLED=true` e `JPA_SHOW_SQL=true` (ambos vêm desligados por padrão).
   Para o front em HTML/CSS/JavaScript puro, sirva os arquivos por HTTP (por exemplo, Live Server no VS Code ou `python -m http.server`); não abra o `index.html` diretamente como `file://`. No perfil local, `CORS_ALLOWED_ORIGINS=*` aceita origens `http://localhost` e `http://127.0.0.1` em qualquer porta. Em produção, informe somente a origem exata do site em `CORS_ALLOWED_ORIGINS`.
   Não aumente `RATELIMIT_AUTH_MAX` para "folgar" nos testes: o padrão (20 chamadas por janela de 15 min, por rota e IP) já comporta o uso normal e é o que protege o login contra força bruta. Além do limite por IP, o login tem bloqueio por conta: `LOGIN_MAX_FALHAS` senhas erradas (padrão 10) em `LOGIN_JANELA_MIN` minutos (padrão 15) bloqueiam aquele e-mail por `LOGIN_BLOQUEIO_MIN` minutos (padrão 15).
4. Banco: PostgreSQL local. Crie o banco `glowup` na sua máquina (`CREATE DATABASE glowup;`) e configure no `.env`:
   ```
   DB_URL=jdbc:postgresql://localhost:5432/glowup
   DB_USER=postgres
   DB_PASS=<senha do seu postgres>
   ```
   Se a senha tiver caracteres especiais, não coloque-a na URL: mantenha-a em `DB_PASS`. Não use `?sslmode=require` em Postgres local sem SSL. As migrations Flyway criam e atualizam as tabelas na primeira inicialização.

   Para apagar compras e dados gerados em testes, pare a API e rode `api/scripts/limpar-dados-de-teste.sql` (explicação no cabeçalho do arquivo). Para voltar a entrar na conta da administradora com a senha do `.env` (`Glow@12345`) quando a conta já existe com outra senha, rode `api/scripts/definir-senha-admin.sql`.

   Boa prática: em vez do superusuário `postgres`, a aplicação pode usar um usuário próprio, dono apenas do banco `glowup` (`CREATE ROLE glowup_app LOGIN PASSWORD '...'; CREATE DATABASE glowup OWNER glowup_app;`), assim uma falha na API não dá acesso ao servidor inteiro. Basta trocar `DB_USER`/`DB_PASS`.
5. Em produção, defina as mesmas três variáveis (`DB_URL`, `DB_USER`, `DB_PASS`) apontando para o PostgreSQL de produção, com SSL se o banco não estiver na mesma rede privada.

## 2. Gerar cada credencial

| Variável | Como obter |
|---|---|
| `JWT_SECRET` | `python -c "import secrets; print(secrets.token_hex(32))"` (um valor diferente para local e produção) |
| `RESEND_API_KEY` / `RESEND_FROM` | Veja a seção 4 (Resend) |
| `MP_TEST_ACCESS_TOKEN` | Painel de desenvolvedores do Mercado Pago → sua aplicação → credenciais de **teste** |
| `MP_EMAIL_PAGADOR_TESTE` | E-mail de uma conta de teste de comprador criada no mesmo painel |
| `MP_WEBHOOK_SECRET` | Mesmo painel → Webhooks → chave secreta da assinatura (opcional no local) |
| `CLOUDINARY_*` | Dashboard do Cloudinary → Settings → API Keys (cloud name, API key e API secret) |

## 3. Empacotamento

O `pom.xml` gera sempre `target/glowup-api.jar` (`<finalName>`). Para gerar o JAR sem rodar os testes (o `ApiApplicationTests` precisa de banco): `./mvnw -DskipTests clean package`.

- **Maven wrapper:** `./mvnw` (Linux/macOS) e `mvnw.cmd` (Windows) ficam em `api/`; rode-os dentro da pasta `api`.
- **Execução local:** `api/rodar-local.sh` (ou `rodar-local.cmd`) carrega o `api/.env` e sobe a API.
- Fora dos perfis `local`, `test` e `dev` (inclusive se `SPRING_PROFILES_ACTIVE` for esquecido), a API se recusa a iniciar se `JWT_SECRET` estiver ausente, curto ou igual ao padrão, ou se `CORS_ALLOWED_ORIGINS` estiver vazio ou com `*`.

## 4. E-mail em produção (Resend)

Com `RESEND_FROM` padrão (`onboarding@resend.dev`) o Resend só entrega ao e-mail dono da conta: serve para testar, não para clientes reais. A API registra um aviso no log ao subir em produção com esse remetente.

1. Crie a conta em resend.com e gere uma API key (**API Keys → Create**). Cadastre-a como `RESEND_API_KEY` (nunca no repositório).
2. **Domains → Add Domain** com um domínio que a equipe controla. O Resend mostra os registros DNS (SPF, DKIM e, opcionalmente, DMARC): crie-os no provedor do domínio e clique em *Verify*.
3. Em produção, defina `RESEND_FROM=GlowUp <contato@seudominio.com.br>` (o endereço precisa ser do domínio verificado) e `EMAIL_ENVIO_HABILITADO=true`. Com o perfil `prod`, o envio já fica habilitado por padrão; a aplicação recusa iniciar se faltar a chave ou se o remetente ainda for `onboarding@resend.dev`. Para desligar explicitamente, use `EMAIL_ENVIO_HABILITADO=false`.
4. Teste: peça a recuperação de senha (`POST /api/auth/esqueci-senha`) para um e-mail que não seja o seu e confira o recebimento.

O remetente de demonstração `onboarding@resend.dev` não serve para clientes. A chave e o domínio são próprios da conta GlowUp e precisam ser cadastrados por uma pessoa com acesso ao Resend e ao DNS do domínio. O exemplo `api/.env.example` usa valores marcadores, não credenciais reais.

## 5.3. Decisões funcionais registradas

- **Cupons:** a regra escolhida neste pacote é não limitar usos por cliente. Continua valendo `limiteUso`, que é o teto global do cupom, além de validade e valor mínimo. Como não há contagem individual, não é necessária tabela nova. Uma alteração futura para “uma vez por cliente” exigiria tabela de resgates ligada a cupom, cliente e pedido, com restrição única.
- **Direito de arrependimento:** pedidos cujo motivo mencione “arrependimento”, “art. 49” ou “artigo 49” não podem ser rejeitados nem ter o valor do estorno reduzido por lacre violado. O lacre controla apenas a reintegração automática ao estoque. Os outros motivos continuam usando a regra comercial anterior. O fluxo financeiro existente ainda registra o estorno na confirmação administrativa do recebimento; confirmem com advogado como operacionalizar o reembolso imediato em conjunto com a logística reversa antes de atender clientes.

## 5.2. Banco: pool, shutdown, RLS e webhook

- **Pool de conexões:** `DB_POOL_MAX` (padrão 10) e `DB_POOL_MIN_IDLE` (padrão 2). Em servidor compartilhado, ajuste ao limite de conexões do banco (durante um deploy, duas instâncias coexistem).
- **Encerramento gracioso:** `server.shutdown=graceful` (espera até `SHUTDOWN_TIMEOUT`, padrão 25s) deixa terminar requisições em andamento, como um webhook de pagamento, antes de desligar no deploy.
- **RLS (herança do Supabase):** a migration V16 liga o RLS em todas as tabelas, sem policies. Foi criada para o Supabase e permanece no histórico do Flyway (não a apague nem edite: isso quebraria a validação de checksum). Em PostgreSQL local não tem efeito prático, porque a API conecta como dono das tabelas.
- **Webhook do Mercado Pago:** a assinatura aceita `ts` de até 15 minutos (`MP_WEBHOOK_TOLERANCIA_SEGUNDOS`, padrão 900), para não recusar reenvios tardios. O webhook sempre reconsulta o gateway, então a janela maior não permite forjar pagamento.
- **Conferência do pagamento:** ao aprovar, a API compara o valor e a referência (`external_reference`) devolvidos pelo gateway com o pedido. Se divergirem, o pagamento fica pendente e a administradora recebe uma notificação `CONCILIACAO_PAGAMENTO`.
- **Migration V7:** não existe nesta distribuição (o Flyway aceita lacunas). Não crie uma V7 depois de a V8+ ter sido aplicada.

## 6. Sessão, senha inicial e atualização de dependências

- **Tempo da sessão:** `JWT_EXPIRACAO_MINUTOS` (padrão 30) e `REFRESH_TOKEN_DIAS` (padrão 7). O front renova a sessão automaticamente via `POST /api/auth/refresh`.
- **Primeiro acesso da Domingas:** o login retorna `trocaSenhaObrigatoria = true` e a API só libera a troca de senha até ela ser feita. Se a conta já existia, veja o `UPDATE` em `BACKEND_COMPLETO.md`.
- **Dependências:** o arquivo `.github/dependabot.yml` faz o GitHub abrir pull requests semanais com atualizações e correções de segurança do Maven. Ative o *Dependabot alerts* em Settings → Code security do repositório.
