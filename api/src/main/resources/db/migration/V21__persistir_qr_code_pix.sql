-- O QR Code em base64 era devolvido só na criação do pagamento; retries e GET /api/pagamentos/pedido/{id}
-- retornavam null. Persistir a imagem mantém o contrato estável para o front-end.
ALTER TABLE pagamentos ADD COLUMN pix_qr_code_base64 TEXT;
