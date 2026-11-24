# 011 - Menú lateral por categorías

**Estado:** Hecha (2026-11-23, sin PR todavía) · **Rama:** `feature/menu-por-categorias` ·
**Depende de:** —

## Objetivo
Que el menú lateral de la empresa (`/app`) agrupe las pantallas por lo que la persona va a hacer: vender, comprar,
manejar el inventario, consultar reportes, administrar a la gente y configurar la empresa. Hoy hay 32 opciones en
cuatro grupos (Compras, Inventario, Ventas y Gestión). Los reportes están repartidos en tres grupos, Proveedores
aparece dentro de Inventario y "Gestión" mezcla tableros, precios, usuarios y configuración.

## Historias
- Como dueño de la ferretería, quiero encontrar todos los reportes en un solo lugar.
- Como jefe de compras, quiero ver a los proveedores junto con el resto de las compras.
- Como administrador, quiero ver en un grupo aparte lo que tiene que ver con las personas (usuarios, roles y
  delegaciones).

## Agrupación acordada
1. **Inicio** (sola, arriba).
2. **Ventas:** Punto de venta, Mi turno, Pedidos de venta, Ventas, Clientes, Cartera, Cajas y turnos.
3. **Compras:** Solicitudes, Pedidos internos, Cotizaciones con IA, Adjudicaciones, Órdenes y pagos, Pagos, Recepción,
   Proveedores, Catálogos de proveedores.
4. **Inventario:** Productos y stock, Movimientos de stock, Despachos, Plan de reabastecimiento.
5. **Reportes:** Tablero del dueño, Reportes de compras (antes "Reportes"), Reportes de ventas, Rotación de productos,
   Informe de compras, Precios y margen.
6. **Gestión humana:** Usuarios y roles, Delegaciones.
7. **Gestión:** Configuración, Avisos y correos, Auditoría.

## Criterios de aceptación
- [x] El menú muestra los siete grupos en ese orden, con las opciones en el orden de la lista.
- [x] Ninguna opción gana ni pierde acceso: cada una conserva sus roles y su módulo actuales.
- [x] Ninguna opción desaparece ni se duplica: siguen siendo 32, con las mismas rutas.
- [x] Un grupo en el que la persona no ve ninguna opción no aparece (por ejemplo, el cajero no ve "Gestión"). Como
  "Delegaciones" la ve todo usuario interno, "Gestión humana" aparece para todos; en el cajero solo con esa opción.
- [x] La opción "Reportes" pasa a llamarse "Reportes de compras"; la ruta `/app/reportes` no cambia.
- [x] Los contadores (solicitudes pendientes y productos bajo el mínimo) siguen en sus opciones.

> Verificado el 2026-11-23: `menu.spec.ts` compara las 32 opciones contra la tabla de permisos anterior y
> `shell.spec.ts` revisa los grupos del dueño y del cajero (208 pruebas en verde, build sin advertencias). En Docker se
> revisó el menú con el jefe de compras, el cajero y el auditor; el dueño entra con MFA y quedó cubierto por la prueba.

## Fuera de alcance
- Funciones nuevas de gestión humana (empleados, nómina, horarios): serían specs aparte.
- Unir "Órdenes y pagos" con "Pagos".
- Cambiar rutas, permisos, el backend o la página de Inicio.
- El menú del portal del proveedor y el de la consola de plataforma.
