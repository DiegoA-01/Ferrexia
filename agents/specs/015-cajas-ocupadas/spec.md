# 015 - Cajas ocupadas en "Mi turno"

**Estado:** Hecha (2026-11-23, sin PR todavía) · **Rama:** `feature/cajas-ocupadas` (sale de `feature/pestanas-categoria`) · **Depende de:**
003, 005

## Objetivo
Que quien va a vender sepa de inmediato qué caja puede usar. Hoy, si alguien sin turno propio entra al Punto de
venta, el sistema lo manda a "Mi turno", y ahí la lista de cajas muestra igual una caja que ya tiene el turno de otra
persona. Al intentar abrirla, el sistema la rechaza (solo puede haber un turno abierto por caja, regla de la spec
003), y la persona no entiende por qué el Punto de venta "no se deja ver".

## Historias
- Como supervisor de ventas, al abrir mi turno quiero ver qué cajas están ocupadas y por quién, para elegir una libre.
- Como cajero, si todas las cajas están ocupadas, quiero que la pantalla me diga qué hacer.

## Comportamiento
- En "Mi turno", la lista para abrir turno muestra cada caja activa con su estado:
  - Libre: se puede elegir.
  - Ocupada: sale como "Caja 1 mostrador · ocupada por Cajero desde el 20 nov, 11:01" y no se puede elegir.
- Si la persona tiene una sola caja libre, queda elegida sola.
- Si todas las cajas están ocupadas, en lugar del formulario sale un aviso: "Todas las cajas tienen un turno abierto.
  Pide a quien la está usando que cierre su turno, o pide al administrador que cree otra caja en Configuración, Cajas".
- El aviso que hoy dice "Abra su turno de caja antes de vender" se mantiene.
- Nada cambia en las reglas de caja: un turno por caja y por cajero, cierre a ciegas, y el cajero no ve cuánto
  debería tener.
- El nombre y la hora del turno abierto solo los ven quienes ya pueden listar cajas (administrador, supervisor de
  ventas, cajero y auditor), y nunca el efectivo de ese turno.

## Criterios de aceptación
- [x] En "Mi turno", una caja con turno abierto sale marcada con el nombre de quien la tiene y desde cuándo, y no se
  puede elegir.
- [x] Con una sola caja libre, esa caja queda elegida.
- [x] Con todas las cajas ocupadas, sale el aviso con qué hacer en vez del formulario.
- [x] La respuesta de cajas no trae montos del turno abierto.
- [x] Prueba de fuga entre empresas: el dato de ocupación solo sale para cajas de la propia empresa.

> Verificado el 2026-11-23: `CajaServiceTest` (ocupada con quién y desde cuándo, libre sin datos, sin campos de
> dinero), `AislamientoEmpresasTest` con MySQL real (el listado de A trae la ocupación y el de B no la ve) y
> `mi-turno.spec.ts` (opción bloqueada con su texto, una sola libre elegida sola, aviso con todas ocupadas). Backend:
> 831 pruebas en verde (1 omitida, Gemini en vivo) y Javadoc sin advertencias; frontend: 230 en verde y build sin
> advertencias. En Docker con el supervisor de ventas: con la Caja 1 del cajero y la Caja 2 de Usuario Total, sale el
> aviso con las dos cajas, quién y desde cuándo, en lugar del formulario.

## Fuera de alcance
- Cerrar el turno de otra persona o forzar un cierre.
- Cambiar la regla de un turno por caja.
- Crear cajas desde "Mi turno" (se crean en Configuración, Cajas).
