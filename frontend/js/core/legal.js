/* Textos legais exibidos nas janelas (Termos de Uso e Política de Devolução). */
(function (global) {
    'use strict';
    const GlowUp = (global.GlowUp = global.GlowUp || {});

    GlowUp.legal = {
        terms: {
            title: 'Termos de Uso do GlowUp',
            html:
                '<p>O GlowUp é um protótipo acadêmico criado para apoiar a revendedora Domingas. Ao usar a plataforma:</p>' +
                '<p><strong>a) Maioridade:</strong> você declara ter 18 anos ou mais.</p>' +
                '<p><strong>b) Devoluções e recolhimento:</strong> você aceita a Política de Devolução e as regras de recolhimento: 7 dias corridos após a entrega, somente com lacre intacto, com entrega presencial ou custo da coleta por conta da cliente.</p>' +
                '<p><strong>c) Dados pessoais (LGPD):</strong> você consente com o tratamento de dados pessoais simplificados (nome, data de nascimento, e-mail, telefones e endereços) para conta, contato e entrega. Não coletamos nem armazenamos CPF.</p>' +
                '<p><strong>d) Área de entrega:</strong> a entrega em casa é restrita a Itapoã e Paranoá, DF. Itapoã Parque, Paranoá Parque e demais regiões devem usar Retirada no Local.</p>' +
                '<p>O aceite destes termos é registrado com data e hora. Pagamentos deste protótipo são simulações acadêmicas.</p>'
        },
        returns: {
            title: 'Política de Devolução',
            html:
                '<p>Você pode solicitar a devolução em até 7 dias corridos após o recebimento.</p>' +
                '<p><strong>Regra sanitária:</strong> perfumes e cosméticos só podem ser devolvidos com lacre original intacto, sem sinais de uso ou violação.</p>' +
                '<p>A devolução poderá ocorrer por coleta com frete combinado ou entrega presencial no ponto de retirada. Após conferência, o estorno segue o meio de pagamento original.</p>'
        }
    };
})(window);
