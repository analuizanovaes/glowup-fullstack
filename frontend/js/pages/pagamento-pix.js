/* Tela: pagamento-pix.html */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;

    const user = S.currentUser();
    const order = S.getOrder(U.query('pedido'));
    if (!order || (user && order.userId !== user.id) || order.pagamento.metodo !== 'PIX') { location.replace(R.notFound); return; }
    if (order.status !== 'Aguardando pagamento') {
        location.replace(order.status === 'Cancelado' ? R.notFound : R.confirmation + '?pedido=' + order.id);
        return;
    }

    const input = U.$('.pix-copy-input');
    const copyBtn = U.$('.btn-copy');
    const payBtn = U.$('.btn-pay');
    input.value = S.pixCode(order);

    // resumo do pedido + tempo da reserva
    const info = document.createElement('p');
    info.className = 'pix-order-info';
    U.$('.pix-header').appendChild(info);
    function tick() {
        const left = new Date(order.expiraEm) - new Date();
        if (left <= 0) {
            S.expirePendingOrders();
            UI.alert('O tempo para pagar este Pix terminou e a reserva dos produtos foi liberada. Você pode refazer o pedido.', { title: 'Pix expirado', icon: UI.icons.warning })
                .then(() => { location.href = R.cart; });
            clearInterval(timer);
            return;
        }
        const m = Math.floor(left / 60000), s = Math.floor(left % 60000 / 1000);
        info.innerHTML = `Pedido <strong>${order.id}</strong> • <strong>${U.money(order.total)}</strong> • expira em ${U.pad(m)}:${U.pad(s)}`;
    }
    const timer = setInterval(tick, 1000);
    tick();

    copyBtn.addEventListener('click', async function () {
        const ok = await U.copyText(input.value);
        if (ok) {
            copyBtn.textContent = 'Copiado!';
            setTimeout(() => { copyBtn.textContent = 'Copiar'; }, 2000);
            UI.toast('Cole o código no app do seu banco, na opção Pix copia e cola.', { type: 'success', title: 'Código Pix copiado' });
        } else {
            input.select();
            UI.toast('Selecione o código e copie manualmente.', { type: 'warning', title: 'Não foi possível copiar' });
        }
    });

    // "Efetuar pagamento" simula o webhook de aprovação do gateway
    payBtn.addEventListener('click', function () {
        payBtn.disabled = true;
        const proc = UI.processing('Confirmando pagamento', 'Aguardando a confirmação do Pix...');
        setTimeout(function () {
            proc.close();
            clearInterval(timer);
            S.approvePayment(order.id);
            location.href = R.confirmation + '?pedido=' + order.id;
        }, 1800);
    });
});
