-- Spec 032 (D19): domicilios. La venta a domicilio se confirma cuando sale (el stock baja con la mercancía); si es
-- contraentrega, su pago queda como CONTRAENTREGA (no entra a la caja) y un cajero lo cobra en su turno cuando vuelve el
-- domiciliario (movimiento COBRO_DOMICILIO). El costo del envío es un producto servicio (sin stock ni kardex).

-- 1) Productos servicio y tipo de servicio de la venta
ALTER TABLE producto ADD COLUMN es_servicio TINYINT(1) NOT NULL DEFAULT 0 AFTER vendible;
ALTER TABLE venta ADD COLUMN tipo_servicio ENUM('MOSTRADOR', 'DOMICILIO') NOT NULL DEFAULT 'MOSTRADOR';

-- 2) Medio contraentrega y cobro del domicilio en la caja
ALTER TABLE venta_pago
    MODIFY COLUMN medio ENUM('EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'TRANSFERENCIA', 'BILLETERA', 'CREDITO',
                             'SALDO_FAVOR', 'CONTRAENTREGA') NOT NULL;
ALTER TABLE movimiento_caja
    MODIFY COLUMN tipo ENUM('BASE', 'VENTA', 'DEVOLUCION', 'ANULACION', 'RETIRO', 'GASTO', 'INGRESO', 'ABONO_CARTERA',
                            'AJUSTE_PAGO', 'COBRO_DOMICILIO') NOT NULL;

-- 3) Domicilio y su historial
CREATE TABLE domicilio (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id              BIGINT       NOT NULL,
    consecutivo             VARCHAR(20)  NOT NULL,
    venta_id                BIGINT       NOT NULL,
    direccion               VARCHAR(300) NOT NULL,
    telefono                VARCHAR(30)  NOT NULL,
    referencia              VARCHAR(300) NULL,
    pago                    ENUM('PAGADO', 'CONTRAENTREGA') NOT NULL,
    estado                  ENUM('PENDIENTE', 'ASIGNADO', 'EN_CAMINO', 'ENTREGADO', 'DEVUELTO', 'CANCELADO')
                                         NOT NULL DEFAULT 'PENDIENTE',
    domiciliario_usuario_id BIGINT       NULL,
    domiciliario_nombre     VARCHAR(150) NULL,
    domiciliario_telefono   VARCHAR(30)  NULL,
    motivo                  VARCHAR(300) NULL,
    creado_en               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entregado_en            DATETIME     NULL,
    cobrado_en              DATETIME     NULL,
    cobrado_turno_id        BIGINT       NULL,
    cobrado_por_usuario_id  BIGINT       NULL,
    UNIQUE KEY uq_domicilio_consecutivo (empresa_id, consecutivo),
    UNIQUE KEY uq_domicilio_venta (venta_id),
    KEY ix_domicilio_estado (empresa_id, estado, creado_en),
    CONSTRAINT fk_domicilio_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_domicilio_venta FOREIGN KEY (venta_id) REFERENCES venta(id),
    CONSTRAINT fk_domicilio_domiciliario FOREIGN KEY (domiciliario_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_domicilio_turno_cobro FOREIGN KEY (cobrado_turno_id) REFERENCES turno_caja(id),
    CONSTRAINT fk_domicilio_cobrado_por FOREIGN KEY (cobrado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE domicilio_evento (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id   BIGINT       NOT NULL,
    domicilio_id BIGINT       NOT NULL,
    estado       VARCHAR(20)  NOT NULL,
    usuario_id   BIGINT       NOT NULL,
    nota         VARCHAR(300) NULL,
    creado_en    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY ix_domicilio_evento (empresa_id, domicilio_id),
    CONSTRAINT fk_domicilio_evento_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_domicilio_evento_domicilio FOREIGN KEY (domicilio_id) REFERENCES domicilio(id),
    CONSTRAINT fk_domicilio_evento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- 4) Permisos: gestionar (asignar, cancelar, devolver, ver todos) y repartir (ver los suyos y marcarlos en camino y
--    entregados). Repartir no va en ninguna plantilla: se da como permiso suelto o en un rol propio "Domiciliario".
INSERT INTO permiso (codigo, nombre, modulo) VALUES
    ('DOMICILIO_GESTIONAR', 'Asignar, cancelar y devolver domicilios y configurar el envío', 'VENTAS'),
    ('DOMICILIO_REPARTIR', 'Repartir domicilios: ver los asignados y marcarlos en camino y entregados', 'VENTAS');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'DOMICILIO_GESTIONAR'),
    ('ADMINISTRADOR', 'DOMICILIO_GESTIONAR'),
    ('SUPERVISOR_VENTAS', 'DOMICILIO_GESTIONAR')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);
