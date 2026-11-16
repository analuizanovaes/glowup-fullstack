// Camada de dados do GlowUp: catálogo, carrinho, checkout, pagamentos, pós-venda, perfil e admin.
// Toda regra de preço, estoque, cupom, frete e pagamento é calculada pelo backend (src/main/java).
(function () {
  'use strict';
  const A = window.GlowUp.api, U = window.GlowUp.utils, G = window.GlowUp.config, C = window.GlowUp.config;

  let cartId = null, cartCache = null, products = [], kits = [], categories = [], users = {}, ordersCache = null, returnsCache = [];
  let currentUser = null, orderHistory = [], savedCouponValue = null, termsVersion = null;
  let alertsCache = [], maintenanceCache = null;
  // Catálogo carregado em modo 'admin' (inclui inativos e custos) ou 'public' (vitrine).
  let catalogMode = null;
  // Pedidos da administradora: lista paginada baixada uma vez e reaproveitada por Visão geral, Pedidos, Devoluções e CRM.
  const ADMIN_ORDERS_TTL_MS = 15000;
  let adminOrdersCache = null, adminOrdersAt = 0, adminOrdersInflight = null;
  let checkoutIdempotencyKey = null;

  const money = (v) => (v === null || v === undefined || v === '' ? null : U.round2(Number(v) || 0));
  const num = (v) => (v === null || v === undefined || v === '' ? null : Number(v));
  const todayISO = () => new Date().toISOString().slice(0, 10);
  const addDaysISO = (iso, days) => { const d = new Date(iso + 'T12:00:00'); d.setDate(d.getDate() + days); return d.toISOString().slice(0, 10); };

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const newIdempotencyKey = () => (window.crypto?.randomUUID ? crypto.randomUUID() : U.randomToken(24));
  const checkoutKey = () => { if (!checkoutIdempotencyKey) checkoutIdempotencyKey = newIdempotencyKey(); return checkoutIdempotencyKey; };
  const resetCheckoutKey = () => { checkoutIdempotencyKey = null; };
  const isColdStartError = (e) => {
    const s = e && e.status;
    return !e || s == null || s === 408 || s === 425 || s === 502 || s === 503 || s === 504 || e.name === 'AbortError' || e.name === 'TypeError';
  };

  async function postWithWakeRetry(path, body, options, hooks) {
    const maxAttempts = 4;
    const delays = [0, 12000, 15000, 15000];
    let lastError = null;
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      if (attempt > 1) {
        hooks?.onWaiting?.(attempt);
        await sleep(delays[attempt - 1] || 15000);
      }
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 30000);
      try {
        const data = await A.post(path, body, Object.assign({}, options, { signal: controller.signal }));
        clearTimeout(timeout);
        return { ok: true, data };
      } catch (e) {
        clearTimeout(timeout);
        lastError = e;
        if (attempt === maxAttempts || !isColdStartError(e)) return { ok: false, error: e };
      }
    }
    return { ok: false, error: lastError };
  }

  function mapCategory(c) {
    if (typeof c === 'string') return { id: null, nome: c, codigo: null, produtos: [] };
    return { id: c.id, nome: c.nome || c.descricao || '', codigo: c.codigo || null, produtos: [] };
  }

  function mapEndereco(e) {
    if (!e) return null;
    return {
      id: e.id ?? e.enderecoId ?? null, apelido: null, cep: e.cep || '', rua: e.logradouro || e.rua || '', numero: e.numero || '',
      complemento: e.complemento || '', bairro: e.bairro || '', cidade: e.cidade || '', uf: e.uf || '', pais: e.pais || 'Brasil',
      principal: !!e.principal, tipoEntrega: e.tipoEntrega || null, prazoDias: num(e.prazoDias)
    };
  }

  function normalizeProduct(p, admin = false) {
    const isKit = p.tipoKit != null || p.tipoVenda === 'KIT' || p.tipoVenda === 'kit' || p.tipo === 'KIT' || p.tipo === 'FABRICA' || p.tipo === 'MANUAL' || Array.isArray(p.componentes);
    const modalidade = p.modalidade || null;
    const estoqueTotal = admin ? num(p.quantidade) : num(p.quantidadeDisponivel ?? p.estoque ?? p.quantidadeEstoque);
    const reservado = admin ? (num(p.quantidadeReservada) || 0) : 0;
    const precoBase = money(p.precoBase ?? p.preco);
    const precoPromocional = money(p.precoPromocional);
    const fimPromocao = p.dataLimitePromocao ? String(p.dataLimitePromocao).slice(0, 10) : null;
    const emPromocao = precoPromocional != null && (!fimPromocao || todayISO() <= fimPromocao);
    const precoVigente = money(p.precoVigente ?? (emPromocao ? precoPromocional : precoBase));
    const itensKit = (p.componentes || p.itensKit || []).map((c) => ({
      produtoId: c.produtoId ?? c.id, qtd: Math.max(1, num(c.quantidade ?? c.qtd) || 1), nome: c.nome || '', imagem: c.imagemUrl || c.imagem || null
    })).filter((c) => c.produtoId != null);
    return {
      id: p.id, nome: p.nome || '', descricao: p.descricao || '', marca: p.marca || '', categoria: typeof p.categoria === 'string' ? p.categoria : (p.categoria?.nome || p.categoria?.descricao || null),
      tipoVenda: isKit ? 'kit' : 'varejo', tipoKit: isKit ? (p.tipoKit || p.tipo || 'FABRICA') : null,
      modalidade, estoque: estoqueTotal, estoqueReservado: reservado, quantidadeEstoque: estoqueTotal,
      custoFabrica: money(p.custoFabrica ?? p.custo), precoBase, precoPromocional, precoVigente, dataInicioPromocao: p.dataInicioOferta ? String(p.dataInicioOferta).slice(0, 10) : null,
      dataFimPromocao: fimPromocao, imagem: p.imagemUrl || p.imagem || U.placeholderImage, itensKit,
      dataValidade: p.dataValidade || null, tempoMedioUsoDias: num(p.tempoMedioUsoDias ?? p.tempoMedioUso) || 30,
      prazoEntregaDias: num(p.prazoEntregaDias) || C.deliveryDays, ativo: p.ativo !== false, criadoEm: p.criadoEm || null
    };
  }

  // Cache curto do catálogo na aba (evita refazer ~100 produtos a cada troca de tela).
  const CATALOG_KEY = C.storagePrefix + 'catalog';
  const CATALOG_TTL_MS = 3 * 60 * 1000;
  function readCatalogCache() {
    try {
      const raw = JSON.parse(sessionStorage.getItem(CATALOG_KEY) || 'null');
      return raw && Date.now() - raw.t < CATALOG_TTL_MS ? raw.d : null;
    } catch (e) { return null; }
  }
  function writeCatalogCache(d) { try { sessionStorage.setItem(CATALOG_KEY, JSON.stringify({ t: Date.now(), d })); } catch (e) { /* ignore */ } }
  function clearCatalogCache() { try { sessionStorage.removeItem(CATALOG_KEY); sessionStorage.removeItem(C.storagePrefix + 'cartCount'); } catch (e) { /* ignore */ } }

  // O back pagina as listas (padrão 50, máx. 100): busca todas as páginas.
  async function fetchAllPages(path) {
    const size = 100;
    let all = [];
    for (let page = 0; page < 50; page++) {
      const rows = await A.get(`${path}${path.includes('?') ? '&' : '?'}pagina=${page}&tamanho=${size}`);
      const list = Array.isArray(rows) ? rows : (rows?.content || []);
      all = all.concat(list);
      if (list.length < size) break;
    }
    return all;
  }

  async function loadCatalog(admin = false, force = false) {
    const mode = admin ? 'admin' : 'public';
    if (!force && catalogMode === mode && products.length && categories.length) return products;
    try {
      if (admin) {
        // Erros de API sobem para a tela (que mostra a mensagem e o botão "Tentar novamente"): nada de engolir e mostrar lista vazia.
        const [rows, kitsResp, cats] = await Promise.all([
          fetchAllPages('/api/admin/produtos'),
          fetchAllPages('/api/kits').catch(() => []),
          A.get('/api/admin/categorias').catch(() => A.get('/api/categorias'))
        ]);
        const kitById = new Map((kitsResp || []).map((k) => [k.id, k]));
        products = rows.map((p) => {
          const norm = normalizeProduct(p, true);
          const kit = kitById.get(p.id);
          if (kit) {
            norm.itensKit = (kit.componentes || []).map((c) => ({ produtoId: c.produtoId, qtd: c.quantidade || 1, nome: c.nome, imagem: c.imagemUrl }));
            if (!norm.imagem) norm.imagem = kit.imagemUrl || norm.imagem;
            if (!norm.descricao && kit.descricao) norm.descricao = kit.descricao;
          }
          return norm;
        });
        kits = products.filter((p) => p.tipoVenda === 'kit');
        categories = (cats || []).map(mapCategory);
      } else {
        let cached = force ? null : readCatalogCache();
        if (!cached) {
          const [pr, kr, ct] = await Promise.all([fetchAllPages('/api/produtos'), fetchAllPages('/api/kits').catch(() => []), A.get('/api/categorias')]);
          cached = { produtos: pr, kitsResp: kr, cats: ct };
          writeCatalogCache(cached);
        }
        const { produtos, kitsResp, cats } = cached;
        products = (produtos || []).map((p) => normalizeProduct(p));
        kits = (kitsResp || []).map((k) => normalizeProduct(k));
        categories = (cats || []).map(mapCategory);
      }
      categories.forEach((c) => { c.produtos = []; });
      products.forEach((p) => { const c = categories.find((x) => x.nome === p.categoria); if (c) c.produtos.push(p.id); });
      kits.forEach((k) => { const c = categories.find((x) => x.nome === k.categoria); if (c && !c.produtos.includes(k.id)) c.produtos.push(k.id); });
      catalogMode = mode;
    } catch (e) {
      products = []; kits = []; catalogMode = null;
      throw e;
    }
    return products;
  }

  const ensureCatalog = () => { const admin = !!isAdmin(); return (products.length && catalogMode === (admin ? 'admin' : 'public')) ? Promise.resolve(products) : loadCatalog(admin); };
  const listProducts = () => ensureCatalog().then(() => products);
  const reloadCatalog = () => loadCatalog(!!isAdmin(), true);
  const getProduct = (id) => products.find((p) => p.id === Number(id)) || kits.find((k) => k.id === Number(id)) || null;
  const kitsUsing = (productId) => kits.filter((k) => k.ativo !== false && k.tipoKit === 'MANUAL' && (k.itensKit || []).some((i) => i.produtoId === Number(productId)));
  const listKits = () => ensureCatalog().then(() => kits);

  let cartInflight = null;
  function applyCart(c) {
    cartId = c.carrinhoId;
    cartCache = {
      id: c.carrinhoId,
      itens: (c.itens || []).map((i) => ({
        id: null, produtoId: i.produtoId, nome: i.nome, imagem: i.imagemUrl || getProduct(i.produtoId)?.imagem || null,
        quantidade: i.quantidade, precoUnitario: money(i.precoUnitario), subtotal: money(i.subtotal),
        estoqueDisponivel: num(i.estoqueDisponivel), disponivel: i.disponivel !== false
      })),
      subtotal: money(c.subtotal), total: money(c.subtotal), alertas: c.alertas || [], podeFinalizar: c.podeFinalizar !== false
    };
    return cartCache;
  }

  function cart(force = false) {
    if (cartInflight && !force) return cartInflight;
    const req = A.get('/api/carrinho').then(applyCart).finally(() => { if (cartInflight === req) cartInflight = null; });
    cartInflight = req;
    return req;
  }

  function cartAdd(productId, qty = 1) {
    const p = getProduct(productId);
    const max = p ? available(p) : null;
    if (max != null && qty > max) return Promise.resolve({ ok: false, status: 409, message: `Estoque insuficiente: só ${max} un.` });
    return A.post('/api/carrinho/itens', { produtoId: Number(productId), quantidade: qty })
      .then(applyCart).then(() => { resetCheckoutKey(); return { ok: true, product: p || { id: Number(productId), nome: 'Produto' } }; }).catch((e) => ({ ok: false, status: e.status || 500, message: e.message }));
  }

  function cartSetQty(productId, qty) {
    if (qty <= 0) return cartRemove(productId);
    return A.put(`/api/carrinho/itens/${Number(productId)}`, { quantidade: qty })
      .then(applyCart).then(() => { resetCheckoutKey(); return { ok: true }; }).catch((e) => ({ ok: false, message: e.message }));
  }

  function cartRemove(productId) {
    return A.delete(`/api/carrinho/itens/${Number(productId)}`).then(applyCart).then(() => { resetCheckoutKey(); return { ok: true }; }).catch((e) => ({ ok: false, message: e.message }));
  }

  function cartClear() { return A.delete('/api/carrinho').then(() => { cartId = null; cartCache = null; resetCheckoutKey(); return { ok: true }; }).catch((e) => ({ ok: false, message: e.message })); }
  const cartCount = () => (cartCache ? Promise.resolve(cartCache) : cart()).then((c) => c.itens.reduce((s, i) => s + i.quantidade, 0)).catch(() => 0);
  const cartTotal = () => cart().then((c) => c.subtotal).catch(() => 0);
  const cartCanCheckout = () => cart().then((c) => c.podeFinalizar && c.itens.length > 0).catch(() => false);

  function previewTotals(way, orderData, endereco) {
    const tipoEntrega = way === 'home' ? 'DOMICILIO' : 'RETIRADA';
    const body = {
      tipoEntrega,
      enderecoId: endereco?.id ?? null,
      cep: tipoEntrega === 'DOMICILIO' ? U.digits(endereco?.cep) : null,
      logradouro: tipoEntrega === 'DOMICILIO' ? (endereco?.rua || endereco?.logradouro || null) : null,
      numero: tipoEntrega === 'DOMICILIO' ? (endereco?.numero || null) : null,
      complemento: tipoEntrega === 'DOMICILIO' ? (endereco?.complemento || null) : null,
      bairro: tipoEntrega === 'DOMICILIO' ? (endereco?.bairro || null) : null,
      cidade: tipoEntrega === 'DOMICILIO' ? (endereco?.cidade || null) : null,
      uf: tipoEntrega === 'DOMICILIO' ? (endereco?.uf || null) : null,
      pais: 'Brasil',
      codigoCupom: savedCouponValue || null
    };
    return A.post('/api/pedidos/simulacao', body).then((t) => {
      const total = money(t.valorTotal ?? t.total);
      const base = { subtotal: money(t.subtotal), desconto: money(t.descontoCupom ?? t.desconto), frete: money(t.valorFrete ?? t.frete), total, podeFinalizar: true, cupom: savedCouponValue };
      base.parcelas = buildParcelsPreview(total);
      return base;
    });
  }

  function buildParcelsPreview(total) {
    const max = C.maxInstallments || 3;
    const interest = C.installmentInterest || {};
    const out = [];
    for (let n = 1; n <= max; n++) {
      const bruto = U.round2(total * (1 + (interest[n] || 0)));
      out.push({ n, valor: Math.round(bruto / n), total: bruto });
    }
    return out;
  }

  async function createOrder(payload, hooks) {
    const user = currentUser;
    if (!user) return { ok: false, message: 'Faça login para finalizar a compra.' };
    if (!termsVersion) {
      try { const t = await A.get('/api/termos/versao-atual'); termsVersion = typeof t === 'string' ? t : (t.versao || t.versaoAtual || C.termsVersion); } catch (e) { termsVersion = C.termsVersion; }
    }
    const entrega = payload.entrega || {};
    const endereco = entrega.tipo === 'home' ? (entrega.endereco || {}) : {};
    const body = {
      aceitouTermos: true,
      versaoTermos: termsVersion || C.termsVersion,
      tipoEntrega: entrega.tipo === 'home' ? 'DOMICILIO' : 'RETIRADA',
      enderecoId: null,
      pais: 'Brasil',
      cep: entrega.tipo === 'home' ? U.digits(endereco.cep) : null,
      logradouro: entrega.tipo === 'home' ? (endereco.rua || null) : null,
      numero: entrega.tipo === 'home' ? (endereco.numero || null) : null,
      complemento: entrega.tipo === 'home' ? (endereco.complemento || null) : null,
      bairro: entrega.tipo === 'home' ? (endereco.bairro || null) : null,
      cidade: entrega.tipo === 'home' ? (endereco.cidade || null) : null,
      uf: entrega.tipo === 'home' ? (endereco.uf || null) : null,
      codigoCupom: savedCouponValue || null
    };
    const result = await postWithWakeRetry('/api/pedidos/checkout', body, { headers: { 'Idempotency-Key': checkoutKey() } }, hooks);
    if (!result.ok) {
      const e = result.error || {};
      return { ok: false, status: e.status || 503, message: isColdStartError(e) ? 'Nosso servidor ainda está acordando. Aguarde alguns segundos e toque em confirmar novamente.' : (e.message || 'Não foi possível confirmar o pedido.') };
    }
    const res = result.data;
    const order = mapOrder(res);
    order.userId = user.id;
    order.clienteNome = user.nome;
    order.clienteEmail = user.email;
    order.pagamento = { metodo: payload.pagamento?.metodo || payload.metodo || null, pago: false, status: order.status, pixCode: null, txid: null, qrCode: null, cardBrand: null, installments: payload.pagamento?.parcelas || payload.parcelas || 1, paidAt: null, cardLast4: null };
    A.delete('/api/carrinho').catch(() => {});
    clearCatalogCache();
    cartId = null; cartCache = null; ordersCache = null; returnsCache = [];
    resetCheckoutKey();
    return { ok: true, order };
  }

  function applyCoupon(code) {
    savedCouponValue = code || null;
    return previewTotals('pickup', {}, null).then((t) => {
      if (cartCache) cartCache.desconto = t.desconto;
      resetCheckoutKey();
      return { ok: true, cupom: { codigo: code }, desconto: t.desconto, subtotal: t.subtotal, frete: t.frete, total: t.total };
    }).catch((e) => ({ ok: false, message: e.message }));
  }
  const saveCoupon = (code) => { savedCouponValue = code || null; resetCheckoutKey(); return Promise.resolve({ ok: true }); };
  const savedCoupon = () => savedCouponValue;
  const clearCoupon = () => { savedCouponValue = null; if (cartCache) cartCache.desconto = 0; resetCheckoutKey(); return Promise.resolve({ ok: true }); };

  function mapOrder(o, admin = false) {
    // Na visão da administradora o pedido é de OUTRA pessoa: nunca preencher lacunas com os dados de quem está logada.
    const me = admin ? null : currentUser;
    const pedidoId = o.id ?? o.pedidoId;
    const endereco = mapEndereco(o.enderecoEntrega);
    const tipoEntrega = o.tipoEntrega || endereco?.tipoEntrega || (num(o.valorFrete) > 0 ? 'DOMICILIO' : 'RETIRADA');
    const criadoEm = o.dataPedido || o.dataCriacao || o.criadoEm || new Date().toISOString();
    const pg = o.pagamento && typeof o.pagamento === 'object' ? o.pagamento : null;
    const metodoPg = pg?.metodo || o.metodoPagamento || o.formaPagamento || null;
    const statusPg = pg?.status || o.pagamentoStatus || 'PENDENTE';
    const itens = (o.itens || []).map((i) => ({
      id: i.idItemPedido ?? i.id ?? null,
      produtoId: i.produtoId,
      idPedido: pedidoId,
      nome: i.produtoNome || i.nome,
      qtd: num(i.quantidade) || 1,
      precoUnitario: money(i.precoUnitario),
      precoCheio: null,
      subtotal: money(i.subtotal ?? (num(i.precoUnitario) * num(i.quantidade))),
      precoPraticado: money(i.precoUnitario), custoFabrica: money(i.custoFabrica ?? 0),
      imagem: getProduct(i.produtoId)?.imagem || null,
      categoria: null
    }));
    const lucro = o.lucroReal || null;
    return {
      id: pedidoId,
      userId: o.clienteId ?? o.userId ?? me?.id ?? null,
      clienteNome: o.clienteNome || me?.nome || '',
      clienteEmail: o.clienteEmail || me?.email || '',
      clienteCpf: o.clienteCpf || me?.cpf || '',
      clienteTelefone: o.telefoneCliente || o.telefone || me?.telefone || '',
      status: o.status,
      pagamentoStatus: statusPg,
      formaPagamento: metodoPg,
      pagamento: { metodo: metodoPg, pago: statusPg === 'APROVADO', status: statusPg, parcelas: num(pg?.parcelas) || 1, valor: money(pg?.valor), pixCode: null, txid: null, qrCode: null, cardBrand: null, installments: num(pg?.parcelas) || null, paidAt: null, cardLast4: null },
      criadoEm,
      prazoDias: num(o.prazoDias) || C.deliveryDays,
      entrega: { tipo: tipoEntrega === 'DOMICILIO' ? 'home' : 'pickup', endereco },
      itens,
      subtotal: money(o.subtotal),
      desconto: money(o.descontoCupom ?? o.desconto),
      frete: money(o.valorFrete ?? o.frete),
      total: money(o.valorTotal ?? o.total),
      cupomCodigo: o.codigoCupom || o.cupom || null,
      cupom: o.codigoCupom || o.cupom || null,
      juros: money(o.juros ?? o.valorJuros ?? 0),
      devolucaoStatus: o.devolucaoStatus || null,
      enderecoEntrega: o.enderecoEntrega || null,
      linkWhatsapp: o.linkWhatsappRetirada || null,
      conferenciaEnderecoPendente: !!(o.conferenciaEnderecoPendente ?? o.conferirEndereco),
      bairroEntrega: o.bairroEntrega || endereco?.bairro || '',
      financeiro: lucro ? {
        valorVenda: money(lucro.valorVenda ?? o.valorTotal),
        // null = o back não tem dados para calcular (ex.: "Dados Insuficientes"); a tela mostra isso em vez de R$ 0,00
        custoFabrica: money(lucro.custoFabrica),
        taxaGateway: money(lucro.taxasFinanceiras),
        lucroReal: money(lucro.lucroReal),
        observacao: lucro.observacao || null,
        taxaEstimada: !!lucro.taxaEstimada
      } : null,
      limiteSolicitacaoDevolucao: (o.limiteSolicitacaoDevolucao || (o.dataEntrega ? new Date(o.dataEntrega).toISOString() : null) || addDaysISO(criadoEm.slice(0, 10), C.returnWindowDays))
    };
  }

  // Lista de pedidos da administradora (todas as páginas). Reaproveitada por 15 s para as telas não refazerem a mesma consulta.
  function adminOrders(force = false) {
    if (!force && adminOrdersCache && Date.now() - adminOrdersAt < ADMIN_ORDERS_TTL_MS) return Promise.resolve(adminOrdersCache);
    if (adminOrdersInflight && !force) return adminOrdersInflight;
    const req = fetchAllPages('/api/admin/pedidos').then((rows) => {
      adminOrdersCache = rows.map((o) => mapOrder(o, true));
      adminOrdersAt = Date.now();
      ordersCache = adminOrdersCache;
      return adminOrdersCache;
    }).finally(() => { if (adminOrdersInflight === req) adminOrdersInflight = null; });
    adminOrdersInflight = req;
    return req;
  }
  const invalidateAdminOrders = () => { adminOrdersCache = null; adminOrdersAt = 0; };

  async function listOrders(admin = false, force = false) {
    if (admin) return adminOrders(force);
    const retsPromise = A.get('/api/devolucoes/minhas').catch(() => null);
    const raw = await A.get('/api/pedidos/meus');
    const list = Array.isArray(raw) ? raw : (raw?.content || raw?.pedidos || []);
    const mapped = list.map((o) => mapOrder(o));
    ordersCache = mapped;
    const rets = await retsPromise;
    returnsCache = rets || [];
    const byPedido = new Map(returnsCache.map((r) => [r.pedidoId, r.status]));
    mapped.forEach((o) => { o.devolucaoStatus = byPedido.get(o.id) || null; });
    return mapped;
  }

  const listOrdersAdmin = (force = false) => listOrders(true, force);
  function getOrder(id, fresh = false) {
    const n = Number(id);
    if (!Number.isInteger(n) || n <= 0) return Promise.resolve(null);
    if (isAdmin()) {
      const cached = !fresh && adminOrdersCache && Date.now() - adminOrdersAt < ADMIN_ORDERS_TTL_MS ? adminOrdersCache.find((o) => o.id === n) : null;
      if (cached) return Promise.resolve(cached);
      return A.get(`/api/admin/pedidos/${n}`).then((o) => mapOrder(o, true)).catch((e) => (e.status === 404 ? null : Promise.reject(e)));
    }
    return A.get(`/api/pedidos/${n}`).then((o) => mapOrder(o)).catch((e) => (e.status === 404 ? null : Promise.reject(e)));
  }

  function mapPayment(p) {
    return {
      id: p.id, pedidoId: p.pedidoId, gatewayId: p.idGateway || null, metodo: p.metodo,
      pagamentoStatus: p.pagamentoStatus || p.status, pedidoStatus: p.pedidoStatus,
      valor: money(p.valor ?? p.valorTotal), parcelas: num(p.parcelas) || 1,
      qrcode: p.pixQrCodeBase64 || null, pixCopiaECola: p.pixCopiaECola || null, ticketUrl: p.pixLinkTicket || null,
      pixExpiraEm: p.pixExpiraEm || null, pagoEm: p.pagoEm || null,
      status: p.pagamentoStatus || p.status, statusPedido: p.pedidoStatus
    };
  }

  function getPayment(orderId) {
    return A.get(`/api/pagamentos/pedido/${Number(orderId)}`).then(mapPayment).catch((e) => (e.status === 404 ? null : Promise.reject(e)));
  }

  function syncPayment(orderId) {
    return A.post(`/api/pagamentos/pedido/${Number(orderId)}/sincronizar`).then(mapPayment);
  }

  function tokenizeCard(data) {
    return new Promise((resolve, reject) => {
      if (!window.MercadoPago || !G.mercadoPagoPublicKey) return reject(new Error('Mercado Pago não configurado: chave pública ausente.'));
      try {
        const mp = new window.MercadoPago(G.mercadoPagoPublicKey, { locale: 'pt-BR' });
        const [mm, yy] = String(data.expiry || '').split('/');
        const yearDigits = String(yy || '').replace(/\D/g, '');
        const cardExpirationYear = yearDigits.length === 2 ? '20' + yearDigits : yearDigits;
        mp.createCardToken({
          cardNumber: U.digits(data.number),
          cardholderName: data.name,
          cardExpirationMonth: String(mm || '').padStart(2, '0'),
          cardExpirationYear,
          securityCode: String(data.cvv || ''),
          identificationType: 'CPF',
          identificationNumber: U.digits(data.cpf)
        }).then((token) => resolve({ ok: true, card: token })).catch((e) => resolve({ ok: false, message: e.message || 'Não foi possível validar o cartão.' }));
      } catch (e) { resolve({ ok: false, message: e.message }); }
    });
  }

  function startPayment(payload, metodoArg, parcelasArg, tokenArg) {
    if (typeof payload === 'number') {
      payload = {
        orderId: payload, metodo: metodoArg, parcelas: parcelasArg,
        tokenCartao: tokenArg?.id || null, bandeiraCartao: tokenArg?.payment_method_id || null,
        documento: tokenArg?.identification_number || null
      };
    }
    const user = currentUser;
    const metodo = payload.metodo === 'PIX' ? 'PIX' : 'CARTAO';
    const body = {
      pedidoId: Number(payload.orderId),
      metodo,
      parcelas: metodo === 'CARTAO' ? (num(payload.parcelas) || 1) : 1,
      tokenCartao: payload.tokenCartao || null,
      bandeiraCartao: payload.bandeiraCartao || null,
      documentoPagador: U.digits(user?.cpf || payload.documento || '') || null
    };
    return postWithWakeRetry('/api/pagamentos', body, {}, null).then((r) => {
      if (r.ok) return { ok: true, ...mapPayment(r.data) };
      const e = r.error || {};
      return { ok: false, status: e.status || 503, message: isColdStartError(e) ? 'O pagamento ainda está inicializando. Tente novamente em instantes.' : (e.message || 'Não foi possível iniciar o pagamento.') };
    });
  }

  function mapReturn(r) {
    return {
      id: r.id, pedidoId: r.pedidoId, status: r.status, motivo: r.motivo, arrependimento: !!r.arrependimento,
      dataSolicitacao: r.dataSolicitacao, dataAnalise: r.dataAnalise || null, produtoLacrado: r.produtoLacrado ?? null,
      observacaoAnalise: r.observacaoAnalise || null, dataRecebimento: r.dataRecebimento || null, valorEstornado: money(r.valorEstornado), itens: r.itens || []
    };
  }

  async function requestReturn(orderId, data) {
    try {
      const order = await getOrder(orderId);
      if (!order) return { ok: false, message: 'Pedido não encontrado.' };
      const item = order.itens.find((i) => i.produtoId === Number(data.produtoId)) || order.itens[0];
      if (!item || item.id == null) return { ok: false, message: 'Item do pedido não identificado para devolução.' };
      const res = await A.post(`/api/devolucoes/pedidos/${Number(orderId)}`, {
        motivo: data.motivo,
        itensDevolvidos: [{ idItemPedido: item.id, quantidade: data.quantidade || item.qtd }],
        arrependimento: false
      });
      ordersCache = null; returnsCache = [];
      return { ok: true, return: mapReturn(res) };
    } catch (e) {
      return { ok: false, status: e.status || 500, message: e.message };
    }
  }

  function returnState(order) {
    if (!order || order.status !== 'ENTREGUE') return 'none';
    const ret = returnsCache.find((r) => r.pedidoId === order.id);
    if (ret) {
      if (ret.status === 'SOLICITADA') return 'requested';
      if (ret.status === 'REJEITADA') return 'rejected';
      return 'done';
    }
    if (order.limiteSolicitacaoDevolucao && todayISO() > String(order.limiteSolicitacaoDevolucao).slice(0, 10)) return 'expired';
    return 'eligible';
  }

  function listReturns() {
    const path = currentUser?.perfil === 'ADMIN' ? '/api/admin/devolucoes' : '/api/devolucoes/minhas';
    return A.get(path).then((list) => {
      returnsCache = list || [];
      return returnsCache.map(mapReturn);
    });
  }

  // approve = lacre intacto (true) ou violado (false). No arrependimento (art. 49) o back aprova sempre; o lacre só decide a volta ao estoque.
  function resolveReturn(id, approve, observation) {
    return A.patch(`/api/admin/devolucoes/${Number(id)}/analise`, { produtoLacrado: !!approve, observacao: observation || null })
      .then((r) => ({ ok: true, return: mapReturn(r) })).catch((e) => ({ ok: false, message: e.message }));
  }

  // Devolução APROVADA: confirma que o produto chegou. É aqui que o back repõe o estoque (se lacrado) e estorna o pagamento.
  function confirmReturnReceipt(id) {
    return A.patch(`/api/admin/devolucoes/${Number(id)}/recebimento`)
      .then((r) => { invalidateAdminOrders(); return { ok: true, return: mapReturn(r) }; }).catch((e) => ({ ok: false, message: e.message }));
  }

  async function registerCustomer(data, hooks) {
    if (!termsVersion) {
      try { const t = await A.get('/api/termos/versao-atual'); termsVersion = typeof t === 'string' ? t : (t.versao || t.versaoAtual || C.termsVersion); } catch (e) { termsVersion = C.termsVersion; }
    }
    const nascimentoBruto = data.dataNascimento || data.nascimento || null;
    const nascimentoData = nascimentoBruto ? U.parseDateBR(nascimentoBruto) : null;
    const result = await postWithWakeRetry('/api/usuarios', {
      nome: data.nome, email: data.email, senha: data.senha,
      dataNascimento: nascimentoData ? U.toISODate(nascimentoData) : nascimentoBruto,
      telefones: (data.telefones && data.telefones.length ? data.telefones : [data.telefone]).map((p) => U.digits(p)).filter(Boolean),
      aceitaTermos: data.aceiteTermos !== false && data.aceitouTermos !== false,
      versaoTermos: termsVersion || C.termsVersion
    }, {}, hooks);
    if (result.ok) return { ok: true };
    const e = result.error || {};
    return { ok: false, status: e.status || 503, message: isColdStartError(e) ? 'Nosso servidor ainda está acordando. Aguarde alguns segundos e tente novamente.' : e.message };
  }

  async function loginCustomer(data, hooks) {
    const result = await postWithWakeRetry('/api/auth/login', { email: data.email, senha: data.senha }, {}, hooks);
    if (!result.ok) {
      const e = result.error || {};
      return { ok: false, status: e.status || 503, message: isColdStartError(e) ? 'Nosso servidor ainda está acordando. Aguarde alguns segundos e tente novamente.' : e.message };
    }
    A.saveSession(result.data);
    try { await loadProfile(); } catch (e) { /* se a API estiver em manutenção, o guard redireciona */ }
    return { ok: true, user: currentUser };
  }

  async function loginAdmin(data) {
    const res = await loginCustomer(data);
    if (!res.ok) return res;
    if (!isAdmin()) {
      await logout();
      return { ok: false, status: 403, message: 'Acesso restrito à administradora.' };
    }
    return { ok: true };
  }

  async function logout() {
    const refreshToken = A.getRefreshToken();
    // Limpa a sessão local ANTES da chamada de rede: a navegação para o login pode cancelar o fetch.
    A.clearTokens(); clearCatalogCache();
    currentUser = null; users = {}; cartId = null; cartCache = null;
    products = []; kits = []; categories = []; ordersCache = null; returnsCache = []; catalogMode = null; invalidateAdminOrders();
    orderHistory = []; savedCouponValue = null; termsVersion = null; alertsCache = []; maintenanceCache = null; window.GlowUp.maintenanceActive = false;
    if (refreshToken) { try { await A.post('/api/auth/logout', { refreshToken }); } catch (e) {} }
    return { ok: true };
  }

  const currentUserFn = () => currentUser;
  const getUser = (id) => (id == null ? currentUser : (users[id] || (currentUser && currentUser.id === Number(id) ? currentUser : null)));
  const isAdmin = () => currentUser && (currentUser.perfil === 'ADMIN' || currentUser.role === 'ADMIN');
  const canAccess = (path) => { if (!currentUser) return !String(path).startsWith('/admin'); if (isAdmin()) return true; return !String(path).startsWith('/admin'); };
  const getAdminContact = () => { const tel = U.digits(C.adminWhatsapp || ''); return { nome: C.adminNome || 'Domingas', telefone: tel, whatsapp: tel, email: C.adminEmail || '' }; };

  async function loadProfile() {
    if (!A.getToken()) { currentUser = null; return null; }
    const p = await A.get('/api/usuarios/me');
    const telefones = (p.telefones || []).map((t) => t.numero);
    const user = {
      id: p.id, nome: p.nome || '', email: p.email || '', cpf: p.cpf || '', dataNascimento: p.dataNascimento || '',
      telefone: telefones[0] || '', telefones,
      telefonesInfo: (p.telefones || []).map((t) => ({ id: t.id, numero: t.numero, principal: !!t.principal })),
      enderecos: (p.enderecos || []).map((e) => ({
        id: e.id, apelido: 'Endereço', cep: e.cep || '', rua: e.logradouro || '', numero: e.numero || '', complemento: e.complemento || '',
        bairro: e.bairro || '', cidade: e.cidade || '', uf: e.uf || '', pais: e.pais || 'Brasil', principal: !!e.principal
      })),
      role: (A.tokenPayload()?.role || p.perfil || p.role || 'CLIENT'), perfil: (A.tokenPayload()?.role || p.perfil || p.role || 'CLIENT'), senhaHash: null, avatarUrl: null, criadoEm: p.criadoEm || null, termosAceitos: [], pedidos: []
    };
    users[user.id] = user;
    currentUser = user;
    return user;
  }

  async function getLegalDoc(slug) {
    if (slug === 'termos') return { title: 'Termos de Uso', version: termsVersion || C.termsVersion, body: '', effectiveDate: '', updatedAt: '' };
    if (slug === 'privacidade') return { title: 'Política de Privacidade', version: '1.0', body: '', effectiveDate: '', updatedAt: '' };
    return null;
  }

  const listCategories = () => ensureCatalog().then(() => categories);

  async function setMainAddress(userId, addressId) {
    const user = getUser(userId) || currentUser;
    const addr = user?.enderecos?.find((a) => a.id === Number(addressId));
    if (!addr) return { ok: false, message: 'Endereço não encontrado.' };
    try {
      await A.patch(`/api/usuarios/me/enderecos/${addr.id}/principal`);
      await loadProfile();
      return { ok: true };
    } catch (e) { return { ok: false, message: e.message }; }
  }

  async function saveAddress(userId, addr) {
    const user = getUser(userId) || currentUser;
    if (!user) return { ok: false, message: 'Sessão expirada. Entre novamente.' };
    const existing = user.enderecos.find((a) => a.id === Number(addr.id));
    const body = {
      pais: 'Brasil', cep: U.digits(addr.cep), logradouro: addr.rua || addr.logradouro, numero: addr.numero,
      complemento: addr.complemento || null, bairro: addr.bairro, cidade: addr.cidade, uf: addr.uf,
      principal: addr.principal != null ? !!addr.principal : (existing ? !!existing.principal : false)
    };
    try {
      if (addr.id) await A.put(`/api/usuarios/me/enderecos/${addr.id}`, body);
      else await A.post('/api/usuarios/me/enderecos', body);
      await loadProfile();
      return { ok: true };
    } catch (e) { return { ok: false, message: e.message }; }
  }

  async function deleteAddress(userId, addressId) {
    try { await A.delete(`/api/usuarios/me/enderecos/${Number(addressId)}`); await loadProfile(); return { ok: true }; }
    catch (e) { return { ok: false, message: e.message }; }
  }

  async function syncPhones(desired) {
    const digitsDesired = (desired || []).map((p) => U.digits(p)).filter((p) => p.length >= 10);
    if (!digitsDesired.length) return { ok: false, message: 'Informe pelo menos um telefone válido.' };
    let info = (currentUser?.telefonesInfo || []).slice();
    for (const d of digitsDesired) {
      if (!info.some((t) => U.digits(t.numero) === d)) {
        await A.post('/api/usuarios/me/telefones', { numero: d });
      }
    }
    await loadProfile();
    info = currentUser?.telefonesInfo || [];
    for (const t of info.slice()) {
      if (!digitsDesired.includes(U.digits(t.numero))) {
        await A.delete(`/api/usuarios/me/telefones/${t.id}`);
      }
    }
    await loadProfile();
    const principal = (currentUser?.telefonesInfo || []).find((t) => U.digits(t.numero) === digitsDesired[0]);
    if (principal && !principal.principal) await A.patch(`/api/usuarios/me/telefones/${principal.id}/principal`);
    await loadProfile();
    return { ok: true };
  }

  async function updateProfile(userId, data) {
    try {
      const res = await A.put('/api/usuarios/me', {
        nome: data.nome, email: data.email,
        senhaAtual: data.senhaAtual || null, novaSenha: data.novaSenha || null
      });
      // Ao trocar o e-mail o back emite outro JWT (o e-mail é o "assunto" do token): sem guardá-lo a sessão cairia na próxima chamada.
      if (res && res.novoToken) A.saveSession({ accessToken: res.novoToken, refreshToken: A.getRefreshToken() });
      if (Array.isArray(data.telefones)) {
        const wanted = data.telefones.map((p) => U.digits(p)).filter(Boolean);
        const hasPhones = (currentUser?.telefonesInfo || []).length > 0;
        // Conta sem telefone (a da administradora nasce assim) pode salvar o perfil sem informar um.
        if (!wanted.length && !hasPhones) await loadProfile();
        else {
          const sync = await syncPhones(data.telefones);
          if (!sync.ok) return sync;
        }
      } else {
        await loadProfile();
      }
      return { ok: true };
    } catch (e) { return { ok: false, message: e.message }; }
  }

  // Intervalos do filtro da Visão geral (as datas são sempre do dia local, nunca convertidas para UTC).
  function dashboardRange(period) {
    const key = U.normalize(period || '');
    const hoje = U.startOfDay(new Date());
    let inicio;
    if (/hoje/.test(key)) inicio = hoje;
    else if (/\b7\b/.test(key)) inicio = U.addDays(hoje, -6);
    else if (/este mes/.test(key)) inicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
    else if (/ano/.test(key)) inicio = U.addDays(hoje, -364);
    else if (/todo/.test(key)) inicio = null;
    else inicio = U.addDays(hoje, -29);
    return { inicio, fim: hoje };
  }

  async function dashboardMetrics(period = 'Últimos 30 dias') {
    const { inicio, fim } = dashboardRange(period);
    const iso = (d) => U.toISODate(d);
    let anterior = null;
    if (inicio) {
      const dias = U.diffDays(fim, inicio) + 1;
      const antFim = U.addDays(inicio, -1);
      anterior = { inicio: U.addDays(antFim, -(dias - 1)), fim: antFim };
    }
    const resumoPath = (a, b) => `/api/admin/dashboard/resumo?inicio=${a ? iso(a) : '2000-01-01'}&fim=${iso(b)}`;
    const [resumo, resumoAnt, orders, produtosAdmin] = await Promise.all([
      A.get(resumoPath(inicio, fim)),
      anterior ? A.get(resumoPath(anterior.inicio, anterior.fim)) : Promise.resolve(null),
      adminOrders(true),
      loadCatalog(true, true)
    ]);

    const faturamento = money(resumo.faturamentoBruto) || 0;
    const lucro = money(resumo.lucroRealTotal) || 0;
    const vendas = num(resumo.pedidosPagos) || 0;
    const faturamentoAnt = resumoAnt ? (money(resumoAnt.faturamentoBruto) || 0) : 0;
    const delta = faturamentoAnt > 0 ? Math.round(((faturamento - faturamentoAnt) / faturamentoAnt) * 1000) / 10 : null;

    // estoque: só o que a administradora tem fisicamente (pronta-entrega ativa; kit manual não tem saldo próprio)
    const fisicos = produtosAdmin.filter((p) => p.ativo && p.modalidade === 'PRONTA_ENTREGA' && p.tipoKit !== 'MANUAL');
    const estoqueTotal = fisicos.reduce((s, p) => s + (num(p.estoque) || 0), 0);
    const limite = C.lowStockThreshold ?? 5;
    const estoqueBaixo = fisicos.filter((p) => (available(p) ?? Infinity) <= limite).length;

    // gráfico: pedidos pagos do período, em até 7 (período curto) ou 12 (período longo) faixas de dias
    const pagos = orders.filter((o) => ['PAGO', 'EM_SEPARACAO', 'ENVIADO', 'ENTREGUE'].includes(o.status));
    const dentro = (o) => { const d = U.startOfDay(o.criadoEm); return d <= fim && (!inicio || d >= inicio); };
    const noPeriodo = pagos.filter(dentro);
    let primeiro = inicio;
    if (!primeiro) primeiro = noPeriodo.length ? U.startOfDay(new Date(Math.min.apply(null, noPeriodo.map((o) => new Date(o.criadoEm).getTime())))) : fim;
    const dias = Math.max(1, U.diffDays(fim, primeiro) + 1);
    const n = dias <= 7 ? dias : dias <= 31 ? 7 : 12;
    const span = Math.ceil(dias / n);
    const chart = Array.from({ length: n }, (_, i) => ({ de: U.addDays(primeiro, i * span), ate: U.addDays(primeiro, (i + 1) * span - 1), fat: 0, lucro: 0 }))
      .filter((b) => b.de <= fim);
    noPeriodo.forEach((o) => {
      const idx = Math.min(chart.length - 1, Math.floor(U.diffDays(o.criadoEm, primeiro) / span));
      if (idx < 0) return;
      chart[idx].fat += o.total || 0;
      chart[idx].lucro += o.financeiro?.lucroReal || 0;
    });
    chart.forEach((b) => {
      b.fat = U.round2(b.fat); b.lucro = U.round2(b.lucro);
      b.label = U.diffDays(b.ate, b.de) > 0 ? `${U.formatDateShort(b.de)} – ${U.formatDateShort(b.ate > fim ? fim : b.ate)}` : U.formatDateShort(b.de);
    });

    const porProduto = {};
    noPeriodo.forEach((o) => o.itens.forEach((i) => { porProduto[i.nome] = (porProduto[i.nome] || 0) + i.qtd; }));
    const top = Object.entries(porProduto).sort((a, b) => b[1] - a[1])[0];
    return {
      periodo: period, vendas, faturamento, lucro,
      pedidos: orders.length,
      ticket: vendas ? U.round2(faturamento / vendas) : 0,
      delta, semDados: num(resumo.pedidosComDadosInsuficientes) || 0,
      estoqueTotal, estoqueBaixo,
      produtoMaisVendido: top ? { nome: top[0], qtd: top[1] } : null,
      pedidosPorStatus: resumo.pedidosPorStatus || {},
      chart,
      recentes: orders.slice().sort((a, b) => new Date(b.criadoEm) - new Date(a.criadoEm)).slice(0, 6)
        .map((o) => ({ id: o.id, titulo: 'Pedido #' + o.id, clienteNome: o.clienteNome, criadoEm: o.criadoEm, status: o.status, total: o.total }))
    };
  }

  async function expiryAlerts() {
    const [list] = await Promise.all([A.get('/api/admin/alertas-validade'), ensureCatalog().catch(() => null)]);
    alertsCache = list || [];
    return alertsCache.map((a) => {
      const p = getProduct(a.produtoId);
      return { id: a.id, dias: num(a.diasRestantes) || 0, dataValidade: a.dataValidade || null, produto: { id: a.produtoId, nome: a.produto || p?.nome || '', imagem: p?.imagem || U.placeholderImage, estoque: p?.estoque ?? null } };
    });
  }

  function generateAlert(productId) {
    return Promise.resolve({ ok: true, id: 'local-' + Date.now(), dias: 0, produto: getProduct(productId) });
  }

  async function crmSuggestions() {
    // o telefone da cliente vem do pedido; se a lista de pedidos falhar, as sugestões ainda aparecem (sem botão de WhatsApp)
    const [repos, orders] = await Promise.all([A.get('/api/admin/reposicoes?status=PENDENTE'), adminOrders().catch(() => [])]);
    const phoneByPedido = new Map(orders.map((o) => [o.id, o.clienteTelefone]));
    return (repos || []).map((r) => {
      const phone = phoneByPedido.get(r.pedidoId) || '';
      const dias = r.dataEstimadaReposicao ? U.diffDays(new Date(r.dataEstimadaReposicao + 'T12:00:00'), new Date()) : null;
      return {
        id: r.id, pedidoId: r.pedidoId,
        user: { id: r.clienteId, nome: r.cliente || '', telefones: phone ? [phone] : [] },
        produto: r.produto || '', dias, usoMedioDias: num(r.tempoMedioUsoDias), quantidadeSugerida: num(r.quantidadeSugerida), status: r.status || 'PENDENTE'
      };
    });
  }

  // status: CONTATADA ou DESCARTADA (a sugestão sai da lista de pendentes)
  function updateRestock(id, status) {
    return A.patch(`/api/admin/reposicoes/${Number(id)}/status`, { status })
      .then(() => ({ ok: true })).catch((e) => ({ ok: false, message: e.message }));
  }

  // Modo manutenção: o estado de verdade mora no back (tabela de configurações); o front só lê e altera.
  function loadMaintenanceStatus() {
    return A.get('/api/admin/manutencao/status').then((v) => {
      maintenanceCache = v === true || v === 'true';
      window.GlowUp.maintenanceActive = maintenanceCache;
      return maintenanceCache;
    });
  }

  function setMaintenance(active) {
    return A.post(active ? '/api/admin/manutencao/ativar' : '/api/admin/manutencao/desativar')
      .then(() => { maintenanceCache = !!active; window.GlowUp.maintenanceActive = !!active; return { ok: true }; })
      .catch((e) => ({ ok: false, message: e.message }));
  }

  const resolveMaintenance = (id, action) => setMaintenance(action === 'activate');

  async function searchAll(query) {
    const q = String(query || '').trim().toLowerCase();
    if (!q) return { products: [], kits: [], orders: [], clients: [] };
    const admin = !!isAdmin();
    const prods = (await listProducts()).filter((p) => p.ativo !== false && p.tipoVenda !== 'kit' && (p.nome + ' ' + (p.marca || '')).toLowerCase().includes(q));
    const ks = (await listKits()).filter((k) => k.ativo !== false && k.nome.toLowerCase().includes(q));
    const orders = (await listOrders(admin).catch(() => [])).filter((o) => String(o.id).includes(q) || (o.clienteNome || '').toLowerCase().includes(q));
    return { products: prods, kits: ks, orders, clients: [] };
  }

  function stockAdjust(productId, delta, reason) {
    const p = getProduct(productId);
    if (p?.tipoKit === 'MANUAL') return Promise.resolve({ ok: false, message: 'Kits manuais não têm estoque próprio; ajuste os componentes.' });
    const body = { quantidade: delta, observacao: reason || null };
    return A.put(`/api/admin/estoque/produtos/${Number(productId)}`, body).then((e) => ({ ok: true, estoque: e })).catch((e) => ({ ok: false, message: e.message }));
  }

  async function uploadProductImage(productId, imageDataUrl) {
    if (!imageDataUrl || !String(imageDataUrl).startsWith('data:image')) return { ok: true };
    try {
      const blob = await (await fetch(imageDataUrl)).blob();
      const form = new FormData();
      form.append('arquivo', blob, 'produto.' + (blob.type.split('/')[1] || 'png'));
      await A.post(`/api/produtos/${Number(productId)}/imagem`, form);
      return { ok: true };
    } catch (e) { return { ok: false, message: e.message }; }
  }

  // O formulário usa dd/mm/aaaa; a API espera aaaa-mm-dd.
  function toIsoDate(value) {
    if (!value) return null;
    const s = String(value).trim();
    if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
    const d = U.parseDateBR(s);
    return d ? U.toISODate(d) : null;
  }

  async function saveProduct(data) {
    await loadCatalog(true, true);
    const isKit = data.tipoVenda === 'kit';
    // Foto nova = data:image escolhida no formulário. O placeholder (produto sem foto) nunca é enviado como se fosse upload.
    const isNewImage = data.imagem && String(data.imagem).startsWith('data:image') && data.imagem !== U.placeholderImage;
    const imageData = isNewImage ? data.imagem : null;
    const imageUrl = !imageData && data.imagem && /^(https?:\/\/|assets\/img\/produtos\/)/.test(data.imagem) ? data.imagem : null;
    const fimPromocao = data.promoAtiva && data.dataFimPromocao ? (() => { const d = U.parseDateBR(data.dataFimPromocao); return d ? new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59).toISOString() : null; })() : null;
    if (data.promoAtiva && !fimPromocao) return { ok: false, errors: { dataFimPromocao: 'Informe o fim da promoção.' } };
    const precoPromocional = data.promoAtiva && data.precoPromocional ? Number(data.precoPromocional) : null;
    const modalidade = data.modalidade === 'SOB_ENCOMENDA' ? 'SOB_ENCOMENDA' : 'PRONTA_ENTREGA';
    const manual = String(data.tipoKit || '').toUpperCase() === 'MANUAL';
    const base = {
      nome: data.nome, descricao: data.descricao || null, marca: data.marca || null, categoria: data.categoria,
      custoFabrica: Number(data.custo) || 0, precoBase: Number(data.precoBase ?? data.preco) || 0,
      precoPromocional, dataLimitePromocao: fimPromocao,
      modalidade
    };
    let body;
    if (isKit) {
      body = {
        ...base,
        tipo: manual ? 'MANUAL' : 'FABRICA',
        quantidadeEstoque: modalidade === 'PRONTA_ENTREGA' && !manual ? (num(data.estoque) || 0) : null,
        itens: (data.itensKit || []).map((i) => ({ produtoId: Number(i.produtoId), quantidade: Math.max(1, num(i.qtd) || 1) }))
      };
    } else {
      body = {
        ...base,
        imagemUrl: imageUrl,
        quantidadeEstoque: modalidade === 'PRONTA_ENTREGA' ? (num(data.estoque) || 0) : null,
        dataValidade: toIsoDate(data.validade || data.dataValidade),
        tempoMedioUsoDias: num(data.tempoMedioUsoDias) || null
      };
    }
    try {
      const saved = data.id
        ? await (isKit ? A.put(`/api/kits/${data.id}`, body) : A.put(`/api/produtos/${data.id}`, body))
        : await (isKit ? A.post('/api/kits', body) : A.post('/api/produtos', body));
      const savedId = saved?.id;
      if (savedId && imageData) await uploadProductImage(savedId, imageData);
      await loadCatalog(true, true);
      return { ok: true, product: getProduct(savedId) || null, isNew: !data.id };
    } catch (e) {
      const errors = {};
      const msg = e.message || 'Erro ao salvar.';
      if (/categoria/i.test(msg)) errors.categoria = msg;
      else if (/preço|preco/i.test(msg)) errors.preco = msg;
      else if (/estoque|validade/i.test(msg)) errors.estoque = msg;
      else if (/kit|componente|itens/i.test(msg)) errors.itensKit = msg;
      else errors.nome = msg;
      // `message` faz a tela mostrar um aviso com o motivo real devolvido pela API (preço, estoque, categoria...).
      return { ok: false, errors, message: msg };
    }
  }

  // Reativa um produto/kit inativado (desfaz a exclusão lógica) sem precisar cadastrá-lo de novo.
  function restoreProduct(id) {
    return A.patch(`/api/admin/produtos/${Number(id)}/reativar`, {}).then(() => loadCatalog(true, true)).then(() => ({ ok: true })).catch((e) => ({ ok: false, message: e.message }));
  }

  function createProduct(data) { return saveProduct({ ...data, id: null }); }
  function updateProduct(id, data) { return saveProduct({ ...data, id }); }

  function removeProduct(id) {
    const p = getProduct(id);
    const path = p?.tipoVenda === 'kit' ? `/api/kits/${id}` : `/api/produtos/${id}`;
    return A.delete(path).then(() => loadCatalog(true, true)).then(() => ({ ok: true })).catch((e) => ({ ok: false, message: e.message }));
  }

  function statusLabel(s) {
    return { AGUARDANDO_PAGAMENTO: 'Aguardando pagamento', PAGO: 'Pago', EM_SEPARACAO: 'Em separação', ENVIADO: 'Enviado', ENTREGUE: 'Entregue', CANCELADO: 'Cancelado', DEVOLVIDO: 'Devolvido' }[s] || s;
  }
  const statusVariant = (s) => ({ AGUARDANDO_PAGAMENTO: 'info', PAGO: 'warning', EM_SEPARACAO: 'warning', ENVIADO: 'primary', ENTREGUE: 'success', CANCELADO: 'danger', DEVOLVIDO: 'danger' }[s] || 'secondary');
  const isOnSale = (p) => !!p && p.precoPromocional != null && p.precoPromocional < p.precoBase && (!p.dataFimPromocao || todayISO() <= p.dataFimPromocao);
  const effectivePrice = (p) => (isOnSale(p) ? p.precoPromocional : p.precoBase);
  const available = (p) => {
    if (!p) return 0;
    if (p.modalidade === 'SOB_ENCOMENDA') return null;
    if (p.tipoKit === 'MANUAL') {
      const comps = (p.itensKit || []).map((i) => getProduct(i.produtoId)).filter(Boolean);
      if (!comps.length) return 0;
      return Math.min(...comps.map((c) => { const a = available(c); return a == null ? Infinity : Math.floor(a / Math.max(1, (p.itensKit.find((x) => x.produtoId === c.id) || {}).qtd || 1)); }));
    }
    return p.estoque == null ? 0 : Math.max(0, p.estoque - (p.estoqueReservado || 0));
  };
  function addToCart(productId, qty = 1) { return cartAdd(productId, qty); }
  const initials = (name) => String(name || '').split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase();
  function saleRemainingDays(p) {
    if (!isOnSale(p) || !p.dataFimPromocao) return null;
    const diff = Math.ceil((new Date(p.dataFimPromocao + 'T23:59:59') - new Date()) / 86400000);
    return diff >= 0 ? diff : null;
  }

  function cartLines() {
    return (cartCache?.itens || []).map((i) => {
      const p = getProduct(i.produtoId) || { id: i.produtoId, nome: i.nome, imagem: i.imagem, marca: '', modalidade: null, tipoVenda: 'varejo', tipoKit: null };
      const issue = i.disponivel === false ? 'soldout' : (i.estoqueDisponivel != null && i.quantidade > i.estoqueDisponivel ? 'reduced' : null);
      return { produtoId: i.produtoId, produto: p, qtd: i.quantidade, unit: i.precoUnitario, subtotal: i.subtotal, issue, disponivel: i.estoqueDisponivel };
    });
  }
  function cartTotals() {
    const lines = cartLines();
    return { lines, subtotal: cartCache?.subtotal || 0, total: cartCache?.subtotal || 0, cupom: savedCouponValue, desconto: cartCache?.desconto || 0 };
  }
  function cartReconcile() {
    const msgs = [];
    cartLines().forEach((l) => { if (l.issue === 'soldout') msgs.push(`"${l.produto.nome}" esgotou.`); else if (l.issue === 'reduced') msgs.push(`"${l.produto.nome}" agora tem só ${l.disponivel} em estoque.`); });
    if (!cartCache) cart().catch(() => {});
    return msgs;
  }
  function checkDelivery(endereco) {
    const uf = U.normalize(endereco?.uf || '');
    // Texto sem acento/maiúsculas e sem pontuação: "Itapoã - II" vira "itapoa ii".
    const bairro = U.normalize(endereco?.bairro || '').replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim();
    const motivo = 'Entrega em casa disponível apenas para Itapoã e Paranoá (DF).';
    if (uf !== 'df') return { allowed: false, reason: motivo, frete: null };
    // "Parque" (Paranoá Parque, Itapoã/Itapuã/Itapoá Parque) só tem retirada no local.
    const atendida = /\b(paranoa|itapoa|itapua)\b/.test(bairro);
    if (atendida && /\bparque\b/.test(bairro)) {
      return { allowed: false, reason: 'Frete domiciliar indisponível para esta localidade (Parque). Selecione Retirada Grátis.', frete: null };
    }
    // Aceita qualquer endereço que contenha Paranoá, Itapoã, Itapuã ou Itapoá (com ou sem acento).
    const allowed = atendida || (C.allowedNeighborhoods || []).includes(bairro);
    return { allowed, reason: allowed ? null : motivo, frete: allowed ? (C.deliveryFee || 0) : null };
  }
  function nextStatus(o) {
    return { PAGO: 'EM_SEPARACAO', EM_SEPARACAO: 'ENVIADO', ENVIADO: 'ENTREGUE' }[o?.status] || null;
  }
  function statusClass(s) {
    return { AGUARDANDO_PAGAMENTO: 'pendente', PAGO: 'pago', EM_SEPARACAO: 'separacao', ENVIADO: 'enviado', ENTREGUE: 'entregue', CANCELADO: 'cancelado', DEVOLVIDO: 'devolvido' }[s] || 'pendente';
  }
  async function advanceOrder(id, status) {
    const o = await getOrder(id, true);
    if (!o) return { ok: false, message: 'Pedido não encontrado.' };
    const target = status || nextStatus(o);
    if (!target) return { ok: false, message: 'Pedido sem próxima etapa disponível.' };
    try { await A.patch(`/api/admin/pedidos/${Number(id)}/status`, { status: target }); invalidateAdminOrders(); return { ok: true, status: target }; }
    catch (e) { return { ok: false, message: e.message }; }
  }

  // Pedido criado com o ViaCEP fora do ar fica com o endereço "a conferir": sem isso o back não deixa ir para Em separação/Enviado.
  async function confirmOrderAddress(id) {
    try { await A.patch(`/api/admin/pedidos/${Number(id)}/endereco-conferido`); invalidateAdminOrders(); return { ok: true }; }
    catch (e) { return { ok: false, message: e.message }; }
  }
  const isManualKit = (p) => p?.tipoKit === 'MANUAL' || (p?.tipoVenda === 'kit' && p?.tipoKit === 'MANUAL');
  function kitComponents(p) {
    return (p?.itensKit || []).map((i) => ({ qtd: i.qtd, produto: getProduct(i.produtoId) })).filter((c) => c.produto);
  }
  function kitSummary(items) {
    let custo = 0, precoAvulso = 0, min = Infinity;
    (items || []).forEach((i) => {
      const p = getProduct(i.produtoId); if (!p) return;
      const q = Math.max(1, num(i.qtd) || 1);
      custo += (p.custoFabrica || 0) * q; precoAvulso += (p.precoVigente ?? p.precoBase ?? 0) * q;
      const a = available(p); if (a != null && a !== Infinity) min = Math.min(min, Math.floor(a / q));
    });
    return { custo: U.round2(custo), precoAvulso: U.round2(precoAvulso), disponivel: min === Infinity ? null : min };
  }
  const isMaintenance = () => maintenanceCache === true || window.GlowUp.maintenanceActive === true;
  function login(email, senha, hooks) { return loginCustomer({ email, senha }, hooks); }
  async function register(data, hooks) {
    const res = await registerCustomer(data, hooks);
    if (res.ok) return { ok: true };
    const msg = res.message || '';
    const errors = {};
    if (/18|nascimento|idade/i.test(msg)) errors.nascimento = msg;
    else if (/e-?mail/i.test(msg)) errors.email = msg;
    else if (/senha/i.test(msg)) errors.senha = msg;
    else if (/telefone/i.test(msg)) errors.telefone = msg;
    else if (/termos/i.test(msg)) errors.termos = msg;
    else errors.email = msg;
    return { ok: false, errors };
  }
  function requestPasswordReset(email) {
    return A.post('/api/auth/esqueci-senha', { email }).then(() => ({ ok: true })).catch((e) => ({ ok: false, message: e.message }));
  }
  function resetPassword(codigoOtp, novaSenha, email) {
    return A.post('/api/auth/redefinir-senha', { email, codigoOtp: U.digits(codigoOtp), novaSenha }).then(() => ({ ok: true })).catch((e) => ({ ok: false, message: e.message }));
  }
  // Hidrata a sessão ao abrir qualquer tela: sem isso o guard via currentUser=null e devolvia para o login.
  const ready = (async () => {
    if (A.getToken()) { try { await loadProfile(); } catch (e) { /* token inválido/expirado: guard decide */ } }
  })();

  const removeAddress = (userId, addressId) => deleteAddress(userId, addressId);
  const setDefaultAddress = (userId, addressId) => setMainAddress(userId, addressId);
  const removeCoupon = () => clearCoupon();

  window.GlowUp.store = {
    ready,
    ensureCatalog, listProducts, reloadCatalog, getProduct, kitsUsing, listKits,
    cart, cartAdd, cartSetQty, cartRemove, cartClear, cartCount, cartTotal, cartCanCheckout,
    cartLines, cartTotals, cartReconcile, checkDelivery,
    previewTotals, createOrder, applyCoupon, saveCoupon, savedCoupon, clearCoupon, removeCoupon,
    listOrders, listOrdersAdmin, getOrder, getPayment, syncPayment, startPayment, tokenizeCard,
    nextStatus, statusClass, advanceOrder, confirmOrderAddress,
    requestReturn, returnState, listReturns, resolveReturn, confirmReturnReceipt,
    registerCustomer, loginCustomer, loginAdmin, logout, currentUser: currentUserFn, getUser, isAdmin, canAccess, getAdminContact, loadProfile,
    getLegalDoc, listCategories, setMainAddress, saveAddress, deleteAddress, updateProfile,
    removeAddress, setDefaultAddress,
    dashboardMetrics, expiryAlerts, crmSuggestions, updateRestock, generateAlert, resolveMaintenance, loadMaintenanceStatus, setMaintenance, searchAll,
    stockAdjust, saveProduct, createProduct, updateProduct, removeProduct, restoreProduct,
    isManualKit, kitComponents, kitSummary, isMaintenance,
    login, register, requestPasswordReset, resetPassword,
    statusLabel, statusVariant, isOnSale, effectivePrice, available, addToCart, initials, saleRemainingDays
  };
})();
