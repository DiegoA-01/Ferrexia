-- Spec 030 (D13): vender sin existencia con permiso, motivo, aviso y reporte. El stock nunca baja de cero: sale lo que
-- hay y la diferencia queda como faltante pendiente, que se descuenta solo cuando entra mercancía de ese producto a esa
-- bodega (del más viejo al más nuevo). "pendiente" es lo que falta por descontar de "cantidad".
CREATE TABLE venta_sin_existencia (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id       BIGINT        NOT NULL,
    venta_id         BIGINT        NOT NULL,
    producto_id      BIGINT        NOT NULL,
    bodega_id        BIGINT        NOT NULL,
    usuario_id       BIGINT        NOT NULL,
    cantidad         DECIMAL(18,4) NOT NULL,
    pendiente        DECIMAL(18,4) NOT NULL,
    motivo           VARCHAR(200)  NOT NULL,
    estado           ENUM('PENDIENTE', 'REGULARIZADA', 'ANULADA') NOT NULL DEFAULT 'PENDIENTE',
    creado_en        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    regularizada_en  DATETIME      NULL,
    KEY ix_venta_sin_existencia_pendiente (empresa_id, producto_id, bodega_id, estado),
    KEY ix_venta_sin_existencia_venta (empresa_id, venta_id),
    CONSTRAINT chk_venta_sin_existencia CHECK (cantidad > 0 AND pendiente >= 0 AND pendiente <= cantidad),
    CONSTRAINT fk_venta_sin_existencia_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_venta_sin_existencia_venta FOREIGN KEY (venta_id) REFERENCES venta(id),
    CONSTRAINT fk_venta_sin_existencia_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT fk_venta_sin_existencia_bodega FOREIGN KEY (bodega_id) REFERENCES bodega(id),
    CONSTRAINT fk_venta_sin_existencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

INSERT INTO permiso (codigo, nombre, modulo) VALUES
    ('VENTA_SIN_EXISTENCIA', 'Vender productos sin existencia, con motivo (queda un faltante pendiente)', 'VENTAS');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'VENTA_SIN_EXISTENCIA'),
    ('ADMINISTRADOR', 'VENTA_SIN_EXISTENCIA'),
    ('SUPERVISOR_VENTAS', 'VENTA_SIN_EXISTENCIA')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);

-- Aviso a compras y bodega: canales del evento nuevo para las empresas que ya existen (un evento sin fila cuenta como
-- activo, así que las empresas nuevas también lo reciben).
INSERT INTO config_notificacion (empresa_id, evento, canal, activo)
SELECT e.id, v.evento, v.canal, 1
FROM empresa e
JOIN (SELECT 'VENTA_SIN_EXISTENCIA' AS evento, 'INTERNA' AS canal
      UNION ALL SELECT 'VENTA_SIN_EXISTENCIA', 'EMAIL') v;
