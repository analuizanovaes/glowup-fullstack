/* GlowUp - configurações gerais do front-end.
   Tudo o que for regra de negócio "ajustável" fica aqui, em um único lugar. */
(function (global) {
    'use strict';

    const GlowUp = (global.GlowUp = global.GlowUp || {});

    GlowUp.config = {
        // Quando true, as rotas protegidas exigem login (RF01). Use false só para revisar o layout.
        guardsEnabled: true,

        storagePrefix: 'glowup:',
        termsVersion: '1.0',

        minAge: 18,                 // RN07 / RD02
        returnWindowDays: 7,        // RN01 / RD03
        maxInstallments: 3,         // RN02
        deliveryFee: 10,            // RN03 (R$ 10,00 fixo)
        deliveryDays: 7,            // prazo padrão de entrega
        lowStockThreshold: 5,       // "estoque baixo" no painel
        expiryAlertDays: 25,        // alerta de validade (pronta-entrega)
        pixExpiresMinutes: 30,      // reserva de estoque do Pix pendente (RN04)
        crmWindowDays: 30,          // CRM: mostra reposições previstas nesse prazo

        // Bairros com entrega em casa (RN03). Comparação sem acento/maiúsculas.
        allowedNeighborhoods: ['itapoa', 'paranoa'],

        // Taxas do gateway absorvidas pela revendedora (RN02/RN05). Valores de simulação:
        // na integração real virão do Mercado Pago (webhook) ou da tela de configuração.
        gatewayFees: { PIX: 0.0099, CARTAO: 0.0498 },

        // Juros de parcelamento repassados à cliente (simulação visual; o valor real vem do gateway).
        installmentInterest: { 1: 0, 2: 0.0196, 3: 0.0392 },

        // Páginas e permissões (RBAC)
        pages: {
            public: ['paginaprincipal', 'login', 'cadastro', 'recuperaracesso', 'redefinirsenha',
                     'termosdeuso', 'termodedevolucao', 'manutencao', 'pedidonaoencontrado'],
            client: ['vitrini', 'carrinho', 'chekout', 'pagamento-pix', 'confirmacaodepedido',
                     'meuspedidos', 'perfil'],
            admin: ['visaogeral', 'pedidos', 'devolucoes', 'produtos', 'cadastrarprodutos', 'crm', 'meuperfil']
        },

        // Nomes reais dos arquivos HTML do projeto
        routes: {
            landing: 'paginaprincipal.html',
            login: 'login.html',
            signup: 'cadastro.html',
            recover: 'recuperaracesso.html',
            reset: 'redefinirsenha.html',
            terms: 'termosdeuso.html',
            returnsPolicy: 'termodedevolucao.html',
            maintenance: 'manutencao.html',
            notFound: 'pedidonaoencontrado.html',
            store: 'vitrini.html',
            cart: 'carrinho.html',
            checkout: 'chekout.html',
            pix: 'pagamento-pix.html',
            confirmation: 'confirmacaodepedido.html',
            myOrders: 'meuspedidos.html',
            profile: 'perfil.html',
            dashboard: 'visaogeral.html',
            orders: 'pedidos.html',
            returns: 'devolucoes.html',
            products: 'produtos.html',
            productForm: 'cadastrarprodutos.html',
            crm: 'crm.html',
            adminProfile: 'meuperfil.html'
        }
    };

    GlowUp.page = (location.pathname.split('/').pop() || '').replace(/\.html?$/i, '') || 'paginaprincipal';
})(window);
