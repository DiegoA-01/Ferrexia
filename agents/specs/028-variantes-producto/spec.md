# 028 - Variantes de producto

**Estado:** Propuesta · **Rama:** `feature/variantes-producto` · **Depende de:** 026 · **Responsable:** Persona 2 ·
**Puntos de la reunión 2:** 32 · **Migración:** sí (se reserva al empezar)

## Objetivo
Que un mismo producto pueda tener opciones (medida, color, calibre, presentación) sin crear a mano un producto por cada
combinación, y que cada opción tenga su propio stock, código y precio.

## Historias
- Como administrador, quiero crear "Tornillo drywall" con las medidas 1", 1 1/2" y 2" en un solo formulario.
- Como cajero, quiero escanear el código de una variante y que se venda esa.
- Como bodeguero, quiero ver el stock de cada variante.

## Comportamiento
- Al crear o editar un producto se pueden agregar **atributos** (por ejemplo Medida y Color) con sus valores; el
  sistema arma las combinaciones y cada una queda como **variante** con su código, código de barras, costo, precio,
  mínimo y máximo.
- Cada variante tiene su propio stock y su kardex; el stock solo cambia por los movimientos de inventario, como hoy.
- En ventas, compras, recepción e inventario se elige la variante, no el producto general.
- Un producto sin atributos funciona exactamente como hoy.

## Criterios de aceptación
- [ ] Se crea un producto con atributos y quedan sus variantes.
- [ ] Cada variante tiene código, precio y stock propios.
- [ ] Vender, comprar y recibir una variante mueve solo su stock.
- [ ] Los productos sin variantes no cambian.
- [ ] Prueba de fuga entre empresas para atributos y variantes.

## Fuera de alcance
- Imágenes por variante.
- Carga masiva de variantes por Excel (se puede sumar después).
