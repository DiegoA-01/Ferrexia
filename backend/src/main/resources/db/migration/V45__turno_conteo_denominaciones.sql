-- Spec 023: conteo del efectivo por billete y moneda al cerrar el turno.
-- Lista JSON [{"tipo":"BILLETE","valor":50000,"cantidad":3,"referencia":""}]; NULL = el turno se cerró escribiendo
-- solo el total (como antes de esta spec).
ALTER TABLE turno_caja ADD COLUMN conteo_denominaciones JSON NULL AFTER contado_otros;
