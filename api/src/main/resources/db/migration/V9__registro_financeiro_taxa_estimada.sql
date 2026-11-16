-- RN05: indica se a taxa do gateway foi estimada (Mercado Pago nao informou o valor liquido na aprovacao)
ALTER TABLE registros_financeiros ADD COLUMN taxa_estimada BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE registros_financeiros r
   SET taxa_estimada = TRUE
  FROM pagamentos p
 WHERE p.pedido_id = r.pedido_id
   AND p.taxa IS NULL;
