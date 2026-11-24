# Plan 015

Sin migración: la ocupación ya se calcula con `turno_caja` (estado ABIERTO).

## Backend (`caja/`)
- `CajaResponse` suma dos campos opcionales: `turnoAbiertoPor` (nombre del cajero del turno abierto) y
  `turnoAbiertoDesde` (fecha y hora de apertura). Con la caja libre van en `null`. `turnoAbierto` se conserva.
- `TurnoCajaRepository`: `findFirstByCajaIdAndEstado(cajaId, ABIERTO)` (con el cajero cargado) en lugar de
  `existsByCajaIdAndEstado` para el listado; las demás llamadas siguen igual.
- `CajaService.listar` arma la respuesta con esos datos. No se agrega ningún monto.
- Pruebas:
  - `CajaServiceTest`: una caja con turno abierto trae nombre y hora; una libre trae `null`.
  - `CajaControllerTest`: el JSON trae los campos nuevos y no trae base, esperado ni contado.
  - `AislamientoEmpresasTest`: la empresa A no ve la ocupación de las cajas de la empresa B (su listado no las trae).

## Frontend (`features/caja/mi-turno.ts`)
- Tipo `Caja` en `esquema.ts` con los dos campos nuevos (a mano, como en las specs anteriores, si el backend local no
  arranca para `npm run api:tipos`).
- En el `select` de abrir turno: las cajas ocupadas salen con `disabled` y el texto "ocupada por X desde dd mmm,
  hh:mm" (formato es-CO de `core/formato`).
- `cajasLibres = computed(...)`; si hay una sola, `cajaId` se fija solo al cargar.
- Si no hay ninguna libre, el formulario se cambia por el aviso de la spec.
- Pruebas en `mi-turno.spec.ts`: caja ocupada deshabilitada con su texto; una sola libre queda elegida; todas
  ocupadas muestra el aviso y no el botón "Abrir turno".

Verificación final: `./mvnw test` (incluida la prueba de fuga con MySQL real), `npm test`, `npm run build` sin
advertencias y revisión en Docker con el supervisor de ventas (Caja 1 ocupada por el cajero, Caja 2 libre).
