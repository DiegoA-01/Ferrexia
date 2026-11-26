# 028 - Variantes de producto

**Estado:** Hecha (2026-11-26; falta revisión en Docker) · **Rama:** `feature/variantes-producto` · **Depende de:** 026 · **Responsable:** Persona 2 ·
**Puntos de la reunión 2:** 32 · **Migración:** V50

## Objetivo
Que un mismo producto pueda tener opciones (medida, color, calibre, presentación) sin crear a mano un producto por cada
combinación, y que cada opción tenga su propio stock, código y precio.

## Decisiones (2026-11-26)
- **Cada variante es un producto normal** agrupado bajo un **producto agrupador**. El agrupador no tiene stock, no se
  vende, no se compra ni se mueve: solo agrupa y define los atributos. Ventas, compras, kardex y precios no cambian.
- **Unidad, categoría e impuesto se heredan y se propagan:** se definen en el agrupador y, al editarlo, se copian a todas
  sus variantes. La unidad solo cambia si ninguna variante tiene movimientos (la misma regla de hoy). Una variante no
  puede tener unidad, categoría o impuesto distintos de los de su agrupador.
- El nombre de la variante lo arma el sistema: nombre del agrupador y sus valores ("Tornillo drywall · 1 1/2"). Así
  sale con su nombre completo en búsquedas, selectores, reportes y facturas sin tocar esos módulos.
- Los **nombres** de los atributos quedan fijos al crear el producto; después solo se agregan o quitan **valores**.
- Una sola entrega (backend y frontend juntos).

## Historias
- Como administrador, quiero crear "Tornillo drywall" con las medidas 1", 1 1/2" y 2" en un solo formulario.
- Como cajero, quiero escanear el código de una variante y que se venda esa.
- Como bodeguero, quiero ver el stock de cada variante.

## Comportamiento
- Al crear un producto se puede activar "Tiene variantes" y agregar **atributos** (por ejemplo Medida y Color, hasta 3)
  con sus valores; el sistema arma las combinaciones (hasta 100) y cada una queda como **variante** con su código,
  código de barras, costo, precio (en la lista General), mínimo y máximo.
- Cada variante tiene su propio stock y su kardex; el stock solo cambia por los movimientos de inventario, como hoy.
- En ventas, compras, recepción e inventario se elige la variante, no el producto general: el agrupador no sale en los
  selectores ni en el punto de venta, y usarlo da 409 `PRODUCTO_AGRUPADOR`.
- Editar los valores: agregar un valor crea las variantes que faltan; quitar un valor **desactiva** sus variantes (nada
  se borra); volver a ponerlo las reactiva. Desactivar el agrupador desactiva sus variantes.
- En la pantalla de inventario el agrupador sale como fila desplegable con sus variantes y el stock total (suma
  informativa que calcula el backend).
- Un producto sin atributos funciona exactamente como hoy.

## Criterios de aceptación
- [x] Se crea un producto con atributos y quedan sus variantes (`VarianteServiceTest`, prueba de fuga contra MySQL).
- [x] Cada variante tiene código, precio y stock propios (es un producto: precio en la General, stock y kardex propios).
- [x] Vender, comprar y recibir una variante mueve solo su stock (todo pasa por `MovimientoInventarioService` con el
  id de la variante; la prueba de fuga mueve una variante y la otra queda en 0; el general da 409). Falta verlo en
  Docker de punta a punta.
- [x] Los productos sin variantes no cambian (toda la suite anterior sigue en verde).
- [x] Prueba de fuga entre empresas para atributos y variantes.

## Fuera de alcance
- Imágenes por variante.
- Carga masiva de variantes por Excel (se puede sumar después).
- Cambiar los nombres de los atributos o agregar un atributo nuevo a un producto que ya tiene variantes.
