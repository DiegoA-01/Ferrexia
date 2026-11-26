-- Spec 026: producto completo.
-- 1) De dónde salió el costo de referencia: proveedor y fecha (la recepción los actualiza junto con el costo).
-- 2) Proveedor al vuelo desde el formulario del producto: solo el nombre es obligatorio, el NIT pasa a opcional.
--    El índice único (empresa_id, nit) de la V17 ignora los NULL, así que varios proveedores sin NIT no chocan.
ALTER TABLE producto
    ADD COLUMN proveedor_referencia_id BIGINT NULL AFTER costo_referencia,
    ADD COLUMN costo_referencia_fecha DATE NULL AFTER proveedor_referencia_id,
    ADD CONSTRAINT fk_producto_proveedor_referencia FOREIGN KEY (proveedor_referencia_id) REFERENCES proveedor(id);

ALTER TABLE proveedor MODIFY COLUMN nit VARCHAR(30) NULL;

-- 3) Decisión D17: superar el stock máximo no bloquea, pero avisa a compras. Canales del evento nuevo para las empresas
--    que ya existen (un evento sin fila cuenta como activo, así que las empresas nuevas también lo reciben).
INSERT INTO config_notificacion (empresa_id, evento, canal, activo)
SELECT e.id, v.evento, v.canal, 1
FROM empresa e
JOIN (SELECT 'STOCK_SOBRE_MAXIMO' AS evento, 'INTERNA' AS canal
      UNION ALL SELECT 'STOCK_SOBRE_MAXIMO', 'EMAIL') v;
