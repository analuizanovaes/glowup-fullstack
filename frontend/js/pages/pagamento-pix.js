/* Tela: pagamento-pix.html */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;

    const user = S.currentUser();
    const order = await S.getOrder(U.query('pedido'));
    if (!order || (user && order.userId !== user.id)) { location.replace(R.notFound); return; }
    if (order.status !== 'AGUARDANDO_PAGAMENTO') {
        location.replace(order.status === 'CANCELADO' ? R.notFound : R.confirmation + '?pedido=' + order.id);
        return;
    }
    // A lista de pedidos não informa o método de pagamento: quem sabe é o pagamento em si.
    let pix = await S.getPayment(order.id).catch(() => null);
    if (!pix) {
        // Pedido criado, mas o Pix ainda não foi gerado (ex.: a primeira tentativa falhou): gera agora.
        const started = await S.startPayment({ orderId: order.id, metodo: 'PIX', parcelas: 1 });
        if (!started.ok) {
            UI.flash(started.message || 'Não foi possível gerar o Pix agora. Tente novamente em instantes.', { type: 'error', title: 'Pix indisponível' });
            location.replace(R.myOrders);
            return;
        }
        pix = started;
    }
    if (pix.metodo && pix.metodo !== 'PIX') { location.replace(R.myOrders); return; }

    const input = U.$('.pix-copy-input');
    const copyBtn = U.$('.btn-copy');
    const payBtn = U.$('.btn-pay');
    input.value = pix.pixCopiaECola || 'Código Pix indisponível';
    const qrBox = U.$('.qrcode-box');
    if (pix.qrcode) {
        const src = /^data:/.test(pix.qrcode) ? pix.qrcode : 'data:image/png;base64,' + pix.qrcode;
        qrBox.innerHTML = `<img src="${src}" alt="QR Code Pix" style="width:100%;max-width:220px;height:auto;display:block;margin:0 auto">`;
    } else if (pix.pixCopiaECola && window.QRCode) {
        // O gateway não mandou a imagem (ex.: modo de simulação): desenha o QR Code a partir do copia e cola.
        qrBox.innerHTML = '';
        qrBox.style.display = 'flex';
        qrBox.style.justifyContent = 'center';
        new window.QRCode(qrBox, { text: pix.pixCopiaECola, width: 200, height: 200, correctLevel: window.QRCode.CorrectLevel.L });
    }

    // resumo do pedido + tempo da reserva
    const info = document.createElement('p');
    info.className = 'pix-order-info';
    U.$('.pix-header').appendChild(info);
    function tick() {
        if (!pix.pixExpiraEm) {
            info.innerHTML = `Pedido <strong>${order.id}</strong> • <strong>${U.money(order.total)}</strong>`;
            clearInterval(timer);
            return;
        }
        const left = new Date(pix.pixExpiraEm) - new Date();
        if (left <= 0) {
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

    // "Efetuar pagamento" sincroniza o status com o Mercado Pago
    payBtn.addEventListener('click', async function () {
        payBtn.disabled = true;
        const proc = UI.processing('Confirmando pagamento', 'Aguardando a confirmação do Pix...');
        const payment = await S.syncPayment(order.id);
        proc.close();
        if (payment && (payment.status === 'APROVADO' || payment.statusPedido === 'PAGO')) {
            clearInterval(timer);
            location.href = R.confirmation + '?pedido=' + order.id;
            return;
        }
        payBtn.disabled = false;
        UI.toast('O pagamento ainda não foi confirmado. Aguarde alguns instantes e tente novamente.', { type: 'info', title: 'Pix em processamento' });
    });
});
