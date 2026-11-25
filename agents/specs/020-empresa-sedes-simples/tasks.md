# Tasks 020

- [x] T1: Reservar la migración en `agents/specs/README.md` y crear `V41__sede_enlace_mapa.sql`
- [x] T2: `GeneradorCodigoEmpresa` con sus pruebas
- [x] T3: Código opcional en el alta (generado, único con sufijo, respetado si viene); pruebas del servicio
- [x] T4: Anotación `@EnlaceGoogleMaps` con sus pruebas
- [x] T5: Sede con `enlaceMapa` (request, entidad, respuesta) sin latitud ni longitud; pruebas y prueba de fuga
- [x] T6: Consola: alta sin campo de código y código mostrado al terminar; pruebas
- [x] T7: Configuración › Sedes: enlace de Google Maps y "Ver en el mapa"; pruebas
- [x] T8: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker — `./mvnw verify` (853, 0 fallos),
  `npm test` (285), `tsc` en verde y revisión en Docker con el administrador (V41 aplicada sobre la demo; enlace
  rechazado y aceptado; "Ver en el mapa"). El build de producción lo hace el CI (Google Fonts sin red en este equipo)
- [x] T9: Verificar los criterios de aceptación y actualizar el índice de specs — aprobada el 2026-11-25

Notas (2026-11-25):
- El código escrito a mano sigue disponible detrás de "Escribir el código de ingreso a mano", como pedía el plan.
- Un código vacío o en blanco cuenta como "no escrito" (el formulario a veces manda `""`): el `@Pattern` acepta vacío y
  exige 3 caracteres solo si se escribe algo.
- El código generado tiene máximo 30 caracteres (deja espacio para `-N`) y no termina en "de", "y", "la"…
- Si dos altas generan el mismo código a la vez, el índice único de la base rechaza la segunda y se reintenta una vez
  con el siguiente número libre.
- `latitud` y `longitud` siguen en la tabla `sede` (no se borran datos), pero la API ya no las recibe ni las devuelve.
- "Ver en el mapa" vuelve a exigir `https:` en la pantalla, aunque el backend ya solo acepta enlaces de Google Maps.
- Las sedes usan la pantalla genérica de catálogos: se agregó el tipo de campo `enlace`.
