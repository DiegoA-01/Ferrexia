-- Spec 025: caja con permisos.
-- 1) El dueño también vende: las plantillas Dueño (ADMIN_SISTEMA) y Administrador abren y cierran turno y cobran.
-- 2) Cajas por persona: quién puede abrir turno en cada caja. Una persona sin cajas asignadas puede usar todas.
-- 3) Decisión D15: retiros, gastos e ingresos sin el PIN del supervisor para quien tenga el permiso.

INSERT INTO permiso (codigo, nombre, modulo) VALUES
    ('CAJA_RETIRO_SIN_PIN', 'Registrar retiros a la bóveda sin el PIN del supervisor', 'CAJA'),
    ('CAJA_GASTO_SIN_PIN', 'Registrar gastos de la caja sin el PIN del supervisor', 'CAJA'),
    ('CAJA_INGRESO_SIN_PIN', 'Registrar ingresos de efectivo sin el PIN del supervisor', 'CAJA');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'TURNO_OPERAR'),
    ('ADMIN_SISTEMA', 'VENTA_COBRAR'),
    ('ADMIN_SISTEMA', 'CAJA_RETIRO_SIN_PIN'),
    ('ADMIN_SISTEMA', 'CAJA_GASTO_SIN_PIN'),
    ('ADMIN_SISTEMA', 'CAJA_INGRESO_SIN_PIN'),
    ('ADMINISTRADOR', 'TURNO_OPERAR'),
    ('ADMINISTRADOR', 'VENTA_COBRAR'),
    ('ADMINISTRADOR', 'CAJA_RETIRO_SIN_PIN'),
    ('ADMINISTRADOR', 'CAJA_GASTO_SIN_PIN'),
    ('ADMINISTRADOR', 'CAJA_INGRESO_SIN_PIN'),
    ('SUPERVISOR_VENTAS', 'CAJA_RETIRO_SIN_PIN'),
    ('SUPERVISOR_VENTAS', 'CAJA_GASTO_SIN_PIN'),
    ('SUPERVISOR_VENTAS', 'CAJA_INGRESO_SIN_PIN')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);

CREATE TABLE caja_usuario (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT   NOT NULL,
    caja_id    BIGINT   NOT NULL,
    usuario_id BIGINT   NOT NULL,
    creado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_caja_usuario (caja_id, usuario_id),
    KEY ix_caja_usuario_usuario (empresa_id, usuario_id),
    CONSTRAINT fk_caja_usuario_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_caja_usuario_caja FOREIGN KEY (caja_id) REFERENCES caja(id),
    CONSTRAINT fk_caja_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;
