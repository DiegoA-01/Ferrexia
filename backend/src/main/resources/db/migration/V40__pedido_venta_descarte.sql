-- Spec 016: quién descartó un pedido de venta, cuándo y por qué, para verlo en el filtro "Eliminados".
-- Los pedidos descartados antes de este cambio quedan con estos campos en NULL (se muestran como "sin dato").
ALTER TABLE pedido_venta
    ADD COLUMN descartado_por_usuario_id BIGINT       NULL AFTER venta_id,
    ADD COLUMN descartado_en             DATETIME     NULL AFTER descartado_por_usuario_id,
    ADD COLUMN motivo_descarte           VARCHAR(300) NULL AFTER descartado_en,
    ADD CONSTRAINT fk_pedido_venta_descartado_por FOREIGN KEY (descartado_por_usuario_id) REFERENCES usuario(id),
    ADD CONSTRAINT chk_pedido_venta_descarte CHECK (
        descartado_en IS NULL
        OR (estado = 'DESCARTADO' AND descartado_por_usuario_id IS NOT NULL
            AND motivo_descarte IS NOT NULL AND CHAR_LENGTH(TRIM(motivo_descarte)) >= 3));
