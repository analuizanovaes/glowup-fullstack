/* Tela: confirmacaodepedido.html */
GlowUp.ready(function () {
    const { utils: U, store: S, config: C } = GlowUp;
    const R = C.routes;
    const user = S.currentUser();
    const order = S.getOrder(U.query('pedido'));
    if (!order || (user && order.userId !== user.id) || order.status === 'Aguardando pagamento' || order.status === 'Cancelado') {
        location.replace(R.notFound);
        return;
    }
    const buttons = U.$$('.action-buttons a');
    buttons[0].setAttribute('href', R.myOrders);
    buttons[1].setAttribute('href', R.store);

    const p = document.createElement('p');
    p.className = 'order-confirm-info';
    const prazo = order.entrega.tipo === 'home' ? `entrega em até ${order.entrega.prazoDias} dias` : 'retirada no local, combine o horário';
    p.innerHTML = `Pedido <strong>${order.id}</strong> • ${U.money(order.total)} • ${prazo}`;
    U.$('.page-title').insertAdjacentElement('afterend', p);
});
