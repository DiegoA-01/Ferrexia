-- Spec 021: categorías de ingresos y egresos de caja. Cada empresa crea las suyas (Configuración › Caja); el gasto y
-- el ingreso del turno llevan una categoría y el retiro puede llevarla. No se borran: se desactivan.
CREATE TABLE categoria_movimiento_caja (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT                     NOT NULL,
    tipo       ENUM('INGRESO', 'EGRESO')  NOT NULL,
    nombre     VARCHAR(80)                NOT NULL,
    activa     BOOLEAN                    NOT NULL DEFAULT TRUE,
    creado_en  DATETIME                   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_categoria_movimiento_caja (empresa_id, tipo, nombre),
    CONSTRAINT fk_categoria_movimiento_caja_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

ALTER TABLE movimiento_caja
    ADD COLUMN categoria_id BIGINT NULL AFTER soporte,
    ADD CONSTRAINT fk_movimiento_caja_categoria FOREIGN KEY (categoria_id) REFERENCES categoria_movimiento_caja(id);

-- Las categorías por defecto para las empresas que ya existen (las nuevas las reciben al darse de alta).
INSERT INTO categoria_movimiento_caja (empresa_id, tipo, nombre)
SELECT e.id, c.tipo, c.nombre
FROM empresa e
CROSS JOIN (
    SELECT 'EGRESO' AS tipo, 'Papelería' AS nombre
    UNION ALL SELECT 'EGRESO', 'Aseo'
    UNION ALL SELECT 'EGRESO', 'Domicilio'
    UNION ALL SELECT 'EGRESO', 'Otro'
    UNION ALL SELECT 'INGRESO', 'Base adicional'
    UNION ALL SELECT 'INGRESO', 'Otro'
) c;
