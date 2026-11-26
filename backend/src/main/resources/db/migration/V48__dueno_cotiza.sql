-- Spec 025: para vender, el dueño también entra al Punto de venta, que cotiza el carrito (VENTA_COTIZAR).
-- Complementa la V47 (abrir turno y cobrar) para las plantillas Dueño y Administrador.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'VENTA_COTIZAR'),
    ('ADMINISTRADOR', 'VENTA_COTIZAR')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);
