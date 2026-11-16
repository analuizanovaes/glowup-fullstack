# GlowUp API — fechamento do back-end (versão definitiva)

Este documento resume o que foi corrigido/adicionado para o back-end cobrir a documentação do Projeto Integrador
(RF01–RF08, RN01–RN08, RNF01–RNF04, RD01–RD04 e as estórias de sistema da seção 6).

## Migrations novas

> Exceção: a V16 foi ajustada nesta auditoria para não tentar aplicar RLS em `flyway_schema_history`, que
> travava a subida em banco novo. Bancos que já aplicaram a V16 antiga precisam de `flyway repair` antes do
> próximo deploy (veja `AUDITORIA_V13_CORRECOES_FINAIS.md`). As demais migrations não foram alteradas.
| Arquivo | O que faz |
|---|---|
| `V4__renomear_colunas_termos_aceites.sql` | `data_hora_aceite → data_aceite_termos`, `versao_documento → versao_termos` (RD04 / 6.3) |
| `V5__completar_backend_checkout_carrinho_crm.sql` | `pedidos.tipo_entrega`, `notificacoes_admin.titulo/mensagem`, `analises_reposicao.data_estimada_reposicao`, índices anti-duplicidade e `uf` como `VARCHAR(2)` |

| `V8__criar_tabela_configuracoes.sql` | `configuracoes_sistema` (chave/valor) com `MODO_MANUTENCAO = false` |
| `V9__registro_financeiro_taxa_estimada.sql` | RN05: `registros_financeiros.taxa_estimada` |
| `V10__pedido_conferencia_endereco.sql` | `pedidos.conferencia_endereco_pendente` (ViaCEP fora do ar no checkout) |
| `V11__senha_provisoria_e_refresh_tokens.sql` | `usuarios.senha_provisoria` e a tabela `refresh_tokens` (só o hash do token) |
| `V12__pedido_devolvido.sql` | Permite o status final `DEVOLVIDO` nos pedidos e no histórico |
| `V13__migrar_lacre_itens_devolucao.sql` | Mantém os lacres históricos dos itens alinhados com a decisão anterior da devolução |
| `V14__permitir_devolucoes_parciais_sucessivas.sql` | Permite novas devoluções depois que uma devolução parcial anterior foi recebida |
| `V15__paginacao_busca_sem_acentos.sql` | Habilita busca paginada de catálogo sem perder a comparação sem acentos |
| `V16__habilitar_rls_em_todas_as_tabelas.sql` | Liga o RLS (sem policies) em todas as tabelas do schema `public` (criada para o Supabase; em PostgreSQL local é inofensiva); a API segue funcionando (conecta como dono das tabelas). Ignora `flyway_schema_history` para não travar em banco novo. Tabelas novas precisam repetir o `ENABLE ROW LEVEL SECURITY` |
| `V17__remover_tabela_legada_reservas_estoque.sql` | Remove a tabela `reservas_estoque`, de um modelo de estoque abandonado |
| `V18__corrigir_lacre_e_arrependimento_devolucao.sql` | RD03/CDC art. 49: permite aprovar arrependimento com lacre violado (o lacre só precisa estar avaliado), adiciona `devolucoes.arrependimento` e diferencia lacre pendente (`NULL`) de violado (`FALSE`) |
| `V19__estado_lacre_e_zero_estorno_devolucao.sql` | Mantém o lacre coerente com o estado da devolução e aceita estorno de valor zero quando a devolução não tem valor a devolver |
| `V20__idempotencia_refresh_multinstancia.sql` | Rotação de refresh token idempotente entre instâncias: instante da rotação e hash do substituto na linha antiga |
| `V21__persistir_qr_code_pix.sql` | Persiste `pagamentos.pix_qr_code_base64` para que retries e consultas devolvam sempre o mesmo QR Code Pix |

Não houve V7 nesta distribuição. Flyway aceita lacunas; se a base de implantação tiver uma V7 aplicada por outra distribuição, confira o histórico e o checksum antes de atualizar.

## Fluxo de compra (do carrinho ao pagamento)
1. `POST /api/carrinho/itens` — adiciona ao carrinho **persistido no servidor** (sempre o da cliente do token).
2. `GET /api/carrinho` — subtotal recalculado em tempo real + alertas de estoque/preço (RF06, 6.29, 6.33).
3. `GET /api/logistica/cep/{cep}` — ViaCEP com contingência: se falhar (HTTP 503) ou vier sem rua, `permiteDigitacaoManual = true` (6.18).
4. `POST /api/pedidos/checkout` — **o servidor calcula tudo**: preço vigente, subtotal, cupom, frete, total e prazo unificado.
   O cliente envia apenas `tipoEntrega`, endereço (ou `enderecoId`) e `codigoCupom`. Valida maioridade (RN07), cascata logística
   país → UF → bairro (RNF01/RN03) e baixa o estoque de forma atômica (RN04). Sob Encomenda nunca é baixado (6.43).
5. `POST /api/pagamentos` — Pix ou cartão até 3x (já existente). Webhook aprova → baixa definitiva, e-mails, lucro real.
   Rejeição/cancelamento/expiração devolvem o estoque. Após a aprovação o carrinho é concluído e a administradora recebe a notificação de nova venda.

## Painel da administradora
`/api/admin/...`: pedidos e dashboard (já existiam) + **categorias**, **cupons**, **estoque** (entrada/ajuste com auditoria em
`movimentacoes_estoque`), **alertas de validade**, **sugestões de reposição (CRM)** e **notificações**.
Cadastro/edição de produto: `POST /api/produtos` e `PUT /api/produtos/{id}` (Pronta-Entrega exige quantidade; Sob Encomenda a ignora).
Busca da vitrine: `GET /api/produtos?categoria=&nome=&ordem=preco_asc|preco_desc&pagina=0&tamanho=50` (filtros sem diferenciar maiúsculas/acentos).

## Meu Perfil (RF01)
`/api/usuarios/me`: consultar/atualizar nome e e-mail (troca de e-mail devolve `novoToken`; pedidos continuam ligados ao ID),
telefones (um principal, ao menos um) e endereços (um principal, exclusão lógica). `DELETE /api/usuarios/me` = exclusão lógica da conta; exige a senha no corpo (`{"senha": "..."}`) e responde 409 se houver pedido em aberto (aguardando pagamento, pago, em separação ou enviado).

## Rotinas automáticas (CRON, fuso America/Sao_Paulo)
| Horário | Rotina |
|---|---|
| 00:00 | RN08: promoções expiradas voltam ao preço base |
| 08:00 | Alerta de validade (≤ 25 dias) só no painel admin, sem duplicar |
| 08:30 | CRM: data da compra + tempo médio de uso → sugestão de reposição, sem duplicar |

## Decisões de projeto a conhecer
* **Reserva de estoque (RN04):** a reserva é a própria baixa atômica no checkout; aprovação a torna definitiva e
  cancelamento/rejeição/expiração (job já existente) devolvem as unidades. A tabela `reservas_estoque` do DER foi removida na V17.
* **Carrinho (RN06):** só é concluído **após a aprovação** do pagamento; falha do Mercado Pago não esvazia a sacola.
* **Frete (RN03):** `GET /api/logistica/frete/{pais}?uf=&bairro=` (parâmetros renomeados para refletir o que o serviço lê).
* **Não implementado por não ter requisito/RF:** tabelas `caminhos` / `itens_caminho` e `configuracoes_financeiras`
  (a taxa continua vindo das propriedades `glowup.financeiro.*`, permitido pela RN05).

## Atualizações recentes de contrato (front)
| Endpoint | O que mudou |
|---|---|
| `GET /api/termos/versao-atual` | Novo, público. Devolve `{"versao": "1.0"}`; o front envia em `versaoTermos` (cadastro e checkout). |
| `PUT /api/usuarios/me/senha` | Novo. Body `{senhaAtual, novaSenha}`; 200 com `{token, refreshToken}` novos (as sessões anteriores deixam de valer), 400 se a senha atual estiver errada. |
| `POST /api/auth/login` | Resposta agora é `{token, refreshToken, trocaSenhaObrigatoria}`. O `token` dura 30 min (`JWT_EXPIRACAO_MINUTOS`). |
| `POST /api/auth/refresh` | Novo. Body `{refreshToken}`; devolve novo par `{token, refreshToken}`. Cada refreshToken vale uma vez (rotação); 401 se inválido/expirado. |
| `POST /api/auth/logout` | Novo. Body `{refreshToken}`; revoga a sessão (204). |
| `GET /api/usuarios/me` | Novo campo `trocaSenhaObrigatoria`. |
| `PUT /api/usuarios/me` | Para **alterar o e-mail**, o body precisa de `senhaAtual` (para só mudar o nome, é opcional). |
| `GET /api/pedidos/meus` e `/{id}` | Novo campo `linkWhatsappRetirada` (RN09): link `wa.me` com mensagem pronta nos pedidos de Retirada; vem `null` nos demais ou sem `WHATSAPP_NUMERO`. |
| `POST /api/usuarios` | `versaoTermos` agora é aceito (opcional, máx. 20). Se informado e diferente da vigente, responde 400; sem ele, grava a vigente. |
| `POST /api/pedidos/checkout` | Exige `aceitouTermos=true`, `versaoTermos` (igual à vigente, senão 400) e o cabeçalho `Idempotency-Key`. `tokenPagamento` não existe mais. No máximo 1 pedido aguardando pagamento por cliente (409). |
| `PATCH /api/pedidos/{id}/cancelamento` | A cliente pode cancelar o próprio pedido enquanto aguarda pagamento; estoque e cupom são liberados. |
| `POST /api/pedidos/{id}/devolucao` | `itensDevolvidos` obrigatório: `[{idItemPedido, quantidade}]`. Itens repetidos são somados. |
| `POST /api/kits` | Agora recebe DTO validado: `categoria` pelo **nome**, `tipo` FABRICA (`quantidadeEstoque`) ou MANUAL (`itens`). Responde 201. |
| `PUT /api/kits/{id}` | Novo (admin). Mesmo corpo do `POST`; tipo e modalidade não mudam. Em kit MANUAL, `itens` substitui os componentes. |
| `GET /api/admin/produtos` | Novo (admin). Lista produtos e kits, inclusive inativos (`ativo`, `tipoKit`), com custo e saldo. |
| `GET /api/produtos` e `GET /api/kits` | Respostas agora trazem `marca`, `categoria`, `precoBase`, `precoPromocional` (só durante a promoção), `imagemUrl`, `quantidadeDisponivel` (nulo = sem limite) e `prazoEntregaDias`; kits MANUAL trazem `componentes`. |
| `GET /api/produtos` e `GET /api/kits` | Filtro por `categoria` e ordenação por `ordem=preco_asc|preco_desc`; a comparação de categorias ignora maiúsculas e acentos. Paginação por `pagina` (começa em 0) e `tamanho` (padrão 50, máximo 100); o corpo continua sendo uma lista e o total vem nos cabeçalhos `X-Total-Count` e `X-Total-Pages`. Produtos e kits aplicam filtros e ordenação no banco. |
| `GET /api/admin/produtos` | Também paginado com `pagina` e `tamanho`; mantém os inativos e os mesmos cabeçalhos de total. |
| `POST /api/pedidos/{id}/devolucao` (novas devoluções) | Depois de uma devolução recebida o pedido aceita outra só para as unidades restantes. Quando as devoluções cobrem todos os itens, o pagamento vira ESTORNADO. A devolução parcial ajusta o lucro real do pedido. |
| `GET /api/pedidos/meus` e `GET /api/admin/pedidos` | Paginação **opcional**: `?pagina=0&tamanho=50` (máx. 100). O corpo continua sendo uma lista; o total vem em `X-Total-Count` e `X-Total-Pages`. Sem parâmetros, devolve a primeira página de 50. |
| `PATCH /api/admin/pedidos/{id}/endereco-conferido` | Novo. Libera pedidos com `conferirEndereco=true`. |
| `POST /api/pagamentos` e `GET /api/pagamentos/pedido/{id}` | `pixQrCodeBase64` agora fica persistido. Retries, consulta e sincronização devolvem o mesmo QR Code em vez de `null`. |
| `GET /api/pedidos/meus` e `GET /api/pedidos/{id}` | `limiteSolicitacaoDevolucao` passou a usar `glowup.devolucao.prazo-dias` (padrão 7), o mesmo prazo validado no servidor. |

## Devolução parcial (RN01 / RD03)
* A cliente informa os itens e as quantidades. Devolver **todos** os itens estorna o total do pedido (com frete) e repõe todo o estoque.
* Devolução **parcial** estorna `preço x quantidade` dos itens devolvidos, já abatendo a fração proporcional do cupom (frete não volta), envia o valor ao Mercado Pago e repõe ao estoque só os itens devolvidos. O pagamento continua APROVADO.
* O estorno e a reposição ocorrem no recebimento (admin), como antes. Há no máximo uma devolução ativa por pedido.

## ViaCEP fora do ar no checkout
O endereço digitado é aceito (a compra não é bloqueada), mas o pedido sai com `conferirEndereco=true` no painel. Enquanto estiver pendente, ele não vai para Em Separação nem Enviado: a administradora confere bairro e CEP e chama `PATCH /api/admin/pedidos/{id}/endereco-conferido`.

## Segurança operacional
* Perfil explícito: só `local`, `test` e `dev` toleram segredo padrão; qualquer outro exige `JWT_SECRET` forte, credenciais do Mercado Pago e `CORS_ALLOWED_ORIGINS`.
* Limite de requisições em `/api/auth/*` e na troca de senha: por padrão 20 por 15 minutos por IP (`RATELIMIT_AUTH_MAX`, `RATELIMIT_AUTH_JANELA_MIN`).
* Ao subir, a API roda uma vez as rotinas diárias (idempotentes), para compensar períodos em que a API ficou fora do ar.
* Tokens JWT carregam a "versão da senha": trocar ou redefinir a senha invalida todos os tokens anteriores.
* Cadastro (`POST /api/usuarios`) também entra no limite de requisições por IP.
* O e-mail simulado (corpo com o código de recuperação) só aparece no log nos perfis `local`, `test` e `dev`.

## Sessão (access token + refresh token) e senha provisória
* **Access token** (JWT) dura 30 min. **Refresh token** (aleatório, 256 bits, 7 dias) fica guardado só como hash SHA-256 e é trocado a cada renovação. Se um token já usado reaparecer (possível cópia), todas as sessões da conta são encerradas.
* **Fluxo do front:** guardar `token` e `refreshToken`; ao receber 401 por expiração, chamar `POST /api/auth/refresh` **uma vez** (serializar as chamadas), salvar o novo par e repetir a requisição; se o refresh falhar, ir para o login.
* Trocar ou redefinir a senha, ou excluir a conta, revoga todas as sessões.
* **Senha provisória:** a conta da administradora é criada por `ADMIN_EMAIL`/`ADMIN_SENHA_INICIAL` com `senha_provisoria = true`. Até ela chamar `PUT /api/usuarios/me/senha`, o servidor só aceita essa rota, `GET /api/usuarios/me` e `/api/auth/*`; o resto responde 403 com `codigo = TROCA_DE_SENHA_OBRIGATORIA`. O login traz `trocaSenhaObrigatoria = true` para o front abrir a tela de troca.
* **Sem exigir a troca (só local):** defina `ADMIN_SENHA_PROVISORIA=false` antes de a conta ser criada. Se ela já existe, rode `UPDATE usuarios SET senha_provisoria = FALSE WHERE email = 'EMAIL_DA_DOMINGAS';`.
* **Conta admin que já existe** (criada antes da V11): para forçar a troca, rode `UPDATE usuarios SET senha_provisoria = TRUE WHERE email = 'EMAIL_DA_DOMINGAS';`.
* Limites por IP (padrão por 15 min): login/recuperação 20, checkout/pagamento 30 e consulta de CEP 100.
