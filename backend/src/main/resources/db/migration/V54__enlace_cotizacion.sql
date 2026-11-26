-- Spec 031 (D14): el proveedor deja de tener cuenta y cotiza desde un enlace de un solo uso. El portal se retira:
-- lo que ya existe queda como historial y los usuarios del portal (con proveedor) quedan desactivados.

-- 1) Enlace de cotización: uno por proveedor invitado a la RFQ. El enlace es "{id}.{firma}"; la firma es un HMAC de
--    id, empresa y versión con una clave del servidor, así que aquí no se guarda nada que sirva para armarlo.
--    Regenerar sube la versión y el enlace anterior deja de servir.
CREATE TABLE enlace_cotizacion (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id            BIGINT   NOT NULL,
    rfq_id                BIGINT   NOT NULL,
    proveedor_id          BIGINT   NOT NULL,
    version               INT      NOT NULL DEFAULT 1,
    vence_en              DATETIME NOT NULL,
    usado_en              DATETIME NULL,
    creado_por_usuario_id BIGINT   NULL,
    creado_en             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_enlace_rfq_proveedor (rfq_id, proveedor_id),
    KEY ix_enlace_empresa (empresa_id, rfq_id),
    CONSTRAINT fk_enlace_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_enlace_rfq FOREIGN KEY (rfq_id) REFERENCES rfq (id),
    CONSTRAINT fk_enlace_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedor (id)
);

-- 2) Usuarios del portal: desactivados, con la sesión cortada (sube la versión de sesión) y sin refresh tokens.
UPDATE refresh_token rt
    JOIN usuario u ON u.id = rt.usuario_id
SET rt.revocado_en = NOW()
WHERE u.proveedor_id IS NOT NULL AND rt.revocado_en IS NULL;

UPDATE usuario
SET activo = 0, version_sesion = version_sesion + 1
WHERE proveedor_id IS NOT NULL AND activo = 1;
