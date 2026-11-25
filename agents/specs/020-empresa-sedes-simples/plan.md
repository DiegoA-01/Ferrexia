# Plan 020

Rama `feature/empresa-sedes-simples`, sacada de `develop` actualizado. Migración propuesta **V41** (se reserva en
`agents/specs/README.md` en el primer commit; si otra rama la tomó, se usa la siguiente libre).

## Lo que hay hoy (revisado en el código)
- **Alta de empresa:** `EmpresaCrearRequest` pide `razonSocial`, `nit`, `codigoPublico`, `planCodigo`, `estadoInicial` y los
  datos del administrador. El código es texto libre de 3 a 40 caracteres (acepta espacios, tildes y números; sin
  `/ \ ? # %`) y debe ser único (`empresa.codigo_publico`). `EmpresaPlataformaService.crear` lo valida.
- **Sede:** `SedeRequest` pide `nombre`, `direccion`, `latitud` y `longitud` (las dos o ninguna, con `@AssertTrue`).
  `Sede` guarda `latitud`/`longitud` como `DECIMAL`. La pantalla de sedes está en Configuración (`empresa.ts`).

## Backend
1. **Código automático:**
   - `codigoPublico` pasa a opcional en `EmpresaCrearRequest`. Si no viene, `GeneradorCodigoEmpresa` (clase nueva, Java
     puro) lo arma desde la razón social: minúsculas, sin tildes, palabras unidas con guion, sin palabras como "S.A.S." o
     "LTDA", máximo 30 caracteres (por ejemplo "Ferretería El Tornillo S.A.S." → `ferreteria-el-tornillo`).
   - Si ya existe, se prueba `-2`, `-3`… dentro de la misma transacción (con el índice único como red final: si choca por
     carrera, se reintenta una vez).
   - Si el dueño de la plataforma sí escribe uno, se respeta y se valida como hoy.
   - `EmpresaDetalleResponse` ya devuelve el código; la respuesta del alta también.
2. **Sede con enlace:**
   - V41: `sede.enlace_mapa VARCHAR(500) NULL`. `latitud` y `longitud` se quedan en la tabla (no se borran datos).
   - `SedeRequest`: se quitan `latitud`/`longitud` y se suma `enlaceMapa` opcional, validado con `@EnlaceGoogleMaps`
     (anotación nueva): `https` y host `google.com`/`www.google.com` con ruta `/maps…`, `maps.google.com`,
     `maps.app.goo.gl` o `goo.gl/maps`. Máximo 500 caracteres.
   - `SedeResponse` suma `enlaceMapa`; deja de exponer latitud y longitud.
3. Auditoría: el alta ya se audita; se suma el código generado al detalle.

## Frontend
- **Consola de plataforma, alta de empresa:** el campo "Código de ingreso" desaparece del formulario; al terminar se muestra
  "Código de ingreso: ferreteria-el-tornillo" con botón copiar. (Si se quiere conservar un campo opcional "Personalizar
  código", va escondido en "Opciones avanzadas".)
- **Sedes (Configuración › Empresa):** se quitan latitud y longitud; campo "Enlace de Google Maps (opcional)" con ayuda
  "Abre la ubicación en Google Maps, toca Compartir y copia el enlace". En la lista, botón "Ver en el mapa" que abre el
  enlace en otra pestaña (`rel="noopener noreferrer"`).
- Tipos en `esquema.ts` (con `npm run api:tipos` o a mano).

## Pruebas
- `GeneradorCodigoEmpresaTest`: tildes, sufijos societarios, espacios repetidos, largo máximo, nombre de un solo carácter.
- `EmpresaPlataformaServiceTest`: alta sin código genera uno; con código repetido genera `-2`; con código escrito lo respeta.
- Validación `@EnlaceGoogleMaps`: acepta los cuatro formatos, rechaza `http`, otros dominios y `javascript:`.
- `SedeYCentroCostoServiceTest`: guarda y devuelve el enlace.
- `AislamientoEmpresasTest`: la sede con enlace de A no aparece en B.
- Frontend: alta sin campo de código muestra el código al terminar; sede sin latitud ni longitud; "Ver en el mapa".

Verificación final: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker con el dueño de la plataforma (alta)
y el dueño de la empresa (sede).
