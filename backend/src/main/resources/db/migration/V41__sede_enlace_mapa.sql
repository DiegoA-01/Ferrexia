-- Spec 020: la ubicación de una sede se guarda como el enlace de Google Maps que comparte la persona, en vez de
-- latitud y longitud. Las columnas latitud y longitud se quedan (no se borran datos); la API deja de usarlas.
ALTER TABLE sede
    ADD COLUMN enlace_mapa VARCHAR(500) NULL AFTER direccion;
