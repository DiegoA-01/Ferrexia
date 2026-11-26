-- Spec 029 (D16): anular una factura de un turno cerrado y cambiar el método de pago de una factura POS.
-- 1) El cambio de pago deja en la caja un "ajuste de pago" por medio (lo que sale del medio viejo y entra al nuevo); el
--    cajero lo hace con el PIN del supervisor (acción CAMBIAR_PAGO).
ALTER TABLE movimiento_caja
    MODIFY COLUMN tipo ENUM('BASE', 'VENTA', 'DEVOLUCION', 'ANULACION', 'RETIRO', 'GASTO', 'INGRESO', 'ABONO_CARTERA',
                            'AJUSTE_PAGO') NOT NULL;

ALTER TABLE autorizacion_supervisor
    MODIFY COLUMN accion ENUM('ANULAR_VENTA', 'DESCUENTO', 'PRECIO_BAJO_MINIMO', 'DEVOLUCION', 'RETIRO', 'GASTO',
                              'INGRESO', 'CAMBIAR_PAGO') NOT NULL;

-- 2) Historial de cambios de pago: venta_pago guarda los pagos vigentes y aquí queda el antes y el después.
CREATE TABLE venta_pago_cambio (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id                BIGINT       NOT NULL,
    venta_id                  BIGINT       NOT NULL,
    turno_id                  BIGINT       NOT NULL,
    usuario_id                BIGINT       NOT NULL,
    autorizado_por_usuario_id BIGINT       NULL,
    antes                     JSON         NOT NULL,
    despues                   JSON         NOT NULL,
    motivo                    VARCHAR(300) NOT NULL,
    creado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY ix_venta_pago_cambio_venta (empresa_id, venta_id),
    CONSTRAINT fk_venta_pago_cambio_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_venta_pago_cambio_venta FOREIGN KEY (venta_id) REFERENCES venta(id),
    CONSTRAINT fk_venta_pago_cambio_turno FOREIGN KEY (turno_id) REFERENCES turno_caja(id),
    CONSTRAINT fk_venta_pago_cambio_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_venta_pago_cambio_autoriza FOREIGN KEY (autorizado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- 3) Permisos nuevos para las plantillas Dueño, Administrador y Supervisor de ventas.
INSERT INTO permiso (codigo, nombre, modulo) VALUES
    ('VENTA_ANULAR_OTRO_TURNO', 'Anular facturas de un turno ya cerrado', 'VENTAS'),
    ('VENTA_CAMBIAR_PAGO', 'Cambiar el método de pago de una factura sin el PIN del supervisor', 'VENTAS');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'VENTA_ANULAR_OTRO_TURNO'),
    ('ADMIN_SISTEMA', 'VENTA_CAMBIAR_PAGO'),
    ('ADMINISTRADOR', 'VENTA_ANULAR_OTRO_TURNO'),
    ('ADMINISTRADOR', 'VENTA_CAMBIAR_PAGO'),
    ('SUPERVISOR_VENTAS', 'VENTA_ANULAR_OTRO_TURNO'),
    ('SUPERVISOR_VENTAS', 'VENTA_CAMBIAR_PAGO')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);
