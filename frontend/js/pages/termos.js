/* Telas: termosdeuso.html e termodedevolucao.html */
GlowUp.ready(function () {
    const { utils: U, config: C } = GlowUp;
    const close = U.$('.btn-fechar');
    const overlay = U.$('.modal-overlay');
    function leave() {
        if (history.length > 1 && document.referrer) history.back(); else location.href = C.routes.landing;
    }
    close.addEventListener('click', leave);
    document.addEventListener('keydown', e => { if (e.key === 'Escape') leave(); });
    overlay.addEventListener('mousedown', e => { if (e.target === overlay) leave(); });
});
