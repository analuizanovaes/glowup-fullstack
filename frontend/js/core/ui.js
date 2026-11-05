/* GlowUp - avisos bonitos (no lugar de alert/confirm do navegador), janelas e ajudas de formulário. */
(function (global) {
    'use strict';

    const GlowUp = (global.GlowUp = global.GlowUp || {});
    const U = GlowUp.utils;
    const UI = (GlowUp.ui = {});
    const esc = U.escapeHtml;

    /* ---------- ícones ---------- */
    const svg = (inner, extra) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" ${extra || ''}>${inner}</svg>`;
    UI.icons = {
        success: svg('<path d="M20 6L9 17l-5-5"/>'),
        error: svg('<circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>'),
        warning: svg('<path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>'),
        info: svg('<circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>'),
        question: svg('<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 015.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/>'),
        trash: svg('<polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>'),
        logout: svg('<path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>'),
        cart: svg('<circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"/>'),
        lock: svg('<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/>'),
        heart: svg('<path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z"/>'),
        box: svg('<path d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>')
    };

    /* ---------- Toasts ---------- */
    let stack;
    function getStack() {
        if (!stack || !document.body.contains(stack)) {
            stack = document.createElement('div');
            stack.className = 'gu-toast-stack';
            stack.setAttribute('aria-live', 'polite');
            document.body.appendChild(stack);
        }
        return stack;
    }

    /**
     * UI.toast('Mensagem', { type: 'success|error|warning|info', title, duration, actions: [{label, onClick, primary}] })
     */
    UI.toast = function (message, opts) {
        opts = opts || {};
        const type = opts.type || 'info';
        const duration = opts.duration === undefined ? (opts.actions ? 12000 : 4200) : opts.duration;
        const titles = { success: 'Tudo certo', error: 'Ops!', warning: 'Atenção', info: 'Aviso' };
        const title = opts.title === undefined ? titles[type] : opts.title;

        const el = document.createElement('div');
        el.className = `gu-toast gu-toast--${type}`;
        el.setAttribute('role', type === 'error' ? 'alert' : 'status');
        el.innerHTML =
            `<div class="gu-toast-icon">${UI.icons[type] || UI.icons.info}</div>` +
            `<div class="gu-toast-body">` +
            (title ? `<div class="gu-toast-title">${esc(title)}</div>` : '') +
            `<div class="gu-toast-text">${esc(message)}</div>` +
            `</div>` +
            `<button type="button" class="gu-toast-close" aria-label="Fechar">&times;</button>` +
            (duration ? `<span class="gu-toast-progress" style="animation-duration:${duration}ms"></span>` : '');

        if (opts.actions && opts.actions.length) {
            const box = document.createElement('div');
            box.className = 'gu-toast-actions';
            opts.actions.forEach(a => {
                const b = document.createElement('button');
                b.type = 'button';
                b.className = 'gu-btn gu-btn--sm' + (a.primary === false ? '' : ' gu-btn--primary');
                b.textContent = a.label;
                b.addEventListener('click', () => { close(); if (a.onClick) a.onClick(); });
                box.appendChild(b);
            });
            el.querySelector('.gu-toast-body').appendChild(box);
        }

        let timer;
        function close() {
            clearTimeout(timer);
            el.classList.remove('is-visible');
            el.classList.add('is-leaving');
            setTimeout(() => el.remove(), 300);
        }
        el.querySelector('.gu-toast-close').addEventListener('click', close);
        getStack().appendChild(el);
        requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('is-visible')));
        if (duration) timer = setTimeout(close, duration);
        return { close };
    };

    ['success', 'error', 'warning', 'info'].forEach(type => {
        UI[type] = (msg, opts) => UI.toast(msg, Object.assign({ type }, opts));
    });

    /** Guarda um aviso para ser exibido na próxima página (ex.: depois de um redirecionamento). */
    UI.flash = function (message, opts) {
        try { sessionStorage.setItem('glowup:flash', JSON.stringify({ message, opts: opts || {} })); } catch (e) { /* ignore */ }
    };
    UI.showFlash = function () {
        try {
            const raw = sessionStorage.getItem('glowup:flash');
            if (!raw) return;
            sessionStorage.removeItem('glowup:flash');
            const f = JSON.parse(raw);
            setTimeout(() => UI.toast(f.message, f.opts), 250);
        } catch (e) { /* ignore */ }
    };

    /* ---------- Janelas ---------- */
    let openCount = 0;
    /**
     * UI.modal({ title, icon, iconClass, text, html, node, wide, actions: [{label, value, primary, danger}], dismissible })
     * Retorna Promise com o valor do botão clicado (ou null se fechado).
     */
    UI.modal = function (opts) {
        opts = opts || {};
        return new Promise(resolve => {
            const overlay = document.createElement('div');
            overlay.className = 'gu-overlay';
            const dialog = document.createElement('div');
            dialog.className = 'gu-dialog' + (opts.wide ? ' gu-dialog--wide' : '');
            dialog.setAttribute('role', 'dialog');
            dialog.setAttribute('aria-modal', 'true');

            let html = '';
            if (opts.dismissible !== false) html += '<button type="button" class="gu-dialog-close" aria-label="Fechar">&times;</button>';
            if (opts.icon) html += `<div class="gu-dialog-icon ${opts.iconClass || ''}">${opts.icon}</div>`;
            if (opts.title) html += `<h2 class="gu-dialog-title">${esc(opts.title)}</h2>`;
            if (opts.text) html += `<p class="gu-dialog-text">${opts.textIsHtml ? opts.text : esc(opts.text)}</p>`;
            html += '<div class="gu-dialog-body"></div>';
            if (opts.actions && opts.actions.length) html += '<div class="gu-dialog-actions"></div>';
            dialog.innerHTML = html;

            const body = dialog.querySelector('.gu-dialog-body');
            if (opts.html) body.innerHTML = opts.html; else if (opts.node) body.appendChild(opts.node); else body.remove();

            let finished = false;
            const api = {
                dialog,
                close(value) {
                    if (finished) return;
                    finished = true;
                    document.removeEventListener('keydown', onKey, true);
                    overlay.classList.remove('is-visible');
                    setTimeout(() => {
                        overlay.remove();
                        if (--openCount <= 0) { openCount = 0; document.body.style.overflow = ''; }
                        if (previous && previous.focus) previous.focus();
                        resolve(value === undefined ? null : value);
                    }, 220);
                }
            };

            const actionsBox = dialog.querySelector('.gu-dialog-actions');
            (opts.actions || []).forEach(a => {
                const b = document.createElement('button');
                b.type = 'button';
                b.className = 'gu-btn' + (a.danger ? ' gu-btn--danger' : a.primary ? ' gu-btn--primary' : '');
                b.textContent = a.label;
                b.addEventListener('click', async () => {
                    if (a.onClick) {
                        const res = await a.onClick(api, b);
                        if (res === false) return;
                    }
                    api.close(a.value === undefined ? true : a.value);
                });
                if (a.autofocus) b.dataset.autofocus = '1';
                actionsBox.appendChild(b);
            });

            const closer = dialog.querySelector('.gu-dialog-close');
            if (closer) closer.addEventListener('click', () => api.close(null));
            if (opts.dismissible !== false) {
                overlay.addEventListener('mousedown', e => { if (e.target === overlay) api.close(null); });
            }

            const previous = document.activeElement;
            function onKey(e) {
                if (!overlay.isConnected || overlay !== lastOverlay()) return;
                if (e.key === 'Escape' && opts.dismissible !== false) { e.stopPropagation(); api.close(null); }
                if (e.key === 'Tab') {
                    const f = U.$$('button, input, select, textarea, a[href]', dialog).filter(x => !x.disabled && x.offsetParent !== null);
                    if (!f.length) return;
                    const first = f[0], last = f[f.length - 1];
                    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
                    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
                }
            }
            const lastOverlay = () => { const all = document.querySelectorAll('.gu-overlay'); return all[all.length - 1]; };
            document.addEventListener('keydown', onKey, true);

            overlay.appendChild(dialog);
            document.body.appendChild(overlay);
            openCount++;
            document.body.style.overflow = 'hidden';
            requestAnimationFrame(() => requestAnimationFrame(() => {
                overlay.classList.add('is-visible');
                const af = dialog.querySelector('[data-autofocus]') || dialog.querySelector('input:not([type=hidden]), select, textarea');
                if (af) af.focus();
            }));

            if (opts.onOpen) opts.onOpen(api);
        });
    };

    UI.alert = function (message, opts) {
        opts = opts || {};
        return UI.modal({
            title: opts.title || 'Aviso',
            text: message,
            icon: opts.icon || UI.icons.info,
            iconClass: opts.iconClass,
            actions: [{ label: opts.okText || 'Entendi', primary: true, value: true, autofocus: true }]
        });
    };

    UI.confirm = function (opts) {
        opts = opts || {};
        return UI.modal({
            title: opts.title || 'Tem certeza?',
            text: opts.message,
            textIsHtml: opts.messageIsHtml,
            icon: opts.icon || (opts.danger ? UI.icons.trash : UI.icons.question),
            iconClass: opts.danger ? 'is-danger' : '',
            actions: [
                { label: opts.cancelText || 'Cancelar', value: false },
                { label: opts.confirmText || 'Confirmar', value: true, primary: !opts.danger, danger: !!opts.danger, autofocus: true }
            ]
        }).then(v => v === true);
    };

    /** Janela de texto legal. type: 'terms' | 'returns' */
    UI.legal = function (type) {
        const doc = GlowUp.legal[type];
        if (!doc) return Promise.resolve();
        return UI.modal({ title: doc.title, html: `<div class="gu-legal">${doc.html}</div>`, wide: true, actions: [{ label: 'Fechar', primary: true }] });
    };

    /** Overlay de "processando" (pagamento etc.). Retorna { close }. */
    UI.processing = function (title, text) {
        let handle;
        const bloom = `<img class="gu-bloom" src="assets/img/logo-flor.svg" alt="">`;
        UI.modal({
            dismissible: false,
            html: `<div class="gu-processing">${bloom}<h2 class="gu-dialog-title">${esc(title || 'Processando...')}</h2><p class="gu-dialog-text">${esc(text || 'Aguarde um instante.')}</p></div>`,
            onOpen: api => { handle = api; }
        });
        return { close: () => { const t = setInterval(() => { if (handle) { handle.close(); clearInterval(t); } }, 30); } };
    };

    /* ---------- Formulários ---------- */
    /** Mostra erro embaixo do campo. Retorna false (útil para `valid = UI.setError(...) && valid`). */
    UI.setError = function (input, message) {
        if (!input) return false;
        UI.clearError(input);
        input.classList.add('has-error');
        const host = input.closest('.input-icon-wrapper, .input-wrapper, .date-input-wrapper, .phone-item, .phone-extra') || input;
        const err = document.createElement('span');
        err.className = 'gu-field-error';
        err.dataset.for = input.id || input.name || '';
        err.textContent = message;
        host.insertAdjacentElement('afterend', err);
        input.setAttribute('aria-invalid', 'true');
        return false;
    };

    UI.clearError = function (input) {
        if (!input) return;
        input.classList.remove('has-error');
        input.removeAttribute('aria-invalid');
        const host = input.closest('.input-icon-wrapper, .input-wrapper, .date-input-wrapper, .phone-item, .phone-extra') || input;
        const next = host.nextElementSibling;
        if (next && next.classList.contains('gu-field-error')) next.remove();
    };

    UI.clearErrors = function (form) {
        U.$$('.has-error', form).forEach(UI.clearError);
        U.$$('.gu-field-error', form).forEach(e => e.remove());
    };

    /** Limpa o erro assim que a pessoa volta a digitar. */
    UI.autoClear = function (form) {
        form.addEventListener('input', e => { if (e.target.classList && e.target.classList.contains('has-error')) UI.clearError(e.target); });
        form.addEventListener('change', e => { if (e.target.classList && e.target.classList.contains('has-error')) UI.clearError(e.target); });
    };

    UI.focusFirstError = function (form) {
        const el = form.querySelector('.has-error');
        if (el) { el.focus(); el.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
    };

    UI.setLoading = function (btn, loading, text) {
        if (!btn) return;
        if (loading) {
            btn.dataset.originalHtml = btn.innerHTML;
            btn.disabled = true;
            btn.innerHTML = `<span class="gu-spinner"></span>${esc(text || 'Aguarde...')}`;
        } else {
            btn.disabled = false;
            if (btn.dataset.originalHtml !== undefined) btn.innerHTML = btn.dataset.originalHtml;
        }
    };

    /** Olho de mostrar/ocultar senha. `trigger` = elemento clicável, `input` = campo. */
    UI.bindPasswordToggle = function (trigger, input) {
        if (!trigger || !input) return;
        trigger.style.cursor = 'pointer';
        trigger.setAttribute('role', 'button');
        trigger.setAttribute('tabindex', '0');
        trigger.setAttribute('aria-label', 'Mostrar ou ocultar senha');
        const toggle = () => {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            trigger.style.color = show ? '#E85D9E' : '';
        };
        trigger.addEventListener('click', toggle);
        trigger.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); } });
    };

    /** Lista de regras da senha (✓ / ✕) em tempo real. */
    UI.bindPasswordRules = function (input, listEl) {
        if (!input || !listEl) return;
        const map = { length: 'rule-length', upper: 'rule-upper', lower: 'rule-lower', number: 'rule-number', special: 'rule-special' };
        const update = () => {
            const value = input.value;
            const r = U.passwordRules(value);
            Object.keys(map).forEach(k => {
                const li = listEl.querySelector('#' + map[k]);
                if (!li) return;
                li.classList.toggle('valid', r[k]);
                li.classList.toggle('invalid', !r[k] && value.length > 0);
            });
        };
        input.addEventListener('input', update);
        update();
    };

    /** Máscara de data + ícone de calendário abrindo o seletor nativo. */
    UI.bindDateField = function (input) {
        if (!input) return;
        U.bindMask(input, 'date');
        input.setAttribute('inputmode', 'numeric');
        input.setAttribute('maxlength', '10');
        const wrapper = input.closest('.input-icon-wrapper, .date-input-wrapper');
        const icon = wrapper && wrapper.querySelector('svg');
        if (!icon) return;
        icon.style.cursor = 'pointer';
        icon.addEventListener('click', () => {
            const picker = document.createElement('input');
            picker.type = 'date';
            picker.max = U.toISODate(new Date());
            const cur = U.parseDateBR(input.value);
            if (cur) picker.value = U.toISODate(cur);
            picker.style.cssText = 'position:fixed;opacity:0;pointer-events:none;top:0;left:0;';
            document.body.appendChild(picker);
            picker.addEventListener('change', () => {
                if (picker.value) {
                    const [y, m, d] = picker.value.split('-');
                    input.value = `${d}/${m}/${y}`;
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                }
                picker.remove();
            });
            picker.addEventListener('blur', () => setTimeout(() => picker.remove(), 200));
            if (picker.showPicker) { try { picker.showPicker(); } catch (e) { input.focus(); } } else { input.focus(); }
        });
    };

    /** Seletor de telefones múltiplos (linha com input + botão remover). */
    UI.phoneRowHtml = function (value) {
        return `<div class="phone-item"><input type="text" class="form-control phone-input" placeholder="(00) 00000-0000" value="${esc(value || '')}" inputmode="tel" autocomplete="tel">` +
            `<button type="button" class="btn-icon-trash" aria-label="Excluir telefone">${UI.icons.trash.replace('<svg ', '<svg width="16" height="16" ')}</button></div>`;
    };
})(window);
