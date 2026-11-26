# 030 - Vender sin stock con permiso y reporte

**Estado:** Hecha (2026-11-26; falta revisión en Docker) · **Rama:** `feature/venta-sin-stock` · **Depende de:** 004, 022 · **Responsable:**
Persona 1 · **Puntos de la reunión 2:** 19 · **Migración:** V52 · **Decisión:** D13 (tomada)

## Objetivo
A veces el cliente pide algo que no aparece con existencia: no se ha registrado la entrada o el negocio lo consigue en
otro lado. Hoy el sistema no deja venderlo (decisión D1). Esta spec permite hacerlo con control y deja un reporte para
corregir el inventario.

## Decisiones (2026-11-26)
- **D13: faltante pendiente.** El stock nunca baja de cero: se descuenta lo que haya y la diferencia queda como
  **faltante pendiente**. Cuando entra mercancía de ese producto a esa bodega (recepción, ajuste, traslado, devolución o
  anulación), el faltante se descuenta solo, del más viejo al más nuevo. La regla "el stock solo cambia por
  movimiento_inventario y nunca queda negativo" sigue igual.
- **Permiso nuevo `VENTA_SIN_EXISTENCIA`** para Dueño, Administrador y Supervisor de ventas (al cajero se le puede sumar
  como permiso suelto). El aviso va a compras (`ADMIN_COMPRAS`) y a bodega (`ALMACENISTA`).

## Historias
- Como cajero con permiso, quiero vender un producto aunque el sistema diga que no hay existencia.
- Como dueño, quiero un reporte de lo que se vendió sin existencia, para corregir el inventario o comprarlo.

## Comportamiento
- **Con permiso:** si la persona tiene el permiso, al cobrar un producto sin stock suficiente el punto de venta avisa
  ("hay 2") y deja seguir con un motivo corto ("No registrado", "Se consigue afuera", "Otro"). Sin el permiso, la venta
  se rechaza como hoy.
- **Stock:** sale lo que hay; la diferencia queda como faltante pendiente de esa venta, producto y bodega, y se
  regulariza sola cuando entra mercancía (parcial o total).
- **Anulación:** al anular una venta con faltante pendiente, el faltante queda anulado y solo vuelve al stock lo que sí
  salió. **Devolución:** lo devuelto primero descuenta el faltante pendiente de esa venta y solo el resto entra al stock.
- **Reporte "Ventas sin existencia":** en Reportes, por fecha, producto y estado (pendiente, regularizada, anulada),
  con la venta, el cajero, la caja, el motivo, lo pendiente y cuándo se regularizó.
- **Aviso:** cada venta sin existencia avisa a compras y bodega.

## Criterios de aceptación
- [x] Con permiso se vende sin existencia indicando el motivo; sin permiso se rechaza (403; sin marcar sigue el 409 y
  sin motivo da 400).
- [x] El stock se maneja según la opción aprobada en D13 (nunca baja de cero; el faltante se regulariza solo, parcial y
  total, al entrar mercancía; la anulación y la devolución lo descuentan).
- [x] El reporte muestra cada venta sin existencia con su estado (pendiente, regularizada o anulada).
- [x] Compras e inventario reciben el aviso (`VENTA_SIN_EXISTENCIA` a `ADMIN_COMPRAS` y `ALMACENISTA`).
- [x] Prueba de fuga entre empresas para el registro nuevo.

## Fuera de alcance
- Crear una solicitud de compra automática (se puede sumar después).
- Regularizar a mano un faltante (por ejemplo, el de algo que se consiguió afuera): por ahora se ve en el reporte y se
  corrige con un ajuste de inventario, que lo regulariza.
