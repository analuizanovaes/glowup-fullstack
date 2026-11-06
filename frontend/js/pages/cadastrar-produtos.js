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

    /* ---- KITS (RF03 / DC02) ----
       Tipo de venda "Kit" libera o campo "Tipo de kit":
         · Kit pronto  (FABRICA): produto normal, com estoque próprio (ou sob encomenda).
         · Kit manual  (MANUAL) : sem estoque próprio; a administradora escolhe os produtos avulsos
                                  que o compõem e cada venda baixa o estoque desses itens. */
    const esc = U.escapeHtml;

    const kitTypeRow = document.createElement('div');
    kitTypeRow.className = 'form-grid';
    kitTypeRow.style.display = 'none';
    kitTypeRow.innerHTML = '<div class="form-group" style="grid-column: 1 / -1;"><label class="form-label" for="pf-tipo-kit">Tipo de kit</label>' +
        '<select class="form-select" id="pf-tipo-kit">' +
        '<option value="FABRICA">Kit pronto (de fábrica)</option>' +
        '<option value="MANUAL">Kit manual (montado por mim)</option>' +
        '</select><small class="kit-help" id="pf-tipo-kit-help"></small></div>';
    tipoVenda.closest('.form-grid').insertAdjacentElement('afterend', kitTypeRow);
    const tipoKit = U.$('#pf-tipo-kit', form);

    const builder = document.createElement('div');
    builder.className = 'kit-builder';
    builder.style.display = 'none';
    builder.innerHTML =
        '<span class="form-label">Composição do kit</span>' +
        '<p class="kit-help">Escolha os produtos avulsos (pronta-entrega) que formam este kit. A cada venda, o estoque de cada item é baixado.</p>' +
        '<div class="kit-add-row">' +
            '<select class="form-select" id="pf-kit-produto" aria-label="Produto do kit"></select>' +
            '<input type="number" class="form-input" id="pf-kit-qtd" min="1" step="1" value="1" aria-label="Quantidade do item">' +
            '<button type="button" class="btn-kit-add">Adicionar</button>' +
        '</div>' +
        '<ul class="kit-items" aria-live="polite"></ul>' +
        '<div class="kit-summary"></div>';
    kitTypeRow.insertAdjacentElement('afterend', builder);
    const kitSelect = U.$('#pf-kit-produto', builder), kitQtd = U.$('#pf-kit-qtd', builder);
    const kitList = U.$('.kit-items', builder), kitSummary = U.$('.kit-summary', builder);

    let kitItems = []; // [{ produtoId, qtd }]
    const isKit = () => tipoVenda.value === 'kit';
    const isManual = () => isKit() && tipoKit.value === 'MANUAL';

    function fillKitOptions() {
        const used = kitItems.map(i => i.produtoId);
        const opts = S.listProducts()
            .filter(p => p.modalidade === 'PRONTA_ENTREGA' && p.tipoVenda !== 'kit' && (!editing || p.id !== editing.id) && used.indexOf(p.id) < 0)
            .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
        kitSelect.innerHTML = '<option value="">Escolha um produto avulso</option>' +
            opts.map(p => `<option value="${p.id}">${esc(p.nome)} (${S.available(p)} disp.)</option>`).join('');
    }

    function renderKit() {
        fillKitOptions();
        kitList.innerHTML = kitItems.length ? kitItems.map((c, i) => {
            const p = S.getProduct(c.produtoId);
            const nomeItem = p ? p.nome : 'Produto removido';
            return `<li class="kit-item" data-i="${i}">
                <div class="kit-item-name">${esc(nomeItem)}<small>${p ? U.money(p.precoBase) + ' · ' + S.available(p) + ' disp.' : 'indisponível'}</small></div>
                <input type="number" class="form-input kit-item-qtd" min="1" step="1" value="${c.qtd}" aria-label="Quantidade de ${esc(nomeItem)}">
                <button type="button" class="kit-item-remove" aria-label="Remover ${esc(nomeItem)} do kit">&times;</button>
            </li>`;
        }).join('') : '<li class="kit-empty">Nenhum item adicionado ainda.</li>';

        const sum = S.kitSummary(kitItems);
        kitSummary.innerHTML = kitItems.length
            ? `<span>Custo dos itens: <strong>${U.money(sum.custo)}</strong></span>` +
              `<span>Preços avulsos somados: <strong>${U.money(sum.precoAvulso)}</strong></span>` +
              `<span>Kits que dá para vender agora: <strong>${sum.disponivel}</strong></span>`
            : '';
        // o custo de fábrica do kit manual é sempre a soma dos custos dos itens
        if (isManual()) custo.value = kitItems.length ? U.mask.money(String(Math.round(sum.custo * 100))) : '';
    }

    builder.addEventListener('click', function (e) {
        if (e.target.closest('.btn-kit-add')) {
            const id = Number(kitSelect.value), qtd = Math.floor(Number(kitQtd.value));
            if (!id) { UI.toast('Escolha um produto para adicionar ao kit.', { type: 'warning', title: 'Kit' }); return; }
            if (!(qtd >= 1)) { UI.toast('Informe uma quantidade válida (mínimo 1).', { type: 'warning', title: 'Kit' }); return; }
            kitItems.push({ produtoId: id, qtd });
            kitQtd.value = 1;
            UI.clearError(builder);
            renderKit();
            return;
        }
        const rm = e.target.closest('.kit-item-remove');
        if (rm) { kitItems.splice(Number(rm.closest('.kit-item').dataset.i), 1); renderKit(); }
    });
    builder.addEventListener('change', function (e) {
        if (!e.target.classList.contains('kit-item-qtd')) return;
        const i = Number(e.target.closest('.kit-item').dataset.i);
        kitItems[i].qtd = Math.max(1, Math.floor(Number(e.target.value)) || 1);
        renderKit();
    });

    function syncKit() {
        const kit = isKit(), manual = isManual();
        kitTypeRow.style.display = kit ? '' : 'none';
        builder.style.display = manual ? '' : 'none';
        // kit manual: não há estoque/validade/modalidade próprios (vêm dos itens, sempre pronta-entrega)
        modalidade.closest('.form-grid').style.display = manual ? 'none' : '';
        if (manual) modalidade.value = 'pronta-entrega';
        U.$('#pf-tipo-kit-help', form).textContent = manual
            ? 'Montado por você com produtos avulsos: não tem estoque próprio, cada venda baixa o estoque dos itens.'
            : 'Kit já fechado de fábrica: tem estoque próprio (ou é sob encomenda), como qualquer produto.';
        custo.readOnly = manual;
        custo.closest('.form-group').style.opacity = manual ? '0.7' : '';
        if (kit && !categoria.value) categoria.value = 'Kits e presentes';
        syncModalidade();
        renderKit();
    }
    tipoVenda.addEventListener('change', syncKit);
    tipoKit.addEventListener('change', syncKit);

    /* ---- modalidade: sob encomenda dispensa estoque e validade ---- */
    function syncModalidade() {
        const pronta = modalidade.value === 'pronta-entrega' && !isManual();
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
        if (editing.tipoVenda === 'kit') {
            tipoKit.value = S.isManualKit(editing) ? 'MANUAL' : 'FABRICA';
            kitItems = (editing.itensKit || []).map(c => ({ produtoId: c.produtoId, qtd: c.qtd }));
        }
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
    /* ---- MODO: produto avulso x kit ----
       Produto e kit têm botões separados na lista ("+ Novo produto" / "+ Adicionar kit").
       Aqui a mesma tela se adapta: no kit, o tipo de venda é fixo e a escolha
       "Kit pronto (de fábrica)" ou "Kit manual" aparece logo no topo do formulário. */
    const kitMode = editing ? editing.tipoVenda === 'kit' : U.query('tipo') === 'kit';
    const tipoVendaGroup = tipoVenda.closest('.form-group');
    tipoVendaGroup.style.display = 'none';
    tipoVenda.value = kitMode ? 'kit' : (editing && editing.tipoVenda ? editing.tipoVenda : 'varejo');
    if (kitMode) {
        // o seletor de tipo de kit ocupa o lugar do antigo "Tipo de Venda"
        const kitGroup = kitTypeRow.firstElementChild;
        kitGroup.style.gridColumn = '';
        tipoVendaGroup.insertAdjacentElement('afterend', kitGroup);
        kitTypeRow.remove();
        // kit só pode ser da categoria "Kits e presentes"
        Array.from(categoria.options).forEach(o => { if (o.value !== 'Kits e presentes') o.remove(); });
        categoria.value = 'Kits e presentes';
        title.textContent = editing ? 'Editar kit' : 'Cadastrar kit';
        nome.placeholder = 'Nome do kit';
        submit.textContent = 'Salvar kit';
        const upTxt = Array.from(upload.childNodes).find(n => n.nodeType === 3 && /Upload/i.test(n.textContent));
        if (upTxt) upTxt.textContent = '\n            Upload da foto do kit\n          ';
        document.title = 'GlowUp - ' + title.textContent;
    } else {
        // produto avulso: sem nenhuma opção de kit
        // a categoria "Kits e presentes" só faz sentido no cadastro de kit
        if (!editing || editing.categoria !== 'Kits e presentes') {
            const opt = Array.from(categoria.options).find(o => o.value === 'Kits e presentes');
            if (opt) opt.remove();
        }
        preco.closest('.form-group').style.gridColumn = '1 / -1';
        document.title = 'GlowUp - ' + title.textContent;
    }
    syncKit();

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
            tipoKit: tipoKit.value, itensKit: kitItems,
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
            const map = { nome, marca, categoria, descricao, custo, precoBase: preco, tipoVenda, modalidade, itensKit: builder, estoque, validade, precoPromocional: promoPreco, dataFimPromocao: promoFim };
            Object.keys(res.errors).forEach(k => map[k] && UI.setError(map[k], res.errors[k]));
            UI.focusFirstError(form);
            UI.toast('Confira os campos destacados.', { type: 'warning', title: 'Quase lá' });
            return;
        }
        if (res.product.precoBase < res.product.custo) UI.flash('Atenção: o preço base está abaixo do custo de fábrica.', { type: 'warning', title: 'Produto salvo' });
        else UI.flash(res.isNew ? (kitMode ? 'O kit já aparece na vitrine.' : 'O produto já aparece na vitrine.') : 'As alterações foram salvas.', { type: 'success', title: res.isNew ? (kitMode ? 'Kit cadastrado' : 'Produto cadastrado') : (kitMode ? 'Kit atualizado' : 'Produto atualizado') });
        location.href = R.products;
    });
});