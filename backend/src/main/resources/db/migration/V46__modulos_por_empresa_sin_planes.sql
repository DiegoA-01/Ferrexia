-- Spec 024 (decisión D11): se quitan los planes. Los módulos de una empresa son todos los del catálogo menos los que el
-- dueño de la plataforma le apague; en empresa_modulo solo quedan los apagados (habilitado = FALSE).
-- Las empresas que ya existen quedan con todos los módulos (incluido Ventas): se borran las excepciones de antes.
-- Las tablas de planes (plan, plan_modulo) y suscripcion.plan_id se conservan sin uso, por si algún día se cobra por plan.
DELETE FROM empresa_modulo;
