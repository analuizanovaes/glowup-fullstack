-- ViaCEP fora do ar no checkout: o endereco digitado e aceito, mas o pedido fica marcado para a administradora
-- conferir bairro/CEP antes de separar e enviar (o frete depende do bairro).
ALTER TABLE pedidos ADD COLUMN conferencia_endereco_pendente BOOLEAN NOT NULL DEFAULT FALSE;
