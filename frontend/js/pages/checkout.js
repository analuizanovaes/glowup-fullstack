/* Tela: chekout.html */
GlowUp.ready(async function () {
    const { utils: U, store: S, ui: UI, layout: L, config: C } = GlowUp;
    const R = C.routes;
    const esc = U.escapeHtml;

    let cartError = null;
    for (let attempt = 1; attempt <= 4; attempt++) {
        try { await S.cart(); cartError = null; break; }
        catch (e) {
            cartError = e;
            const retryable = !e || e.status == null || e.status === 408 || e.status === 425 || e.status === 502 || e.status === 503 || e.status === 504 || e.name === 'AbortError' || e.name === 'TypeError';
            if (!retryable || attempt === 4) break;
            const wake = UI.processing('Acordando o servidor', 'Estamos iniciando nosso servidor seguro para carregar sua sacola...');
            await new Promise((resolve) => setTimeout(resolve, 12000));
            wake.close();
        }
    }
    if (cartError) {
        UI.toast('Não foi possível carregar sua sacola. Verifique sua conexão e tente novamente.', { type: 'error', title: 'Sacola indisponível' });
        location.href = R.cart;
        return;
    }
    if (!S.cartTotals().lines.length) {
        UI.flash('Sua sacola está vazia. Escolha alguns produtos antes de finalizar.', { type: 'info', title: 'Sacola vazia' });
        location.href = R.cart;
        return;
    }

    /* ---- elementos ---- */
    const deliveryCards = U.$$('.delivery-options .option-card');
    const [homeCard, pickupCard] = deliveryCards;
    const [pixCard, cardCard] = U.$$('.payment-options .option-card');
    const addressBox = U.$('.form-box');
    const cardBox = U.$('#card-form-box');
    const addr = U.$$('.form-control', addressBox);
    const [cep, numero, rua, bairro, complemento, cidade] = addr;
    const cepNotice = U.$('.notice-text', addressBox);
    const cepNoticeOriginal = cepNotice.textContent;
    const card = U.$$('.form-control', cardBox);
    const [cNumber, cName, cExpiry, cCvv, cCpf] = card;
    const installments = U.$('.select-control', cardBox);
    const summary = U.$('.summary-card');
    const sumRows = U.$$('.summary-row', summary);
    const freteEl = sumRows[1].lastElementChild;
    const subtotalEl = sumRows[0].lastElementChild;
    const totalRow = U.$('.total-row', summary);
    const totalEl = totalRow.lastElementChild;
    const couponInput = U.$('.coupon-input', summary);
    const couponBtn = U.$('.btn-apply', summary);
    const submitBtn = U.$('.btn-submit-order');

    const state = { entrega: 'home', metodo: 'PIX', cepOk: false, bloqueado: false };
    let discountRow = null, interestRow = null;

    /* ---- máscaras ---- */
    U.bindMask(cep, 'cep'); U.bindMask(cNumber, 'card'); U.bindMask(cExpiry, 'expiry');
    U.bindMask(cCvv, 'cvv'); U.bindMask(cCpf, 'cpf');
    cep.setAttribute('inputmode', 'numeric'); cNumber.setAttribute('inputmode', 'numeric');
    cNumber.autocomplete = 'cc-number'; cName.autocomplete = 'cc-name'; cExpiry.autocomplete = 'cc-exp'; cCvv.autocomplete = 'cc-csc';
    UI.autoClear(document.body);

    /* ---- seleção de cartões de opção (entrega / pagamento) ---- */
    function setSelected(group, el) { group.forEach(c => c.classList.toggle('selected', c === el)); }

    function selectDelivery(type) {
        if (type === 'home' && state.bloqueado) {
            UI.toast('Esse endereço não tem entrega em casa. Escolha Retirada Grátis.', { type: 'warning', title: 'Entrega indisponível' });
            return;
        }
        state.entrega = type;
        setSelected(deliveryCards, type === 'home' ? homeCard : pickupCard);
        addressBox.style.display = type === 'home' ? '' : 'none';
        refresh();
    }
    homeCard.addEventListener('click', () => selectDelivery('home'));
    pickupCard.addEventListener('click', () => selectDelivery('pickup'));

    function selectPayment(method) {
        state.metodo = method;
        setSelected([pixCard, cardCard], method === 'PIX' ? pixCard : cardCard);
        cardBox.classList.toggle('active', method === 'CARTAO');
        refresh();
    }
    pixCard.addEventListener('click', () => selectPayment('PIX'));
    cardCard.addEventListener('click', () => selectPayment('CARTAO'));

    /* ---- endereço / CEP (ViaCEP com contingência manual) ---- */
    function parseCity() {
        const parts = cidade.value.split('/');
        return { cidade: (parts[0] || '').trim(), uf: (parts[1] || '').trim().toUpperCase() };
    }

    function setBlocked(reason) {
        state.bloqueado = !!reason;
        homeCard.classList.toggle('is-disabled', !!reason);
        cepNotice.classList.toggle('is-error', !!reason);
        cepNotice.textContent = reason || cepNoticeOriginal;
    }

    let rejectedNow = false;

    /**
     * Endereço sem entrega em casa: mostra um aviso bem visível, apaga o que foi digitado e libera a troca,
     * para a pessoa informar outro endereço (ou seguir com a Retirada Grátis) em vez de ficar travada.
     */
    function rejectAddress(reason, opts) {
        rejectedNow = true;
        [cep, numero, rua, bairro, complemento, cidade].forEach(el => { el.value = ''; });
        UI.clearErrors(addressBox);
        setBlocked(null);
        selectDelivery(opts && opts.pickup ? 'pickup' : 'home');
        cepNotice.classList.add('is-error');
        cepNotice.textContent = 'O endereço anterior foi removido. Informe um endereço de Itapoã ou Paranoá (DF) para receber em casa.';
        UI.alert(`${reason} Os campos do endereço foram apagados: informe outro endereço ou escolha a Retirada Grátis no Local.`, {
            title: 'Esse endereço não tem entrega em casa',
            icon: UI.icons.warning,
            iconClass: 'is-danger',
            okText: 'Entendi'
        }).then(() => { if (state.entrega === 'home') cep.focus(); });
    }

    /** Avalia as regras de entrega com o que está digitado (sem forçar a troca de opção). */
    function evaluateAddress(silent) {
        const c = parseCity();
        if (!U.digits(cep.value).length && !bairro.value.trim()) { setBlocked(null); return true; }
        if (!bairro.value.trim()) { setBlocked(null); return true; }
        // sem cidade/UF ainda não dá para dizer que o endereço foi recusado (a pessoa pode estar no meio do preenchimento)
        if (!c.uf) { setBlocked(null); return true; }
        const res = S.checkDelivery({ pais: 'Brasil', uf: c.uf, bairro: bairro.value });
        setBlocked(res.allowed ? null : res.reason);
        if (!res.allowed && state.entrega === 'home' && !silent) {
            rejectAddress(res.reason);
        }
        return res.allowed;
    }

    async function lookupCep() {
        const digits = U.digits(cep.value);
        if (digits.length !== 8) return;
        cep.dataset.busy = '1';
        const ctrl = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), 4500);
        try {
            const r = await fetch(`https://viacep.com.br/ws/${digits}/json/`, { signal: ctrl.signal });
            const d = await r.json();
            if (d.erro || !d.logradouro && !d.bairro) throw new Error('empty');
            rua.value = d.logradouro || rua.value;
            bairro.value = d.bairro || bairro.value;
            cidade.value = `${d.localidade || 'Brasília'}/${d.uf || 'DF'}`;
            evaluateAddress(false);
            if (state.entrega === 'home') numero.focus();
        } catch (err) {
            // contingência (RNF03): libera digitação manual
            UI.toast('Não conseguimos consultar o CEP agora. Preencha o endereço manualmente.', { type: 'info', title: 'Busca de CEP indisponível' });
        } finally {
            clearTimeout(timer);
            delete cep.dataset.busy;
        }
    }
    cep.addEventListener('input', () => {
        if (!state.bloqueado && cepNotice.classList.contains('is-error')) { cepNotice.classList.remove('is-error'); cepNotice.textContent = cepNoticeOriginal; }
        if (U.digits(cep.value).length === 8) lookupCep();
    });
    [bairro, cidade].forEach(el => el.addEventListener('change', () => evaluateAddress(false)));
    [bairro, cidade].forEach(el => el.addEventListener('input', U.debounce(() => evaluateAddress(true), 250)));

    // endereço padrão do perfil
    const me = S.currentUser();
    const main = me && me.enderecos.find(a => a.principal);
    if (main) {
        cep.value = main.cep; numero.value = main.numero; rua.value = main.rua;
        bairro.value = main.bairro; complemento.value = main.complemento || ''; cidade.value = `${main.cidade}/${main.uf}`;
        evaluateAddress(true);
        if (state.bloqueado) rejectAddress(S.checkDelivery({ pais: 'Brasil', uf: parseCity().uf, bairro: bairro.value }).reason, { pickup: true });
    }

    /* ---- resumo e parcelas ---- */
    function addressPayload() {
        if (state.entrega !== 'home') return null;
        const c = parseCity();
        return { id: null, cep: cep.value, rua: rua.value.trim(), numero: numero.value.trim(), complemento: complemento.value.trim(), bairro: bairro.value.trim(), cidade: c.cidade, uf: c.uf, pais: 'Brasil' };
    }
    function currentInstallments() { return state.metodo === 'CARTAO' ? Number(installments.value || 1) : 1; }

    function buildInstallments(t) {
        const keep = installments.value || '1';
        const base = t.total;
        installments.innerHTML = [1, 2, 3].map(n => {
            const total = U.round2(base * (1 + (C.installmentInterest[n] || 0)));
            const label = n === 1 ? `1x de ${U.money(base)}` : `${n}x de ${U.money(total / n)} (total ${U.money(total)})`;
            return `<option value="${n}">${label}</option>`;
        }).join('');
        installments.value = keep;
    }

    async function refresh() {
        const t = await S.previewTotals(state.entrega, { metodo: state.metodo, parcelas: currentInstallments() }, addressPayload());
        buildInstallments(t);
        subtotalEl.textContent = U.money(t.subtotal);
        freteEl.textContent = t.frete ? U.money(t.frete) : 'Grátis';
        totalEl.textContent = U.money(t.total);

        if (discountRow) { discountRow.remove(); discountRow = null; }
        if (t.desconto) {
            discountRow = document.createElement('div');
            discountRow.className = 'summary-row';
            discountRow.innerHTML = `<span>Cupom ${esc(t.cupom)}</span><span style="color:#2e7d32;font-weight:700">- ${U.money(t.desconto)}</span>`;
            totalRow.parentNode.insertBefore(discountRow, totalRow);
            couponInput.value = t.cupom; couponInput.disabled = true; couponBtn.disabled = true;
        } else { couponInput.disabled = false; couponBtn.disabled = false; }

        if (interestRow) { interestRow.remove(); interestRow = null; }
        if (t.juros) {
            interestRow = document.createElement('div');
            interestRow.className = 'summary-row';
            interestRow.innerHTML = `<span>Juros do parcelamento</span><span>${U.money(t.juros)}</span>`;
            totalRow.parentNode.insertBefore(interestRow, totalRow);
        }
    }
    installments.addEventListener('change', refresh);

    async function applyCoupon() {
        if (couponInput.disabled) return;
        const res = await S.applyCoupon(couponInput.value);
        if (!res.ok) { UI.toast(res.message, { type: 'error', title: 'Cupom' }); return; }
        refresh();
        UI.toast(`Cupom ${res.cupom.codigo} aplicado: você economizou ${U.money(res.desconto)}.`, { type: 'success', title: 'Cupom aplicado' });
    }
    couponBtn.addEventListener('click', applyCoupon);
    couponInput.addEventListener('keydown', e => { if (e.key === 'Enter') applyCoupon(); });

    /* ---- confirmar pedido ---- */
    function validate() {
        let ok = true;
        const req = (el, msg) => { if (!el.value.trim()) ok = UI.setError(el, msg) && ok; };
        if (state.entrega === 'home') {
            if (U.digits(cep.value).length !== 8) ok = UI.setError(cep, 'Informe um CEP válido.') && ok;
            req(numero, 'Informe o número.'); req(rua, 'Informe a rua.'); req(bairro, 'Informe o bairro.'); req(cidade, 'Informe cidade/UF.');
            const c = parseCity();
            if (!c.uf) ok = UI.setError(cidade, 'Use o formato Cidade/UF (ex.: Brasília/DF).') && ok;
            if (ok) {
                const chk = S.checkDelivery({ pais: 'Brasil', uf: c.uf, bairro: bairro.value });
                if (!chk.allowed) { rejectAddress(chk.reason); return false; }
            }
        }
        if (state.metodo === 'CARTAO') {
            if (!U.isValidCard(cNumber.value)) ok = UI.setError(cNumber, 'Número de cartão inválido.') && ok;
            if (cName.value.trim().length < 3) ok = UI.setError(cName, 'Informe o nome do titular.') && ok;
            if (!U.isValidExpiry(cExpiry.value)) ok = UI.setError(cExpiry, 'Validade inválida.') && ok;
            if (U.digits(cCvv.value).length < 3) ok = UI.setError(cCvv, 'CVV inválido.') && ok;
            if (!U.isValidCPF(cCpf.value)) ok = UI.setError(cCpf, 'CPF inválido.') && ok;
        }
        return ok;
    }

    submitBtn.addEventListener('click', async function () {
        UI.clearErrors(document.body);
        rejectedNow = false;
        if (!validate()) {
            if (!rejectedNow) { UI.focusFirstError(document.body); UI.toast('Confira os campos destacados.', { type: 'warning', title: 'Quase lá' }); }
            return;
        }

        const c = parseCity();
        const payload = {
            entrega: { tipo: state.entrega, endereco: state.entrega === 'home' ? {
                cep: cep.value, numero: numero.value.trim(), rua: rua.value.trim(), bairro: bairro.value.trim(),
                complemento: complemento.value.trim(), cidade: c.cidade, uf: c.uf, pais: 'Brasil' } : null },
            // CPF e dados do cartão ficam só nesta tela (seriam enviados direto ao gateway) — nunca são salvos
            pagamento: { metodo: state.metodo, parcelas: currentInstallments() }
        };

        UI.setLoading(submitBtn, true, 'Confirmando...');
        let wakeProc = null;
        const res = await S.createOrder(payload, {
            onWaiting: (attempt) => {
                if (!wakeProc) wakeProc = UI.processing('Acordando o servidor', 'Estamos iniciando nosso servidor seguro. Isso pode levar alguns segundos na primeira vez do dia.');
                else wakeProc.setText(`Ainda acordando o servidor... tentativa ${attempt} de 4.`);
            }
        });
        if (wakeProc) wakeProc.close();
        if (!res.ok) {
            UI.setLoading(submitBtn, false);
            if (/aguardando pagamento/i.test(res.message || '')) {
                // Já existe um pedido pendente: leva a cliente direto para pagá-lo.
                const pend = (await S.listOrders().catch(() => [])).find(o => o.status === 'AGUARDANDO_PAGAMENTO');
                UI.toast(res.message, { type: 'info', title: 'Pedido pendente' });
                setTimeout(() => { location.href = pend ? R.pix + '?pedido=' + pend.id : R.myOrders; }, 1500);
                return;
            }
            if (res.field === 'cep') { rejectAddress(res.message); return; }
            UI.toast(res.message, { type: 'error', title: 'Não foi possível confirmar' });
            if (res.stock) setTimeout(() => { location.href = R.cart; }, 1800);
            return;
        }
        const order = res.order;

        if (state.metodo === 'PIX') {
            const payment = await S.startPayment({ orderId: order.id, metodo: 'PIX', parcelas: 1 });
            UI.setLoading(submitBtn, false);
            if (!payment.ok) {
                // O pedido já existe: a tela do Pix tenta gerar o pagamento de novo.
                UI.toast(payment.message, { type: 'warning', title: 'Pagamento não iniciado' });
                setTimeout(() => { location.href = R.pix + '?pedido=' + order.id; }, 1500);
                return;
            }
            if (payment.status === 'APROVADO' || payment.statusPedido === 'PAGO') {
                location.href = R.confirmation + '?pedido=' + order.id;
                return;
            }
            location.href = R.pix + '?pedido=' + order.id;
            return;
        }

        const token = await S.tokenizeCard({ number: cNumber.value, name: cName.value, expiry: cExpiry.value, cvv: cCvv.value, cpf: cCpf.value });
        UI.setLoading(submitBtn, false);
        if (!token.ok) { UI.toast(token.message, { type: 'error', title: 'Cartão não validado' }); return; }
        const payment = await S.startPayment({
            orderId: order.id,
            metodo: 'CARTAO',
            parcelas: currentInstallments(),
            tokenCartao: token.card?.id,
            bandeiraCartao: token.card?.payment_method_id,
            documento: U.digits(cCpf.value)
        });
        if (!payment.ok) { UI.toast(payment.message, { type: 'error', title: 'Pagamento recusado' }); return; }
        if (payment.status === 'APROVADO' || payment.statusPedido === 'PAGO') location.href = R.confirmation + '?pedido=' + order.id;
        else UI.alert('O pagamento foi processado, mas ainda não foi aprovado. Verifique o status do pedido.', { title: 'Pagamento em análise', icon: UI.icons.warning });
    });

    selectPayment('PIX');
    selectDelivery(state.entrega);
    L.refreshCart();
});
