# Revisión en Docker (2026-11-27)

Stack `ferrexia` en http://localhost:8088, con `develop` = `a3a7147` (specs 000 a 036) y la demo recién sembrada.
Cuentas y claves: `credenciales-docker.local.txt` (no se sube a git). Código de empresa: el que dice ese archivo.
El Dueño de la ferretería y el dueño del software piden configurar el segundo factor en el primer ingreso.

Marca `[x]` lo que funcione y anota debajo lo que falle. Al terminar, se marcan los criterios de cada spec.

## Antes de empezar
- [ ] Entrar como Dueño de la ferretería (configurar el segundo factor) y como cajero.
- [ ] El cajero abre turno en la Caja 1 mostrador.

## 018 · Mi turno claro (cajero y supervisor)
- [ ] El cajero ve en "Mi turno" el estado claro del turno, el botón Cerrar turno y las acciones en el selector.
- [ ] El supervisor revisa el flujo de cajas (`agents/specs/018-mi-turno-claro/revision-flujo-cajas.md`).

## 021 · Mi turno: facturas, ingresos y gastos (cajero y supervisor)
- [ ] "Ver facturas del turno": paginadas, reimprimir y detalle.
- [ ] Registrar un gasto y un ingreso con categoría.
- [ ] Cierre ciego: el cajero cuenta antes de ver lo esperado; el supervisor sí ve lo esperado.

## 022 · Permisos, roles propios y PIN
- [ ] El Dueño crea un rol propio (por ejemplo "Cajero nocturno") con algunos permisos y se lo da a un usuario.
- [ ] Ese usuario entra con un PIN de 6 dígitos y ve solo lo que dan sus permisos.
- [ ] Quitar un permiso suelto a una persona y comprobar que pierde esa opción.
- [ ] Los usuarios de la demo ven lo mismo que antes de la 022.

## 023 · Cierre contando billetes y monedas
- [ ] Cerrar el turno contando por denominación; el total coincide con lo contado.
- [ ] Reporte de ventas por turno.

## 024 · Sin planes
- [ ] En la consola de plataforma, crear una empresa eligiendo sus módulos; apagar y prender un módulo.

## 025 · Caja con permisos (falta tu aprobación)
- [ ] El Dueño abre turno y vende.
- [ ] Una caja asignada a una persona solo la abre esa persona.
- [ ] Con el permiso, retiro y gasto sin PIN; sin el permiso, pide PIN del supervisor.

## 026 · Producto completo (falta decidir la unidad con movimientos)
- [ ] Crear un producto con impuesto, proveedor y fecha del costo.
- [ ] Alta de proveedor al vuelo desde el producto.
- [ ] Aviso de stock máximo superado.
- [ ] Intentar cambiar la unidad de un producto con movimientos y anotar qué pasa (hoy da 409 `UNIDAD_EN_USO`).

## 027 · Venta ágil
- [ ] Categorías en el punto de venta y en el pedido.
- [ ] Cliente y pago en un solo paso.

## 028 · Variantes de producto
- [ ] Crear un producto con variantes (talla, color) y ver la tabla de combinaciones.
- [ ] Comprar y recibir una variante, y venderla por código de barras en el punto de venta.
- [ ] El inventario muestra el producto general desplegable.

## 029 · Anular factura de otro turno y cambiar el pago
- [ ] Cambiar el método de pago de una factura en "Facturas del turno" (el cajero con PIN del supervisor).
- [ ] Como supervisor, anular una factura de un turno ya cerrado ("Anular (turno cerrado)" en Ventas).

## 030 · Vender sin existencia
- [ ] Vender más de lo que hay con "¿Vender igual?" y motivo (Dueño, Administrador o Supervisor).
- [ ] El faltante aparece en Reportes de ventas › "Sin existencia" y se regulariza solo al recibir mercancía.

## 031 · Proveedor sin cuenta (enlace para cotizar)
- [ ] Crear un proveedor solo con el nombre.
- [ ] Abrir una RFQ e ir a "Enlaces para cotizar": copiar el enlace.
- [ ] Abrir el enlace en una ventana privada, cotizar (el anti-bot de prueba pasa solo) y enviar.
- [ ] Volver a abrir el enlace: dice que ya no está disponible.
- [ ] En la orden: "Respuesta del proveedor" (aceptó, preparando, despachó) y "Enviar al proveedor".

## 032 · Domicilios (vendedor, cajero y domiciliario a 360 px)
- [ ] Venta a domicilio contraentrega con dirección y envío.
- [ ] Asignar domiciliario, marcar en camino y entregado.
- [ ] El cajero cobra la contraentrega en su turno.
- [ ] Un domiciliario con el permiso suelto `DOMICILIO_REPARTIR` ve solo los suyos.

## 034 · Reportes de ventas ampliados
- [ ] Reportes › Ventas › "Ver por: Tipo de servicio".
- [ ] El Dueño ve costo y margen; el cajero no (ni en el Excel o PDF).

## 035 · Sedes como empresas con super administrador
- [ ] En la consola de plataforma: crear una segunda empresa, un grupo con las dos y su super administrador.
- [ ] Entrar en `/grupo/ingreso` (segundo factor), ver las dos empresas y el tablero.
- [ ] "Entrar" a una empresa y volver con "Cambiar de empresa".
- [ ] Un traslado de la ferretería a la segunda empresa (mismo código de producto en las dos): enviar, recibir con
  un faltante, y otro traslado anulado.
- [ ] El administrador de la empresa no puede cambiar la clave del usuario del super administrador (409).

## 036 · Ajustes de la prueba con usuarios
- [ ] Revisar los puntos de `agents/specs/036-ajustes-prueba-usuarios/tasks.md`.

## Arreglos del 2026-11-27
- [ ] El cajero ve en Ventas las ventas que él cobró y las filtra por fecha y estado.
- [ ] En la orden de compra, los botones de la factura en revisión salen según los permisos.

## Anotaciones
