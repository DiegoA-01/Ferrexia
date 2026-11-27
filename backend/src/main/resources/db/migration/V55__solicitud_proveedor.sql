-- Spec 033: la solicitud de compra se dirige a proveedores específicos o queda abierta a todos los de la categoría
-- (como hasta ahora). Los proveedores elegidos pasan a la RFQ y filtran las ofertas de contrato.
ALTER TABLE solicitud_compra
    ADD COLUMN modo_proveedor ENUM('ABIERTA', 'ESPECIFICA') NOT NULL DEFAULT 'ABIERTA' AFTER motivo;

CREATE TABLE solicitud_proveedor (
    solicitud_id BIGINT NOT NULL,
    proveedor_id BIGINT NOT NULL,
    PRIMARY KEY (solicitud_id, proveedor_id),
    KEY ix_solicitud_proveedor_proveedor (proveedor_id),
    CONSTRAINT fk_solicitud_proveedor_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud_compra(id),
    CONSTRAINT fk_solicitud_proveedor_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

-- D21 (opción A): con alerta de presupuesto excedido o de solicitud duplicada, solo quien tenga este permiso la envía
-- con justificación; los demás quedan bloqueados. Por defecto, la plantilla Dueño.
INSERT INTO permiso (codigo, nombre, modulo) VALUES
    ('SOLICITUD_EXCEPCION', 'Enviar una solicitud con alerta de presupuesto o duplicada, con justificación', 'COMPRAS');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'SOLICITUD_EXCEPCION')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);
