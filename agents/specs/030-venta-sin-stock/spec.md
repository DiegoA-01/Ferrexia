# 030 - Vender sin stock con permiso y reporte

**Estado:** Propuesta · **Rama:** `feature/venta-sin-stock` · **Depende de:** 004, 022 · **Responsable:** Persona 1 ·
**Puntos de la reunión 2:** 19 · **Migración:** sí (se reserva al empezar) · **Decisión:** D13

## Objetivo
A veces el cliente pide algo que no aparece con existencia: no se ha registrado la entrada o el negocio lo consigue en
otro lado. Hoy el sistema no deja venderlo (decisión D1). Esta spec permite hacerlo con control y deja un reporte para
corregir el inventario.

## Historias
- Como cajero con permiso, quiero vender un producto aunque el sistema diga que no hay existencia.
- Como dueño, quiero un reporte de lo que se vendió sin existencia, para corregir el inventario o comprarlo.

## Comportamiento
- **Con permiso (D13):** si la persona tiene el permiso "vender sin existencia", al agregar un producto sin stock
  suficiente sale un aviso y puede seguir, con un motivo corto ("no registrado", "se consigue afuera"). Sin el permiso,
  la venta se rechaza como hoy.
- **Qué pasa con el stock:** se define en el plan entre dos opciones y se anota en D13: (a) el stock queda negativo y
  se marca para revisar, o (b) el stock no baja de cero y se registra un faltante pendiente que se descuenta cuando
  entre la mercancía.
- **Reporte "Ventas sin existencia":** en Reportes, por fecha, producto, cajero y caja, con el motivo y si ya se
  regularizó.
- **Aviso:** cada venta sin existencia avisa a compras e inventario.

## Criterios de aceptación
- [ ] Con permiso se vende sin existencia indicando el motivo; sin permiso se rechaza.
- [ ] El stock se maneja según la opción aprobada en D13.
- [ ] El reporte muestra cada venta sin existencia con su estado.
- [ ] Compras e inventario reciben el aviso.
- [ ] Prueba de fuga entre empresas para el registro nuevo.

## Fuera de alcance
- Crear una solicitud de compra automática (se puede sumar después).
