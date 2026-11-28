-- El Dueño (ADMIN_SISTEMA) también atiende los pedidos internos (tomar, atender, descartar): PEDIDO_INTERNO_GESTIONAR.
-- Antes solo lo tenían Admin. compras y Compras. No crea permisos nuevos; el Administrador hereda del Dueño en el código.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'PEDIDO_INTERNO_GESTIONAR'),
    ('ADMINISTRADOR', 'PEDIDO_INTERNO_GESTIONAR')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);
