/* Tela: cadastrarprodutos.html (cadastro e edição de produto) */
GlowUp.ready(function () {
    const { utils: U, store: S, ui: UI, config: C } = GlowUp;
    const R = C.routes;

    const form = U.$('.modal-card form');
    form.noValidate = true;
    form.removeAttribute('onsubmit');
    UI.autoClear(form);

    const inputs = U.$$('.form-input', form);
    const [nome, marca, validade, custo, preco] = inputs;
    const estoque = U.$('input[type=number]', form);
    const selects = U.$$('.form-select', form);
    const tipoVenda = selects[0], modalidade = selects[1];
    const promoSwitch = U.$('.promo-row input[type=checkbox]');
    const title = U.$('.modal-title');
    const upload = U.$('.upload-box');
    const submit = U.$('.btn-submit', form);
    estoque.min = 0; estoque.step = 1;

    /* ---- máscaras ---- */
    UI.bindDateField(validade);
    U.bindMask(custo, 'money'); U.bindMask(preco, 'money');

    /* ---- campo de categoria (necessário para os filtros da vitrine) ---- */
    const catRow = document.createElement('div');
    catRow.className = 'form-grid';
    catRow.innerHTML = '<div class="form-group" style="grid-column: 1 / -1;"><label class="form-label">Categoria</label>' +
        '<select class="form-select" id="pf-categoria"><option value="">Selecione</option>' +
        ['Perfumaria feminina', 'Perfumaria masculina', 'Corpo e banho', 'Kits e presentes'].map(c => `<option>${c}</option>`).join('') + '</select></div>';
    modalidade.closest('.form-grid').parentNode.insertBefore(catRow, modalidade.closest('.form-grid'));
    const categoria = U.$('#pf-categoria', form);

    /* ---- campo de descrição do produto ---- */
    const descRow = document.createElement('div');
    descRow.className = 'form-grid';
    descRow.innerHTML = '<div class="form-group" style="grid-column: 1 / -1;"><label class="form-label">Descrição do produto</label>' +
        '<textarea class="form-input" id="pf-descricao" rows="3" maxlength="500" placeholder="Descreva o produto (aroma, benefícios, modo de uso...)" style="border-radius: 18px; resize: vertical;"></textarea></div>';
    categoria.closest('.form-grid').insertAdjacentElement('afterend', descRow);
    const descricao = U.$('#pf-descricao', form);

    /* ---- campos da promoção (aparecem ao ativar o interruptor) ---- */
    const promoRow = document.createElement('div');
    promoRow.className = 'form-grid';
    promoRow.style.display = 'none';
    promoRow.innerHTML = '<div class="form-group"><label class="form-label">Preço promocional</label><input type="text" class="form-input" placeholder="R$ 0,00" id="pf-promo-preco"></div>' +
        '<div class="form-group"><label class="form-label">Fim da promoção</label><input type="text" class="form-input" placeholder="dd / mm / aaaa" id="pf-promo-fim"></div>';
    U.$('.promo-row', form).insertAdjacentElement('afterend', promoRow);
    const promoPreco = U.$('#pf-promo-preco', form), promoFim = U.$('#pf-promo-fim', form);
    U.bindMask(promoPreco, 'money'); UI.bindDateField(promoFim);
    promoSwitch.addEventListener('change', () => { promoRow.style.display = promoSwitch.checked ? '' : 'none'; });

    /* ---- modalidade: sob encomenda dispensa estoque e validade ---- */
    function syncModalidade() {
        const pronta = modalidade.value === 'pronta-entrega';
        [estoque, validade].forEach(el => {
            el.disabled = !pronta;
            el.closest('.form-group').style.opacity = pronta ? '' : '0.5';
            if (!pronta) { el.value = ''; UI.clearError(el); }
        });
    }
    modalidade.addEventListener('change', syncModalidade);

    /* ---- upload de imagem (reduzida para caber no navegador) ---- */
    let imagem = null;
    const file = document.createElement('input');
    file.type = 'file'; file.accept = 'image/*'; file.hidden = true;
    upload.appendChild(file);
    upload.style.cursor = 'pointer';
    upload.setAttribute('tabindex', '0');
    const uploadLabelHtml = upload.innerHTML.replace(/<input[^>]*>/, '');
    upload.addEventListener('click', e => { if (e.target !== file) file.click(); });
    upload.addEventListener('keydown', e => { if (e.key === 'Enter') file.click(); });

    function showPreview(src) {
        upload.classList.add('has-preview');
        upload.innerHTML = `<img src="${src}" alt="Prévia do produto" class="upload-preview">`;
        upload.appendChild(file);
    }

    file.addEventListener('change', function () {
        const f = file.files[0];
        if (!f) return;
        if (!/^image\//.test(f.type)) { UI.toast('Escolha um arquivo de imagem (JPG, PNG ou WEBP).', { type: 'error', title: 'Arquivo inválido' }); return; }
        const reader = new FileReader();
        reader.onload = function () {
            const img = new Image();
            img.onload = function () {
                const max = 720, ratio = Math.min(1, max / Math.max(img.width, img.height));
                const cv = document.createElement('canvas');
                cv.width = Math.round(img.width * ratio); cv.height = Math.round(img.height * ratio);
                cv.getContext('2d').drawImage(img, 0, 0, cv.width, cv.height);
                imagem = cv.toDataURL('image/jpeg', 0.82);
                showPreview(imagem);
            };
            img.onerror = () => UI.toast('Não conseguimos ler essa imagem.', { type: 'error' });
            img.src = reader.result;
        };
        reader.readAsDataURL(f);
    });

    /* ---- edição ---- */
    const editId = U.query('id');
    let editing = null;
    if (editId) {
        editing = S.getProduct(editId);
        if (!editing) { UI.flash('Produto não encontrado.', { type: 'error' }); location.replace(R.products); return; }
        title.textContent = 'Editar produto';
        nome.value = editing.nome; marca.value = editing.marca; categoria.value = editing.categoria;
        descricao.value = editing.descricao || '';
        custo.value = U.mask.money(String(Math.round(editing.custo * 100)));
        preco.value = U.mask.money(String(Math.round(editing.precoBase * 100)));
        tipoVenda.value = editing.tipoVenda;
        modalidade.value = editing.modalidade === 'PRONTA_ENTREGA' ? 'pronta-entrega' : 'sob-encomenda';
        if (editing.estoque !== null) estoque.value = editing.estoque;
        if (editing.validade) validade.value = U.formatDateBR(editing.validade + 'T12:00:00');
        if (editing.precoPromocional) {
            promoSwitch.checked = true; promoRow.style.display = '';
            promoPreco.value = U.mask.money(String(Math.round(editing.precoPromocional * 100)));
            promoFim.value = U.formatDateBR(editing.dataFimPromocao + 'T12:00:00');
        }
        showPreview(editing.imagem);
    }
    syncModalidade();

    /* ---- fechar ---- */
    const close = () => { location.href = R.products; };
    U.$('.modal-close').addEventListener('click', close);
    document.addEventListener('keydown', e => { if (e.key === 'Escape' && !document.querySelector('.gu-overlay')) close(); });

    /* ---- salvar ---- */
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        UI.clearErrors(form);
        const data = {
            id: editing ? editing.id : null,
            nome: nome.value, marca: marca.value, categoria: categoria.value, descricao: descricao.value,
            custo: U.parseMoney(custo.value), precoBase: U.parseMoney(preco.value),
            tipoVenda: tipoVenda.value,
            modalidade: modalidade.value === 'pronta-entrega' ? 'PRONTA_ENTREGA' : 'SOB_ENCOMENDA',
            estoque: estoque.value === '' ? null : Number(estoque.value),
            validade: validade.value,
            promoAtiva: promoSwitch.checked,
            precoPromocional: U.parseMoney(promoPreco.value), dataFimPromocao: promoFim.value,
            imagem
        };
        const res = S.saveProduct(data);
        if (!res.ok) {
            if (res.message) { UI.toast(res.message, { type: 'error', title: 'Não foi possível salvar' }); return; }
            const map = { nome, marca, categoria, descricao, custo, precoBase: preco, tipoVenda, estoque, validade, precoPromocional: promoPreco, dataFimPromocao: promoFim };
            Object.keys(res.errors).forEach(k => map[k] && UI.setError(map[k], res.errors[k]));
            UI.focusFirstError(form);
            UI.toast('Confira os campos destacados.', { type: 'warning', title: 'Quase lá' });
            return;
        }
        if (res.product.precoBase < res.product.custo) UI.flash('Atenção: o preço base está abaixo do custo de fábrica.', { type: 'warning', title: 'Produto salvo' });
        else UI.flash(res.isNew ? 'O produto já aparece na vitrine.' : 'As alterações foram salvas.', { type: 'success', title: res.isNew ? 'Produto cadastrado' : 'Produto atualizado' });
        location.href = R.products;
    });
});