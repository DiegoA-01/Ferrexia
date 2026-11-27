-- Spec 035: sedes como empresas separadas bajo un grupo con un super administrador.
-- Cada empresa sigue aislada. El grupo y su super administrador son globales (como usuario_plataforma): el super
-- administrador no pertenece a ninguna empresa, entra por su propio login con MFA y, para entrar a una empresa, usa un
-- usuario Dueño (ADMIN_SISTEMA) vinculado a su cuenta dentro de esa empresa.

CREATE TABLE grupo_empresa (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre    VARCHAR(150) NOT NULL,
    activo    TINYINT(1)   NOT NULL DEFAULT 1,
    creado_en DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_grupo_empresa_nombre (nombre)
) ENGINE=InnoDB;

ALTER TABLE empresa ADD COLUMN grupo_id BIGINT NULL,
    ADD CONSTRAINT fk_empresa_grupo FOREIGN KEY (grupo_id) REFERENCES grupo_empresa(id),
    ADD INDEX idx_empresa_grupo (grupo_id);

-- Super administrador del grupo (mismo molde de usuario_plataforma; version_sesion corta sus tokens)
CREATE TABLE usuario_grupo (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    grupo_id          BIGINT       NOT NULL,
    nombre            VARCHAR(150) NOT NULL,
    correo            VARCHAR(150) NOT NULL,
    hash_password     VARCHAR(100) NOT NULL,
    activo            TINYINT(1)   NOT NULL DEFAULT 1,
    version_sesion    INT          NOT NULL DEFAULT 0,
    mfa_secreto       VARCHAR(64)  NULL,
    mfa_activo        TINYINT(1)   NOT NULL DEFAULT 0,
    mfa_ultimo_paso   BIGINT       NULL,
    intentos_fallidos INT          NOT NULL DEFAULT 0,
    bloqueado_hasta   DATETIME     NULL,
    ultimo_acceso     DATETIME     NULL,
    creado_en         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_usuario_grupo_correo (correo),
    CONSTRAINT fk_usuario_grupo_grupo FOREIGN KEY (grupo_id) REFERENCES grupo_empresa(id)
) ENGINE=InnoDB;

-- Vínculo del super administrador con su usuario Dueño dentro de cada empresa del grupo
CREATE TABLE usuario_grupo_empresa (
    usuario_grupo_id BIGINT NOT NULL,
    empresa_id       BIGINT NOT NULL,
    usuario_id       BIGINT NOT NULL,
    PRIMARY KEY (usuario_grupo_id, empresa_id),
    UNIQUE KEY uq_usuario_grupo_empresa_usuario (usuario_id),
    CONSTRAINT fk_uge_usuario_grupo FOREIGN KEY (usuario_grupo_id) REFERENCES usuario_grupo(id),
    CONSTRAINT fk_uge_empresa       FOREIGN KEY (empresa_id)       REFERENCES empresa(id),
    CONSTRAINT fk_uge_usuario       FOREIGN KEY (usuario_id)       REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Traslados entre empresas del grupo. Tocan dos empresas, por eso NO llevan empresa_id ni @TenantId: todo acceso pasa
-- por el servicio, que exige que la empresa del contexto sea la origen o la destino. El consecutivo (TG) sale de la origen.
CREATE TABLE traslado_grupo (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    grupo_id                 BIGINT       NOT NULL,
    consecutivo              VARCHAR(30)  NOT NULL,
    empresa_origen_id        BIGINT       NOT NULL,
    empresa_destino_id       BIGINT       NOT NULL,
    bodega_origen_id         BIGINT       NOT NULL,
    bodega_destino_id        BIGINT       NULL,
    estado                   ENUM('EN_TRANSITO','RECIBIDO','ANULADO') NOT NULL DEFAULT 'EN_TRANSITO',
    nota                     VARCHAR(300) NULL,
    enviado_por_usuario_id   BIGINT       NOT NULL,
    enviado_en               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recibido_por_usuario_id  BIGINT       NULL,
    recibido_en              DATETIME     NULL,
    nota_recepcion           VARCHAR(300) NULL,
    anulado_por_usuario_id   BIGINT       NULL,
    anulado_en               DATETIME     NULL,
    motivo_anulacion         VARCHAR(300) NULL,
    UNIQUE KEY uq_traslado_grupo_consecutivo (empresa_origen_id, consecutivo),
    INDEX idx_traslado_grupo_origen (empresa_origen_id, enviado_en),
    INDEX idx_traslado_grupo_destino (empresa_destino_id, estado),
    CONSTRAINT fk_tg_grupo           FOREIGN KEY (grupo_id)           REFERENCES grupo_empresa(id),
    CONSTRAINT fk_tg_empresa_origen  FOREIGN KEY (empresa_origen_id)  REFERENCES empresa(id),
    CONSTRAINT fk_tg_empresa_destino FOREIGN KEY (empresa_destino_id) REFERENCES empresa(id),
    CONSTRAINT fk_tg_bodega_origen   FOREIGN KEY (bodega_origen_id)   REFERENCES bodega(id),
    CONSTRAINT fk_tg_bodega_destino  FOREIGN KEY (bodega_destino_id)  REFERENCES bodega(id),
    CONSTRAINT fk_tg_enviado_por     FOREIGN KEY (enviado_por_usuario_id)  REFERENCES usuario(id),
    CONSTRAINT fk_tg_recibido_por    FOREIGN KEY (recibido_por_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_tg_anulado_por     FOREIGN KEY (anulado_por_usuario_id)  REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE traslado_grupo_linea (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    traslado_id         BIGINT        NOT NULL,
    producto_origen_id  BIGINT        NOT NULL,
    producto_destino_id BIGINT        NOT NULL,
    cantidad            DECIMAL(18,4) NOT NULL,
    cantidad_recibida   DECIMAL(18,4) NULL,
    costo_unitario      DECIMAL(18,4) NOT NULL,
    CHECK (cantidad > 0),
    INDEX idx_tgl_traslado (traslado_id),
    CONSTRAINT fk_tgl_traslado         FOREIGN KEY (traslado_id)         REFERENCES traslado_grupo(id),
    CONSTRAINT fk_tgl_producto_origen  FOREIGN KEY (producto_origen_id)  REFERENCES producto(id),
    CONSTRAINT fk_tgl_producto_destino FOREIGN KEY (producto_destino_id) REFERENCES producto(id)
) ENGINE=InnoDB;

-- Dos tipos de movimiento nuevos: la salida de la origen al enviar y la entrada en la destino al recibir
ALTER TABLE movimiento_inventario
    MODIFY COLUMN tipo ENUM('ENTRADA_COMPRA','SALIDA_CONSUMO','AJUSTE_POSITIVO','AJUSTE_NEGATIVO',
                            'DEVOLUCION_PROVEEDOR','TRASLADO','SALIDA_VENTA','ANULACION_DESPACHO',
                            'ANULACION_VENTA','DEVOLUCION_CLIENTE','SALIDA_TRASLADO_GRUPO',
                            'ENTRADA_TRASLADO_GRUPO') NOT NULL;

-- Permisos de los traslados entre empresas del grupo: Dueño, Administrador y Almacenista
INSERT INTO permiso (codigo, nombre, modulo) VALUES
    ('TRASLADO_GRUPO_ENVIAR', 'Enviar y anular traslados de mercancía a otra empresa del grupo', 'INVENTARIO'),
    ('TRASLADO_GRUPO_RECIBIR', 'Recibir traslados de mercancía enviados por otra empresa del grupo', 'INVENTARIO');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'TRASLADO_GRUPO_ENVIAR'),
    ('ADMINISTRADOR', 'TRASLADO_GRUPO_ENVIAR'),
    ('ALMACENISTA', 'TRASLADO_GRUPO_ENVIAR'),
    ('ADMIN_SISTEMA', 'TRASLADO_GRUPO_RECIBIR'),
    ('ADMINISTRADOR', 'TRASLADO_GRUPO_RECIBIR'),
    ('ALMACENISTA', 'TRASLADO_GRUPO_RECIBIR')
)
WHERE r.empresa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM rol_permiso x WHERE x.rol_id = r.id AND x.permiso_id = p.id);
