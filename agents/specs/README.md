# Specs

Una carpeta por cambio, numerada para conservar el orden: `NNN-nombre/` con `spec.md` (qué y por qué, sin tecnología),
`plan.md` (cómo: tablas, endpoints, pantallas y pruebas) y `tasks.md` (pasos T1, T2… que se marcan con `[x]` solo si sus
pruebas pasan). El flujo está en `AGENTS.md`.

Estados de una spec: **Propuesta** (en revisión) → **Aprobada** (se puede planear y construir) → **En construcción** →
**Hecha** (criterios de aceptación verificados).

> **Revisión del 2026-11-20 (rama `fix/ventas-pos-revision`, sobre `feature/ventas-pos`).** Las specs 004 a 008 se
> construyeron juntas en `feature/ventas-pos`. La revisión corrigió: la devolución de una venta a crédito rebaja primero
> su deuda (no saca efectivo ni crea saldo a favor mientras se deba); la venta devuelta del todo devuelve también el
> redondeo; el reintento con la misma clave ve la venta ya creada y rechaza otro carrito (`CLAVE_REPETIDA`); el turno se
> bloquea en la primera lectura (venta, devolución y abono, mismo orden de bloqueo); el permiso de descuento no pasa el
> límite de quien lo da; el pedido se cobra con sus mismos productos (`PEDIDO_DISTINTO`); una venta de menos de $25 no se
> redondea a cero; el costo promedio parte del costo vigente y no cuenta la bodega de averías; el abono con tarjeta o
> transferencia pide referencia; la cartera entrega `vencido`; y el POS fija el carrito mientras la venta va en camino,
> no vacía el carrito con Esc desde un campo ni sin preguntar, F9 en el cobro confirma y descarta cotizaciones viejas.

## Índice

| Spec | Fase de la spec original | Qué entrega | Rama | Depende de | Estado |
|---|---|---|---|---|---|
| [000-ci-github-actions](000-ci-github-actions/spec.md) | F0 | CI con GitHub Actions, Dependency Review y CodeQL | `feature/ci-github-actions` | — | Hecha (PR #26) |
| [001-ventas-catalogo](001-ventas-catalogo/spec.md) | F1 | Módulos, roles, impuestos, código de barras, listas y precios con historial | `feature/ventas-catalogo` | 000 | Hecha (V29 + V31, PR #28) |
| [002-ventas-clientes](002-ventas-clientes/spec.md) | F2 | Clientes, consumidor final, autorización de datos | `feature/ventas-clientes` | 001 | Hecha (V32, PR #29) |
| [003-caja-turnos](003-caja-turnos/spec.md) | F3 | Cajas, turnos, libro de caja, PIN de supervisor, cierre ciego | `feature/caja-turnos` | 001 | Hecha (V33, PR #30) |
| [004-venta-backend](004-venta-backend/spec.md) | F4 | La venta en una sola transacción: precios, descuentos, impuestos, pagos, stock, caja, idempotencia | `feature/ventas-pos` | 002, 003 | Hecha (V34, PR #32) |
| [005-pos-frontend](005-pos-frontend/spec.md) | F5 | POS con lector y atajos, cobro, tiquete de 80 mm, mi turno | `feature/ventas-pos` | 004 | Hecha (PR #32; falta prueba en Docker) |
| [006-anulacion-devolucion](006-anulacion-devolucion/spec.md) | F6 | Anulación en el turno y devolución de cliente | `feature/ventas-pos` | 004 | Hecha (V35, PR #32) |
| [007-cartera](007-cartera/spec.md) | F7 | Crédito, cupos, abonos y edades | `feature/ventas-pos` | 004 | Hecha (V37, PR #32) |
| [008-costo-promedio-margen](008-costo-promedio-margen/spec.md) | F8 | Costo promedio ponderado y margen real | `feature/ventas-pos` | 004 | Hecha (V36, PR #32) |
| [009-reportes-cuadre](009-reportes-cuadre/spec.md) | F9 | Reportes de ventas y caja, tablero, cuadre diario automático | `feature/ventas-reportes` | 006, 008 | Hecha (V38) |
| [010-endurecimiento-e2e](010-endurecimiento-e2e/spec.md) | F10 | Alertas, pruebas de punta a punta en el CI, prueba de carga | `feature/ventas-endurecimiento` | 009 | Hecha (V39; falta ver el `e2e` del CI en GitHub) |
| [011-menu-por-categorias](011-menu-por-categorias/spec.md) | — | Menú lateral en siete grupos: Ventas, Compras, Inventario, Reportes, Gestión humana y Gestión | `feature/menu-por-categorias` | — | Hecha (PR #12) |
| [012-inicio-categorias](012-inicio-categorias/spec.md) | — | Tablero de categorías con icono en la página de Inicio | `feature/inicio-categorias` | 011 | Hecha (PR #13) |
| [013-menu-desplegable](013-menu-desplegable/spec.md) | — | Barra lateral con categorías desplegables y tarjetas de Inicio que entran directo a su categoría | `feature/menu-desplegable` | 011, 012 | Hecha (PR #14; la regla de varias categorías abiertas la reemplaza la 017) |
| [014-pestanas-categoria](014-pestanas-categoria/spec.md) | — | Fila de pestañas con las pantallas de la categoría actual en cada pantalla | `feature/pestanas-categoria` | 011, 013 | Hecha (PR #15) |
| [015-cajas-ocupadas](015-cajas-ocupadas/spec.md) | — | "Mi turno" muestra qué cajas están ocupadas, por quién y desde cuándo | `feature/cajas-ocupadas` | 003, 005 | Hecha (PR #16) |

### Reunión N.° 2 (2026-11-23)
Specs que salen de los 55 puntos de la reunión N.° 2. El agrupado por tema, el reparto entre dos personas y las fases
están en [`../plan-reunion-2.md`](../plan-reunion-2.md). Todas están en **Propuesta**: se revisan, se toman sus
decisiones y se aprueban antes de escribir el plan.

| Spec | Fase | Qué entrega | Rama | Depende de | Responsable | Estado |
|---|---|---|---|---|---|---|
| [016-pos-ajustes-rapidos](016-pos-ajustes-rapidos/spec.md) | 1 | Sin últimos 4 dígitos, voucher opcional, método de pago obligatorio, pedidos eliminados visibles | `feature/pos-ajustes-rapidos` | 005, 006 | Persona 1 | Hecha (V40, PR #19) |
| [017-interfaz-general](017-interfaz-general/spec.md) | 1 | Punto de miles, sin flechas en números, sin scroll horizontal, pestañas fijas, una categoría abierta | `feature/interfaz-general` | 013, 014 | Persona 2 | Hecha (PR #20; arreglo de pestañas en #22) |
| [018-mi-turno-claro](018-mi-turno-claro/spec.md) | 1 | "Mi turno" sin "crear turno" con turno abierto, botón de cerrar, acciones en menú, revisión del flujo de cajas | `feature/mi-turno-claro` | 003, 015 | Persona 1 | Hecha (PR #22; falta revisión en Docker con el cajero) |
| [019-reorganizar-menu](019-reorganizar-menu/spec.md) | 1 | Reportes en su categoría (con el informe de compras) e Importación de datos en Configuración | `feature/reorganizar-menu` | 011, 013 | Persona 2 | Hecha (PR #23) |
| [020-empresa-sedes-simples](020-empresa-sedes-simples/spec.md) | 1 | Código de ingreso automático y sede con enlace de Google Maps | `feature/empresa-sedes-simples` | SaaS 2 | Persona 2 | Hecha (V41, PR #24) |
| [021-mi-turno-movimientos](021-mi-turno-movimientos/spec.md) | 2 | Facturas del turno con paginación, ingresos y egresos con categoría, esperado antes de cerrar | `feature/mi-turno-movimientos` | 018 | Persona 1 | Hecha (V42, PR #26) |
| [022-permisos-plantillas-rol](022-permisos-plantillas-rol/spec.md) | 2 | Permisos por acción, plantillas de rol, permisos sueltos y clave corta del personal | `feature/permisos-plantillas-rol` | SaaS 3 | Persona 2 | Hecha (V43 y V44, PR #27 y #28) |
| [023-cierre-por-denominacion](023-cierre-por-denominacion/spec.md) | 2 | Cierre contando billetes y monedas, vista de ventas por turno | `feature/cierre-por-denominacion` | 021 | Persona 1 | Mezclada (V45, PR #29); falta revisión en Docker |
| [024-planes-simbolicos](024-planes-simbolicos/spec.md) | 2 | Sin planes: los módulos se eligen al crear la empresa; las existentes con todos | `feature/cierre-por-denominacion` (con la 023 y la 027) | SaaS 2 | Persona 2 | Mezclada (V46, PR #29); falta revisión en Docker |
| [025-caja-con-permisos](025-caja-con-permisos/spec.md) | 3 | El dueño vende, cajas por persona, retiro y gasto sin PIN con permiso | `feature/cierre-por-denominacion` (con la 023, 024 y 027) | 022 | Persona 1 | Mezclada (V47 y V48, PR #29); revisada en Docker, falta aprobación |
| [026-producto-completo](026-producto-completo/spec.md) | 3 | Categoría y proveedor al vuelo, impuesto, unidad ajustable, stock máximo, proveedor y fecha del costo | `feature/cierre-por-denominacion` | 001 | Persona 2 | Mezclada (V49, PR #29); falta decidir la unidad con movimientos |
| [027-venta-agil](027-venta-agil/spec.md) | 3 | Categorías en el pedido y el POS, cliente y pago en un paso | `feature/cierre-por-denominacion` (con la 023) | 005, 016 | Persona 1 | Mezclada (PR #29); falta revisión en Docker |
| [028-variantes-producto](028-variantes-producto/spec.md) | 3 | Atributos y variantes con código, precio y stock propios | `feature/variantes-producto` | 026 | Persona 2 | Hecha (V50); falta revisión en Docker |
| [029-anular-factura-metodo-pago](029-anular-factura-metodo-pago/spec.md) | 3 | Anular factura de un turno cerrado y cambiar el método de pago | `feature/anular-factura-metodo-pago` | 006, 022 | Persona 1 | Hecha (V51); falta revisión en Docker |
| [030-venta-sin-stock](030-venta-sin-stock/spec.md) | 4 | Vender sin existencia con permiso, aviso y reporte | `feature/venta-sin-stock` | 004, 022 | Persona 1 | Hecha (V52); falta revisión en Docker |
| [031-proveedor-sin-perfil](031-proveedor-sin-perfil/spec.md) | 4 | Proveedor sin cuenta que cotiza con un enlace de un solo uso y anti-bot | `feature/proveedor-sin-perfil` | — | Persona 2 | Hecha (V54); falta revisión en Docker |
| [032-domicilios](032-domicilios/spec.md) | 4 | Venta a domicilio, domiciliarios, estados y dinero contraentrega | `feature/domicilios` | 004, 021 | Persona 1 | Hecha (V53); falta revisión en Docker |
| [033-solicitudes-compra-mejoradas](033-solicitudes-compra-mejoradas/spec.md) | 4 | Proveedor específico o abierta, historial y edición, alertas que bloquean | `feature/solicitudes-compra-mejoradas` | — | Persona 2 | Propuesta |
| [034-reportes-ventas-ampliados](034-reportes-ventas-ampliados/spec.md) | 4 | Ventas por categoría, empleado, producto, medio de pago, tipo de servicio y caja | `feature/reportes-ventas-ampliados` | 009, 019, 023, 032 | Persona 1 | Propuesta |
| [035-sedes-super-admin](035-sedes-super-admin/spec.md) | 4 | Grupo de empresas con un super administrador | `feature/sedes-super-admin` | 020, 022 | Persona 2 | Propuesta (aclarar D12) |
| [036-ajustes-prueba-usuarios](036-ajustes-prueba-usuarios/spec.md) | 4 | Ajustes de la prueba con usuarios: avisos al crear empresa, cantidad y existencia en el POS, detalle de venta en su fila, Inicio solo con categorías y pendientes | `fix/ajustes-prueba-usuarios` | 005, 012, 020, 024 | Persona 1 | En construcción |

Al terminar la 006 el negocio ya puede **vender y cuadrar caja**. De la 007 en adelante se suman crédito, márgenes y
control fino.

**Futuro (sin spec todavía):** factura electrónica DIAN; repuestos: compatibilidad por marca y modelo de vehículo y
referencias cruzadas; catálogos de proveedor en PDF leídos con IA; correo real por SMTP.

## Decisiones

| # | Pregunta | Decisión | Estado | Spec |
|---|---|---|---|---|
| D1 | ¿Se permite vender sin stock (stock negativo)? | **No.** Es la principal fuente de descuadres. | **Tomada** (2026-11-20, construida en V34) | 004 |
| D2 | ¿El precio de mostrador incluye IVA? | **Sí**, como es costumbre en Colombia; Java separa base e impuesto. | **Tomada** (construida en V29) | 001 |
| D3 | ¿Redondear el total en efectivo a $50? | **Sí**, guardando la diferencia en `redondeo` para que cuadre. | **Tomada** (2026-11-20, construida en V34) | 004 |
| D4 | ¿Crédito a clientes en la primera versión? | **Sí, con la 007**: clientes registrados, sin pasar el cupo, bloqueo por mora con excepción manual. | **Tomada** (2026-11-20) | 007 |
| D5 | ¿Roles nuevos `SUPERVISOR_VENTAS` y `VENDEDOR`? | **Sí**; el dueño puede asignar los dos a la misma persona en negocios pequeños. | **Tomada** (2026-11-19) | 001 |
| D6 | ¿Tolerancia de diferencia de caja sin aprobación? | $2.000, configurable por empresa. | **Tomada** (2026-11-19) | 003 |
| D7 | ¿Una caja puede descontar de varias bodegas? | **No**: una caja, una bodega. Los traslados ya existen. | **Tomada** (2026-11-19) | 003 |
| D8 | ¿Quién fija precios: solo el dueño o también el jefe de compras? | El dueño, con opción de delegarlo a `ADMIN_COMPRAS`. | **Tomada** (construida en V29) | 001 |
| D9 | ¿Precio distinto por caja y por unidad del mismo producto? | **Sí**, como pedía la spec original; hoy el precio se guarda solo en la unidad del producto. | **Tomada** (2026-11-19) | 001 |
| D10 | ¿Cómo son las contraseñas del personal y quién las ve? | Dueño y administradores: clave fuerte (12+, mayúscula, número, símbolo) y MFA. Personal: clave corta o PIN de 6 dígitos que asigna el jefe. Nadie **ve** una clave (se guardan cifradas): se **restablece** y queda auditado. | Tomada (2026-11-25, tal como está; se construye en la Parte B de la 022) | 022 |
| D11 | ¿Qué pasa con los planes? | Se quitan los planes. Al crear una empresa, el dueño de la plataforma marca en un panel los módulos que necesita; después los prende o apaga uno por uno. Las empresas que ya existían quedan con todos los módulos. Las tablas de planes se conservan sin uso. | Tomada (2026-11-25, por el equipo: sin planes, módulos por empresa) | 024 |
| D12 | ¿Las sedes pasan a ser empresas separadas con un super administrador? | Grupo de empresas aisladas con un super administrador que cambia de empresa sin volver a ingresar. Faltan las tres preguntas de la spec 035. | **Pendiente: aclarar** | 035 |
| D13 | ¿Se permite vender sin existencia? (revisa D1) | Sí, solo con permiso y motivo, con aviso y reporte. Falta elegir: stock negativo marcado o faltante pendiente sin bajar de cero. | Tomada (2026-11-26: faltante pendiente, el stock nunca baja de cero; permiso para Dueño, Administrador y Supervisor) | 030 |
| D14 | ¿El proveedor deja de tener cuenta? | Sí: cotiza con un enlace de un solo uso, solo el nombre obligatorio, límite de peticiones y anti-bot. El portal se deja de usar. | Tomada (2026-11-26: tal como está, portal retirado; anti-bot con Turnstile por HTTP; no espera a la 033) | 031 |
| D15 | ¿Retiros y gastos sin PIN? | Con el permiso, sin PIN; sin el permiso, PIN del supervisor como hoy. | Tomada (2026-11-25, tal como está) | 025 |
| D16 | ¿Se puede cambiar el método de pago de una factura? | Sí en facturas POS, con permiso y motivo, ajustando la caja; nunca en facturas electrónicas DIAN. | Tomada (2026-11-26, tal como está; permisos para Dueño, Administrador y Supervisor, el cajero con PIN) | 029 |
| D17 | ¿Se puede superar el stock máximo? | Sí, con alerta; no bloquea. | Tomada (2026-11-25, tal como está) | 026 |
| D18 | ¿Qué roles quedan? | Permisos por acción con plantillas: Dueño, Administrador, Cajero, Vendedor, Supervisor de ventas, Bodega, Compras y Contador/Auditor; cada usuario migra sin ganar ni perder acceso. | Tomada (2026-11-25, tal como está) | 022 |
| D19 | ¿Qué alcance tiene domicilios? | Venta tipo domicilio con dirección y envío, domiciliario, estados y pago contraentrega en la caja; sin mapa en vivo ni app. | Tomada (2026-11-26: tal como está; contraentrega como venta al despachar; envío como producto servicio) | 032 |
| D20 | ¿El cajero ve lo esperado antes de cerrar? (revisa el cierre ciego de la 003) | Cuenta primero y después ve lo esperado y la diferencia; quien tenga el permiso "ver esperado" lo ve siempre. | Tomada (2026-11-25, opción A: cierre ciego; mientras llega la 022 ven lo esperado SUPERVISOR_VENTAS y ADMIN_SISTEMA) | 021 |
| D21 | ¿Las alertas de presupuesto excedido y solicitud duplicada se pueden saltar con justificación? (revisa la 18-a) | Solo quien tenga el permiso `SOLICITUD_EXCEPCION` (por defecto el Dueño); los demás quedan bloqueados. | Recomendada | 033 |

Una decisión **Recomendada** pasa a **Tomada** cuando la aprueba el equipo; se anota aquí antes de aprobar la spec.

## Reserva de migraciones
Antes de crear una migración, la rama la anota aquí (en su primer commit) para que dos ramas no usen el mismo número.

| Versión | Rama | Qué |
|---|---|---|
| V25 | (en `develop`) | Informe de compras |
| V26 a V30 | (en `develop`, PR #27) | Reabastecimiento, inventario por sede, pedidos internos, precios de venta (V29), catálogos de proveedores |
| V31 | `feature/ventas-catalogo` | Completar la spec 001: módulos, roles y precio vigente único |
| V32 | `feature/ventas-clientes` | Clientes y consumidor final (spec 002) |
| V33 | `feature/caja-turnos` | Cajas, turnos, libro de caja, PIN y autorizaciones (spec 003) |
| V34 | `feature/ventas-pos` | La venta: venta, líneas, pagos, pedido de venta, límite de descuento por rol y consecutivo por caja (spec 004) |
| V35 | `feature/ventas-pos` | Anulación y devolución: tipos de movimiento, devoluciones, saldo a favor y bodega de averías (spec 006) |
| V36 | `feature/ventas-pos` | Costo promedio del producto y costo de cada movimiento (spec 008) |
| V37 | `feature/ventas-pos` | Cartera: cuentas por cobrar, abonos, saldo a favor como medio de pago y excepción de crédito (spec 007) |
| V38 | `feature/ventas-reportes` | Cuadre diario automático: `cuadre_diario` (spec 009) |
| V39 | `feature/ventas-endurecimiento` | Umbrales de anulaciones y descuentos por cajero y registro de alertas (spec 010) |
| V40 | `feature/pos-ajustes-rapidos` | Quién, cuándo y por qué se descartó un pedido de venta (spec 016) |
| V41 | `feature/empresa-sedes-simples` | Enlace de Google Maps de la sede, `sede.enlace_mapa` (spec 020) |
| V42 | `feature/mi-turno-movimientos` | Categorías de ingresos y egresos de caja y `movimiento_caja.categoria_id` (spec 021) |
| V43 | `feature/permisos-plantillas-rol` | Catálogo de permisos, roles como plantillas, `rol_permiso` de los 12 roles y `usuario_permiso` (spec 022, Parte A) |
| V44 | `feature/permisos-parte-b` | Clave corta del personal y roles propios por empresa (spec 022, Parte B) |
| V45 | `feature/cierre-por-denominacion` | `turno_caja.conteo_denominaciones`: conteo del efectivo por billete y moneda (spec 023) |
| V46 | `feature/cierre-por-denominacion` | Sin planes: se borran las excepciones de `empresa_modulo` y todas las empresas quedan con todos los módulos (spec 024) |
| V47 | `feature/cierre-por-denominacion` | Caja con permisos: el Dueño opera el turno y cobra, permisos de retiro, gasto e ingreso sin PIN y `caja_usuario` (spec 025) |
| V48 | `feature/cierre-por-denominacion` | El Dueño y el Administrador cotizan el carrito para entrar al Punto de venta (spec 025) |
| V49 | `feature/cierre-por-denominacion` | Producto completo: proveedor y fecha del costo de referencia, NIT de proveedor opcional y evento `STOCK_SOBRE_MAXIMO` (spec 026) |
| V50 | `feature/variantes-producto` | Variantes de producto: `producto.agrupador_id`, `es_agrupador`, `atributos` y `combinacion` (spec 028) |
| V51 | `feature/anular-factura-metodo-pago` | Cambio del método de pago: `venta_pago_cambio`, tipo de caja `AJUSTE_PAGO`, acción `CAMBIAR_PAGO` y permisos de anular de otro turno y cambiar pago (spec 029) |
| V52 | `feature/venta-sin-stock` | Vender sin existencia: `venta_sin_existencia` (faltante pendiente), permiso `VENTA_SIN_EXISTENCIA` y evento de aviso (spec 030) |
| V53 | `feature/domicilios` | Domicilios: `domicilio`, `domicilio_evento`, `producto.es_servicio`, `venta.tipo_servicio`, medio `CONTRAENTREGA`, caja `COBRO_DOMICILIO` y permisos (spec 032) |
| V54 | `feature/proveedor-sin-perfil` | Proveedor sin perfil: `enlace_cotizacion` y usuarios PROVEEDOR desactivados (spec 031) |
| V55 en adelante | libre | Se reserva al empezar cada spec |

**Numeración propuesta en los planes 018 a 035 (no es reserva):** V41 (020), V42 (021), V43 y V44 (022), V45 (023),
V46 (025), V47 (026), V48 (028), V49 (029), V50 (030), V51 (031), V52 (032), V53 (033), V54 (035). Las specs 018, 019,
024, 027 y 034 no llevan migración. Cada rama reserva su número en esta tabla en su primer commit; si otra rama ya lo tomó,
usa el siguiente libre y corrige su plan.

## Riesgos generales

| Riesgo | Cómo se mitiga | Spec |
|---|---|---|
| El POS debe ser muy rápido | Búsqueda indexada por código y código de barras; el carrito vive en la pantalla y solo hay una petición al cobrar. | 004, 005 |
| Sin internet no se puede vender | Fuera de estas specs. Se documenta como limitación; un modo sin conexión sería una spec aparte. | — |
| Bloqueos de la base con muchas cajas | Bloqueo solo de las filas de stock de la venta, en orden de id, y transacciones cortas. | 004 |
| La DIAN cambia el esquema de facturación | El modelo de venta ya guarda base, impuesto por línea y datos del cliente; la factura será una capa encima. | futuro |
| Dos ramas construyen lo mismo o usan la misma migración | Una spec, una rama y una persona responsable; migración reservada en esta página. | todas |
