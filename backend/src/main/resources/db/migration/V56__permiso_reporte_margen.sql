-- Spec 034: el costo y el margen del reporte de ventas (y de su exportación) solo los ve quien tenga este permiso.
-- Por defecto: Dueño, Administrador y Contador/Auditor. Cajero, Vendedor y Supervisor ven ventas y total, sin costo.
INSERT INTO permiso (codigo, nombre, modulo) VALUES
    ('REPORTE_MARGEN', 'Ver el costo y el margen en el reporte de ventas', 'VENTAS');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'REPORTE_MARGEN'),
    ('ADMINISTRADOR', 'REPORTE_MARGEN'),
    ('AUDITOR', 'REPORTE_MARGEN')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);
