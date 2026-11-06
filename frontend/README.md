# glowup-fullstack
Repositório oficial do GlowUp: um e-commerce full-stack para revenda de cosméticos. Desenvolvido com Java, Spring Boot e PostgreSQL, aplicando Clean Code, Git Flow rigoroso e contratos de API documentados via Swagger.

---

## Front-end (HTML, CSS e JavaScript nativos)

### Estrutura de pastas

```
assets/img/logo-flor.svg      logo (flor sem tronco)
css/
  base/         variáveis de cor e reset
  layouts/      estruturas de tela (painel admin, área da cliente, telas de acesso, resultado)
  components/   peças reutilizáveis (logo, avisos/toasts/janelas, selos de status, rodapé...)
  pages/        estilos exclusivos de cada tela
js/
  core/         config, utils, legal, ui (avisos), store (dados), layout (rotas, menu, busca)
  pages/        um arquivo por tela
```

Cada `.html` carrega: `css/base` → `css/layouts` → `css/components` → `css/pages/<tela>.css`, e depois `js/core/*` → `js/pages/<tela>.js`.

### Como funciona sem o back-end

Os dados ficam no `localStorage` do navegador (`js/core/store.js`), com as regras de negócio do documento
(maioridade, senha forte, e-mail único, trava de CEP, reserva de estoque, Pix/cartão até 3x, lucro real,
devolução de 7 dias com lacre, soft delete, CRM e alertas de validade). Quando a API existir, basta trocar
as funções de `store.js` por chamadas `fetch`: as telas só usam `GlowUp.store.*`.

Para voltar aos dados de exemplo, rode no console do navegador: `GlowUp.store.reset()`.

### Contas de demonstração (senha: `Glow@1234`)

| Perfil | E-mail |
|---|---|
| Administradora | domingas@glowup.com |
| Cliente | mariana@email.com |

Cartão de teste recusado: final `0002` (ex.: 4000 0000 0000 0002). Qualquer outro cartão válido (ex.: 4111 1111 1111 1111) é aprovado.
Cupons: `GLOW10` (10%) e `BEMVINDA` (R$ 15 acima de R$ 80).

Para revisar telas protegidas sem login, altere `guardsEnabled` para `false` em `js/core/config.js`.

---

## Kits (RF03 / DC02)

| | Kit pronto (`tipoKit: 'FABRICA'`) | Kit manual (`tipoKit: 'MANUAL'`) |
|---|---|---|
| Estoque | próprio (pronta-entrega) ou sem controle (sob encomenda) | **não tem**; depende dos itens |
| Composição | nenhuma | `itensKit: [{ produtoId, qtd }]` (só avulsos, pronta-entrega) |
| Venda baixa... | o próprio kit | cada item avulso (`qtd` do item × qtd de kits) |
| Disponível | `estoque - reservado` | o gargalo: `min(floor(livre do item / qtd do item))` |
| Custo de fábrica | informado | soma automática dos custos dos itens |

- **Tela:** `cadastrarprodutos.html` → Tipo de Venda "Kit / Presente" libera "Tipo de kit"; em "Kit manual" aparece o montador de composição.
- **Regra:** `js/core/store.js` (`isManualKit`, `stockTargets`, `available`, `saveProduct`). Reserva, baixa, estorno e devolução usam `stockTargets`, a mesma função para produto avulso, kit pronto e kit manual.
- **Integridade:** item que compõe kit manual não pode ser inativado nem virar kit/sob encomenda; o checkout confere a demanda somada (kit + o mesmo item avulso na sacola).
- Dados antigos do navegador são migrados sozinhos (`migrateKits`). Para ver os kits de exemplo novos, rode `GlowUp.store.reset()`.
