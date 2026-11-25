-- Spec 022, Parte B: roles propios por empresa, plantilla Administrador, permisos para las reglas de los servicios y
-- clave corta (PIN) del personal.

-- Permisos nuevos. ROL_GESTIONAR es de la pantalla de roles; los demás reemplazan reglas que antes miraban el rol dentro
-- de los servicios. Cada uno se da exactamente a los roles que la regla aceptaba, así que nadie gana ni pierde acceso.
INSERT INTO permiso (codigo, nombre, modulo) VALUES
    ('ROL_GESTIONAR', 'Crear, editar y borrar roles propios de la empresa', 'USUARIOS'),
    ('VENTA_VER_TODAS', 'Ver las ventas y los reportes de todos los cajeros y vendedores', 'VENTAS'),
    ('VENTA_VER_PROPIAS', 'Ver solo las ventas en las que fue el vendedor', 'VENTAS'),
    ('VENTA_SUPERVISAR', 'Cambiar pedidos de otros y las condiciones de crédito de un cliente', 'VENTAS'),
    ('PEDIDO_VENTA_VER_TODOS', 'Ver los pedidos de venta de todos los vendedores', 'VENTAS'),
    ('ORDEN_DOCUMENTOS', 'Adjuntar documentos a las órdenes de compra', 'COMPRAS'),
    ('SOLICITUD_VER_TODAS', 'Ver las solicitudes de compra de todos', 'COMPRAS'),
    ('SOLICITUD_CANCELAR_AJENA', 'Cancelar solicitudes de compra de otras personas', 'COMPRAS'),
    ('DELEGACION_REVOCAR_AJENA', 'Revocar delegaciones de aprobación de otras personas', 'COMPRAS'),
    ('PEDIDO_INTERNO_VER_TODOS', 'Ver los pedidos internos de todas las sedes', 'COMPRAS');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON (r.codigo, p.codigo) IN (
    ('ADMIN_SISTEMA', 'ROL_GESTIONAR'),
    ('ADMIN_SISTEMA', 'VENTA_VER_TODAS'),
    ('AUDITOR', 'VENTA_VER_TODAS'),
    ('SUPERVISOR_VENTAS', 'VENTA_VER_TODAS'),
    ('VENDEDOR', 'VENTA_VER_PROPIAS'),
    ('ADMIN_SISTEMA', 'VENTA_SUPERVISAR'),
    ('SUPERVISOR_VENTAS', 'VENTA_SUPERVISAR'),
    ('ADMIN_SISTEMA', 'PEDIDO_VENTA_VER_TODOS'),
    ('CAJERO', 'PEDIDO_VENTA_VER_TODOS'),
    ('SUPERVISOR_VENTAS', 'PEDIDO_VENTA_VER_TODOS'),
    ('ADMIN_COMPRAS', 'ORDEN_DOCUMENTOS'),
    ('COMPRADOR', 'ORDEN_DOCUMENTOS'),
    ('ADMIN_COMPRAS', 'SOLICITUD_VER_TODAS'),
    ('APROBADOR', 'SOLICITUD_VER_TODAS'),
    ('AUDITOR', 'SOLICITUD_VER_TODAS'),
    ('COMPRADOR', 'SOLICITUD_VER_TODAS'),
    ('ADMIN_COMPRAS', 'SOLICITUD_CANCELAR_AJENA'),
    ('ADMIN_COMPRAS', 'DELEGACION_REVOCAR_AJENA'),
    ('ADMIN_COMPRAS', 'PEDIDO_INTERNO_VER_TODOS'),
    ('ADMIN_SISTEMA', 'PEDIDO_INTERNO_VER_TODOS'),
    ('AUDITOR', 'PEDIDO_INTERNO_VER_TODOS'),
    ('COMPRADOR', 'PEDIDO_INTERNO_VER_TODOS')
);

-- Plantilla Administrador (D18): lo mismo que el Dueño menos editar los datos de la empresa.
INSERT INTO rol (codigo, nombre, descripcion, es_plantilla)
VALUES ('ADMINISTRADOR', 'Administrador', 'Administra usuarios, configuración y operación; no edita los datos de la empresa', TRUE);
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT a.id, rp.permiso_id
FROM rol a
JOIN rol d ON d.codigo = 'ADMIN_SISTEMA'
JOIN rol_permiso rp ON rp.rol_id = d.id
JOIN permiso p ON p.id = rp.permiso_id
WHERE a.codigo = 'ADMINISTRADOR' AND p.codigo <> 'EMPRESA_GESTIONAR';

-- Credencial (D10): CLAVE (dueño, administradores, proveedores y las claves que ya existían) o PIN de 6 dígitos que
-- asigna el jefe al personal y que el trabajador no puede cambiar. En los dos casos solo se guarda el hash BCrypt.
ALTER TABLE usuario
    ADD COLUMN tipo_credencial ENUM('CLAVE', 'PIN') NOT NULL DEFAULT 'CLAVE' AFTER hash_password;
