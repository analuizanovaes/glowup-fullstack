/* GlowUp - funções utilitárias: formatação, máscaras e validações. */
(function (global) {
    'use strict';

    const GlowUp = (global.GlowUp = global.GlowUp || {});
    const U = (GlowUp.utils = {});

    /* ---------- DOM ---------- */
    U.$ = (sel, ctx) => (ctx || document).querySelector(sel);
    U.$$ = (sel, ctx) => Array.from((ctx || document).querySelectorAll(sel));

    U.escapeHtml = function (str) {
        return String(str == null ? '' : str).replace(/[&<>"']/g, c => (
            { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
        ));
    };

    U.debounce = function (fn, wait) {
        let t;
        return function () {
            const args = arguments, ctx = this;
            clearTimeout(t);
            t = setTimeout(() => fn.apply(ctx, args), wait || 200);
        };
    };

    U.query = (name) => new URLSearchParams(location.search).get(name);

    U.go = (url) => { location.href = url; };

    /* ---------- texto ---------- */
    U.normalize = (s) => String(s == null ? '' : s).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
    U.digits = (s) => String(s == null ? '' : s).replace(/\D/g, '');
    U.firstName = (full) => String(full || '').trim().split(/\s+/)[0] || '';

    /* ---------- dinheiro ---------- */
    const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    U.money = (n) => brl.format(Math.round((Number(n) || 0) * 100) / 100).replace(/\u00a0/g, ' ');
    U.round2 = (n) => Math.round((Number(n) || 0) * 100) / 100;

    /** "R$ 1.234,56" | "1234,5" -> 1234.56 */
    U.parseMoney = function (str) {
        if (typeof str === 'number') return str;
        const clean = String(str || '').replace(/[^\d,.-]/g, '');
        if (!clean) return NaN;
        const normalized = clean.includes(',') ? clean.replace(/\./g, '').replace(',', '.') : clean;
        return parseFloat(normalized);
    };

    /* ---------- datas ---------- */
    const pad = (n) => String(n).padStart(2, '0');
    U.pad = pad;
    const MONTHS_SHORT = ['jan.', 'fev.', 'mar.', 'abr.', 'mai.', 'jun.', 'jul.', 'ago.', 'set.', 'out.', 'nov.', 'dez.'];
    const MONTHS_LONG = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
    const WEEKDAYS = ['Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado'];

    U.startOfDay = (d) => { const x = new Date(d); x.setHours(0, 0, 0, 0); return x; };
    U.addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
    U.diffDays = (a, b) => Math.round((U.startOfDay(a) - U.startOfDay(b)) / 86400000);

    /** 23 de out. de 2026 */
    U.formatDate = (d) => { d = new Date(d); return `${pad(d.getDate())} de ${MONTHS_SHORT[d.getMonth()]} de ${d.getFullYear()}`; };
    U.formatDateShort = (d) => { d = new Date(d); return `${pad(d.getDate())} ${MONTHS_SHORT[d.getMonth()].replace('.', '')} ${d.getFullYear()}`; };
    U.formatDateBR = (d) => { d = new Date(d); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`; };
    U.formatDateLong = (d) => { d = new Date(d); return `${WEEKDAYS[d.getDay()]}, ${d.getDate()} de ${MONTHS_LONG[d.getMonth()]}`; };
    U.toISODate = (d) => { d = new Date(d); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };

    /** "dd/mm/aaaa" -> Date (validada) ou null */
    U.parseDateBR = function (str) {
        const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(String(str || '').replace(/\s/g, ''));
        if (!m) return null;
        const d = +m[1], mo = +m[2], y = +m[3];
        const date = new Date(y, mo - 1, d);
        if (date.getFullYear() !== y || date.getMonth() !== mo - 1 || date.getDate() !== d) return null;
        return date;
    };

    U.calcAge = function (birth, ref) {
        ref = ref || new Date();
        let age = ref.getFullYear() - birth.getFullYear();
        const m = ref.getMonth() - birth.getMonth();
        if (m < 0 || (m === 0 && ref.getDate() < birth.getDate())) age--;
        return age;
    };

    /* ---------- máscaras ---------- */
    U.mask = {
        phone(v) {
            const d = U.digits(v).slice(0, 11);
            if (d.length <= 2) return d.length ? `(${d}` : '';
            if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
            if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
            return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
        },
        cep(v) {
            const d = U.digits(v).slice(0, 8);
            return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
        },
        date(v) {
            const d = U.digits(v).slice(0, 8);
            if (d.length > 4) return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`;
            if (d.length > 2) return `${d.slice(0, 2)}/${d.slice(2)}`;
            return d;
        },
        cpf(v) {
            const d = U.digits(v).slice(0, 11);
            if (d.length > 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
            if (d.length > 6) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`;
            if (d.length > 3) return `${d.slice(0, 3)}.${d.slice(3)}`;
            return d;
        },
        card(v) { return U.digits(v).slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 '); },
        expiry(v) {
            const d = U.digits(v).slice(0, 4);
            return d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
        },
        cvv(v) { return U.digits(v).slice(0, 4); },
        money(v) {
            const d = U.digits(v).slice(0, 10);
            if (!d) return '';
            return U.money(parseInt(d, 10) / 100);
        }
    };

    /** Liga uma máscara a um input (mantém o cursor no final). */
    U.bindMask = function (input, type) {
        if (!input) return;
        const fn = U.mask[type];
        input.addEventListener('input', () => { input.value = fn(input.value); });
        if (input.value) input.value = fn(input.value);
    };

    /* ---------- validações ---------- */
    U.isEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(v || '').trim());
    U.isPhone = (v) => { const d = U.digits(v); return d.length === 10 || d.length === 11; };

    U.passwordRules = function (value) {
        value = value || '';
        return {
            length: value.length >= 8,
            upper: /[A-Z]/.test(value),
            lower: /[a-z]/.test(value),
            number: /[0-9]/.test(value),
            special: /[\W_]/.test(value)
        };
    };
    U.isStrongPassword = (v) => Object.values(U.passwordRules(v)).every(Boolean);

    U.isValidCPF = function (v) {
        const d = U.digits(v);
        if (d.length !== 11 || /^(\d)\1+$/.test(d)) return false;
        for (let t = 9; t < 11; t++) {
            let sum = 0;
            for (let i = 0; i < t; i++) sum += parseInt(d[i], 10) * (t + 1 - i);
            const dig = ((sum * 10) % 11) % 10;
            if (dig !== parseInt(d[t], 10)) return false;
        }
        return true;
    };

    U.isValidCard = function (v) {
        const d = U.digits(v);
        if (d.length < 13 || d.length > 19) return false;
        let sum = 0, alt = false;
        for (let i = d.length - 1; i >= 0; i--) {
            let n = parseInt(d[i], 10);
            if (alt) { n *= 2; if (n > 9) n -= 9; }
            sum += n; alt = !alt;
        }
        return sum % 10 === 0;
    };

    U.isValidExpiry = function (v) {
        const m = /^(\d{2})\/(\d{2})$/.exec(v || '');
        if (!m) return false;
        const month = +m[1], year = 2000 + +m[2];
        if (month < 1 || month > 12) return false;
        const end = new Date(year, month, 0, 23, 59, 59);
        return end >= new Date();
    };

    /** Consulta o ViaCEP com limite de tempo. Resolve { ok, data } (ok=false => preencher manualmente). */
    U.lookupCep = async function (cep) {
        const d = U.digits(cep);
        if (d.length !== 8) return { ok: false };
        const ctrl = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), 4500);
        try {
            const r = await fetch('https://viacep.com.br/ws/' + d + '/json/', { signal: ctrl.signal });
            const j = await r.json();
            if (j.erro || (!j.logradouro && !j.bairro)) return { ok: false };
            return { ok: true, data: { rua: j.logradouro || '', bairro: j.bairro || '', cidade: j.localidade || 'Brasília', uf: j.uf || 'DF' } };
        } catch (e) { return { ok: false }; } finally { clearTimeout(timer); }
    };

    /* ---------- hash (protótipo) ---------- */
    // Apenas para o protótipo de front-end. A senha real será tratada (bcrypt) no back-end.
    U.hash = function (str) {
        let h1 = 0xdeadbeef, h2 = 0x41c6ce57;
        const s = 'glowup::' + str;
        for (let i = 0; i < s.length; i++) {
            const ch = s.charCodeAt(i);
            h1 = Math.imul(h1 ^ ch, 2654435761);
            h2 = Math.imul(h2 ^ ch, 1597334677);
        }
        h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
        h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
        return (h2 >>> 0).toString(16).padStart(8, '0') + (h1 >>> 0).toString(16).padStart(8, '0');
    };

    U.randomToken = function (len) {
        const chars = 'abcdefghjkmnpqrstuvwxyz23456789';
        let out = '';
        const arr = new Uint32Array(len || 24);
        (global.crypto || {}).getRandomValues ? global.crypto.getRandomValues(arr) : arr.forEach((_, i) => arr[i] = Math.random() * 4294967296);
        arr.forEach(n => { out += chars[n % chars.length]; });
        return out;
    };

    /** Copia texto (com fallback para navegadores/arquivos locais sem Clipboard API). */
    U.copyText = async function (text) {
        try {
            if (navigator.clipboard && global.isSecureContext) {
                await navigator.clipboard.writeText(text);
                return true;
            }
        } catch (e) { /* tenta o fallback */ }
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.cssText = 'position:fixed;opacity:0;top:0;left:0;';
        document.body.appendChild(ta);
        ta.select();
        let ok = false;
        try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
        ta.remove();
        return ok;
    };

    /** Imagem de reserva (flor GlowUp) quando uma foto não carrega. */
    U.placeholderImage = 'data:image/svg+xml;utf8,' + encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400"><rect width="400" height="400" fill="#F6DBEA"/>' +
        '<g transform="translate(200 200)" fill="#E85D9E" opacity=".55">' +
        [0, 45, 90, 135, 180, 225, 270, 315].map(a => `<ellipse cx="0" cy="-62" rx="30" ry="46" transform="rotate(${a})"/>`).join('') +
        '</g><circle cx="200" cy="200" r="34" fill="#FFF3F9"/><circle cx="200" cy="200" r="20" fill="#D44687" opacity=".8"/></svg>'
    );
})(window);
