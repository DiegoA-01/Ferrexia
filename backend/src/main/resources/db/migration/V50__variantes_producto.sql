-- Spec 028: variantes de producto. Cada variante es un producto normal (con su código, stock, kardex y precio) que
-- apunta a un producto agrupador. El agrupador no tiene stock, no se vende ni se compra: solo agrupa y guarda los
-- atributos. Los atributos y la combinación de cada variante van como JSON en la misma fila, así que el aislamiento por
-- empresa del producto (empresa_id) ya los cubre.
--   atributos   (agrupador): [{"nombre":"Medida","valores":["1\"","2\""]}]
--   combinacion (variante):  {"Medida":"1\""}
ALTER TABLE producto
    ADD COLUMN es_agrupador TINYINT(1) NOT NULL DEFAULT 0 AFTER activo,
    ADD COLUMN agrupador_id BIGINT NULL AFTER es_agrupador,
    ADD COLUMN atributos JSON NULL AFTER agrupador_id,
    ADD COLUMN combinacion JSON NULL AFTER atributos,
    ADD CONSTRAINT fk_producto_agrupador FOREIGN KEY (agrupador_id) REFERENCES producto(id),
    ADD CONSTRAINT ck_producto_agrupador_sin_padre CHECK (es_agrupador = 0 OR agrupador_id IS NULL);
