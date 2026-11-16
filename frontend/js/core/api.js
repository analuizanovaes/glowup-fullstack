/* GlowUp - cliente HTTP da API Spring Boot. */
(function (global) {
    'use strict';

    const GlowUp = (global.GlowUp = global.GlowUp || {});
    const C = GlowUp.config;
    const prefix = C.storagePrefix;

    const api = (GlowUp.api = {
        baseUrl: (C.apiBaseUrl || 'http://localhost:8080').replace(/\/+$/, ''),
        accessToken: null,
        refreshToken: null,
        refreshing: null
    });

    function storageGet(key) { try { return sessionStorage.getItem(prefix + key); } catch (e) { return null; } }
    function storageSet(key, value) { try { sessionStorage.setItem(prefix + key, value); } catch (e) { /* ignore */ } }
    function storageRemove(key) { try { sessionStorage.removeItem(prefix + key); } catch (e) { /* ignore */ } }

    api.loadSession = function () {
        api.accessToken = storageGet('accessToken');
        api.refreshToken = storageGet('refreshToken');
        return !!api.accessToken;
    };

    api.saveSession = function (data) {
        api.accessToken = data.token || data.accessToken || null;
        api.refreshToken = data.refreshToken || null;
        if (api.accessToken) storageSet('accessToken', api.accessToken); else storageRemove('accessToken');
        if (api.refreshToken) storageSet('refreshToken', api.refreshToken); else storageRemove('refreshToken');
    };

    api.clearSession = function () {
        api.accessToken = api.refreshToken = null;
        storageRemove('accessToken'); storageRemove('refreshToken'); storageRemove('user');
    };

    class ApiError extends Error {
        constructor(status, message, data) {
            super(message || 'Não foi possível concluir a operação.');
            this.name = 'ApiError';
            this.status = status;
            this.data = data;
        }
    }
    api.ApiError = ApiError;

    function normalizeError(status, data) {
        if (data && typeof data === 'object') {
            if (Array.isArray(data.erros)) return data.erros.join(' ');
            if (data.erros && typeof data.erros === 'object') return Object.values(data.erros).flat().join(' ');
            if (data.message) return data.message;
            if (data.erro) return data.erro;
            if (data.mensagem) return data.mensagem;
        }
        if (status === 401) return 'Sua sessão expirou. Entre novamente.';
        if (status === 403) return 'Você não tem permissão para esta ação.';
        if (status === 404) return 'Recurso não encontrado.';
        if (status === 409) return 'Esta operação conflita com o estado atual.';
        if (status >= 500) return 'O serviço está indisponível no momento. Tente novamente.';
        return 'Não foi possível concluir a operação.';
    }

    async function parseBody(response) {
        const text = await response.text();
        if (!text) return null;
        try { return JSON.parse(text); } catch (e) { return text; }
    }

    async function refreshSession() {
        if (!api.refreshToken) return false;
        if (!api.refreshing) {
            api.refreshing = request('/api/auth/refresh', {
                method: 'POST', auth: false, retry: false,
                body: { refreshToken: api.refreshToken }
            }).then(data => { api.saveSession(data); return true; })
                .catch(() => { api.clearSession(); return false; })
                .finally(() => { api.refreshing = null; });
        }
        return api.refreshing;
    }

    async function request(path, options) {
        options = options || {};
        const method = (options.method || 'GET').toUpperCase();
        const headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
        let body = options.body;
        if (body && !(body instanceof FormData) && typeof body !== 'string') {
            headers['Content-Type'] = 'application/json';
            body = JSON.stringify(body);
        }
        if (options.auth !== false && api.accessToken) headers.Authorization = 'Bearer ' + api.accessToken;

        const response = await fetch(api.baseUrl + path, { method, headers, body, credentials: 'omit', signal: options.signal });
        const data = await parseBody(response);
        if (response.status === 503 && data && typeof data === 'object' &&
            /manuten/i.test(String(data.message || data.erro || ''))) {
            GlowUp.maintenanceActive = true;
        }
        if (response.status === 401 && options.auth !== false && options.retry !== false && api.refreshToken) {
            const renewed = await refreshSession();
            if (renewed) return request(path, Object.assign({}, options, { retry: false }));
        }
        if (!response.ok) throw new ApiError(response.status, normalizeError(response.status, data), data);
        return data;
    }

    api.request = request;
    api.get = (path, options) => request(path, Object.assign({ method: 'GET' }, options));
    api.post = (path, body, options) => request(path, Object.assign({ method: 'POST', body }, options));
    api.put = (path, body, options) => request(path, Object.assign({ method: 'PUT', body }, options));
    api.patch = (path, body, options) => request(path, Object.assign({ method: 'PATCH', body }, options));
    api.delete = (path, options) => request(path, Object.assign({ method: 'DELETE' }, options));

    api.setTokens = function (accessToken, refreshToken) { api.saveSession({ accessToken, refreshToken }); };
    api.getToken = function () { return api.accessToken; };
    api.getRefreshToken = function () { return api.refreshToken; };
    api.clearTokens = function () { api.clearSession(); };

    api.query = function (params) {
        const q = new URLSearchParams();
        Object.keys(params || {}).forEach(k => {
            const v = params[k];
            if (v !== undefined && v !== null && v !== '') q.set(k, v);
        });
        const s = q.toString();
        return s ? '?' + s : '';
    };

    api.errorMessage = function (err, fallback) {
        return err && err.message ? err.message : (fallback || 'Não foi possível concluir a operação.');
    };

    api.loadSession();
    // Limpa tokens antigos salvos em localStorage (a sessão agora é só por aba/navegador aberto).
    try { localStorage.removeItem(prefix + 'accessToken'); localStorage.removeItem(prefix + 'refreshToken'); } catch (e) { /* ignore */ }

    api.tokenPayload = function () {
        try {
            const part = (api.accessToken || '').split('.')[1];
            if (!part) return null;
            return JSON.parse(decodeURIComponent(atob(part.replace(/-/g, '+').replace(/_/g, '/')).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')));
        } catch (e) { return null; }
    };
})(window);
