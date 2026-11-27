-- Spec 037: el correo identifica a la persona en todo el sistema (el ingreso ya no pide el código de la empresa).
--  * Un correo sirve para una sola empresa. Excepción: el usuario interno del super administrador de un grupo (spec 035,
--    tabla usuario_grupo_empresa), que repite su correo en cada empresa del grupo y nunca entra por el login normal.
--  * correo_login: el correo en minúsculas para todos los usuarios, NULL para los internos de grupo. Su UNIQUE hace
--    cumplir la regla (MySQL permite varios NULL en un índice único). Lo mantiene la entidad Usuario al guardar.
--  * UNIQUE (empresa_id, correo) de V17 se conserva.
-- Si ya hay correos repetidos entre empresas, la migración se detiene con un mensaje que dice cómo corregirlos.

DROP PROCEDURE IF EXISTS sp_v58_revisar_correos;

DELIMITER //
CREATE PROCEDURE sp_v58_revisar_correos()
BEGIN
    DECLARE repetido VARCHAR(150) DEFAULT NULL;
    DECLARE mensaje VARCHAR(250);
    SELECT LOWER(u.correo) INTO repetido
      FROM usuario u
     WHERE u.id NOT IN (SELECT usuario_id FROM usuario_grupo_empresa)
     GROUP BY LOWER(u.correo)
    HAVING COUNT(*) > 1
     LIMIT 1;
    IF repetido IS NOT NULL THEN
        SET mensaje = CONCAT('V58: correo repetido entre empresas: ', repetido, '. Cambie el correo de uno de los usuarios y vuelva a arrancar');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = mensaje;
    END IF;
END//
DELIMITER ;

CALL sp_v58_revisar_correos();
DROP PROCEDURE sp_v58_revisar_correos;

ALTER TABLE usuario ADD COLUMN correo_login VARCHAR(150) NULL AFTER correo;

UPDATE usuario
   SET correo_login = LOWER(correo)
 WHERE id NOT IN (SELECT usuario_id FROM usuario_grupo_empresa);

ALTER TABLE usuario ADD UNIQUE KEY uq_usuario_correo_login (correo_login);
