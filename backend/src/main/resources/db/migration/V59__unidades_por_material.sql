-- Spec 038: unidades según el material. Se suma la medida ÁREA, las unidades de ferretería que faltaban y lo que mide
-- cada categoría (opcional): el formulario del producto ofrece primero las unidades de esa medida.

ALTER TABLE unidad_medida
    MODIFY COLUMN magnitud ENUM('CANTIDAD','MASA','VOLUMEN','LONGITUD','AREA') NOT NULL DEFAULT 'CANTIDAD';

-- factor = cuántas unidades base de su medida equivale (bases: UND, KG, LT, M y M2)
INSERT INTO unidad_medida (codigo, nombre, magnitud, factor_conversion_base)
SELECT v.codigo, v.nombre, v.magnitud, v.factor FROM (
    SELECT 'M3' AS codigo, 'Metro cúbico' AS nombre, 'VOLUMEN' AS magnitud, 1000.000000 AS factor
    UNION ALL SELECT 'M2', 'Metro cuadrado', 'AREA', 1.000000
    UNION ALL SELECT 'TON', 'Tonelada', 'MASA', 1000.000000
    UNION ALL SELECT 'LB', 'Libra', 'MASA', 0.453592
    UNION ALL SELECT 'GAL', 'Galón', 'VOLUMEN', 3.785411
) v
WHERE NOT EXISTS (SELECT 1 FROM unidad_medida u WHERE u.codigo = v.codigo);

-- NULL = la categoría no dice qué mide: se ofrecen todas las unidades
ALTER TABLE categoria_producto
    ADD COLUMN magnitud ENUM('CANTIDAD','MASA','VOLUMEN','LONGITUD','AREA') NULL AFTER nombre;
