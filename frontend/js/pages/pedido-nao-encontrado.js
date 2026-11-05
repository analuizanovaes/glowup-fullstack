/* Tela: pedidonaoencontrado.html */
GlowUp.ready(function () {
    const { utils: U, store: S, config: C } = GlowUp;
    const R = C.routes;
    const btn = U.$('.btn-primary');
    const user = S.currentUser();
    btn.setAttribute('href', !user ? R.login : user.role === 'ADMIN' ? R.orders : R.myOrders);
});
