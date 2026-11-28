# 038 - Unidades según el material

**Estado:** Hecha (2026-11-28) · **Rama:** `fix/grupos-cancelar-eliminar` (con el arreglo de grupos) · **Depende de:** 026
· **Responsable:** Persona 1 · **Migración:** V59 · **Decisión:** D22

## Objetivo
Que cada producto se maneje en la unidad de su material (el cable por metros, la arena por metros cúbicos) y que la unidad
se pueda corregir aunque el producto ya tenga movimientos, sin descuadrar el inventario ni los costos.

## Comportamiento
1. **Unidades nuevas:** metro cúbico (m³ = 1.000 litros), metro cuadrado (m², medida nueva "área"), tonelada
   (1.000 kg), libra (0,4536 kg) y galón (3,785 litros).
2. **La categoría dice qué mide su material (opcional):** unidades, longitud, área, volumen o peso. Se elige al crear la
   categoría desde el producto ("Nueva") o al lado de la unidad para una categoría que ya existe (quien gestiona el
   catálogo base).
3. **Formulario del producto:** si la categoría dice qué mide, la unidad ofrece solo las de esa medida; con "Ver todas
   las unidades" se puede elegir cualquiera (las dos opciones). Sin medida en la categoría se ofrecen todas.
4. **Cambiar la unidad de un producto con movimientos:** solo a otra unidad que mida lo mismo (por ejemplo
   centímetros a metros, unidades a docenas). En la misma operación se convierte todo lo guardado en la unidad del
   producto: stock por bodega, kárdex (cantidad, stock resultante y costo), mínimo y máximo (del producto y por sede),
   costo de referencia y promedio, faltantes de venta sin existencia, líneas de venta y devolución, despachos, órdenes de
   compra, recepciones, precios y topes de contrato y pedidos internos. Lo que guarda su propia unidad (solicitudes, RFQ,
   pedidos de venta, precios de venta) no cambia.
5. **Cambiar a una unidad que mide otra cosa con movimientos (por ejemplo, el cable creado por unidades y que es por
   metro):** el formulario pide la equivalencia ("1 UND equivale a ___ M") y con ella se convierte todo lo del punto 4.
   Además, lo que guarda su propia unidad pasa a la medida nueva: el precio de venta de la unidad del producto pasa a la
   nueva (los de otras unidades de la medida vieja, como la caja, dejan de estar vigentes) y las solicitudes de compra,
   pedidos de venta y despachos cambian su cantidad y unidad. Las ventas ya hechas conservan la unidad en que se
   vendieron. No se permite si el producto está en una RFQ en curso.
6. **Vender en otra unidad (punto de venta):** cada línea del carrito tiene un selector con las unidades que miden lo
   mismo que la del producto (el cable en metros o en centímetros, el tornillo por unidad o por docena). Si esa unidad
   tiene precio propio (D9, por ejemplo la caja) se usa ese; si no, el servidor lo calcula desde el precio de la unidad
   del producto (el metro a $3.700 deja el centímetro a $37). El aviso de existencia compara en la unidad del producto.
7. **No se permite:** pasar a una unidad que mide otra cosa sin decir la equivalencia, cambiar la de un producto general con
   variantes, ni la de un producto que aparece en traslados entre empresas del grupo (la otra empresa lo registra en la
   unidad actual).

## Criterios de aceptación
- [x] Existen m³, m², tonelada, libra y galón.
- [x] La categoría puede decir qué mide y el producto ofrece sus unidades, con la opción de ver todas.
- [x] Con movimientos, el cambio a una unidad de la misma medida convierte stock, kárdex y costos (probado en MySQL real:
      24 UND → 2 DOC y $500 → $6.000, y de vuelta).
- [x] Con movimientos, el cambio a otra medida sin equivalencia se rechaza con `UNIDAD_EN_USO`; con la equivalencia
      convierte stock y precios (probado en MySQL real: 24 UND → 24 M con 1 UND = 1 M, el precio pasa al metro y el de la
      docena deja de estar vigente).
- [x] En el punto de venta se elige la unidad de cada línea y el precio sale de la unidad del producto si no tiene uno
      propio (probado en MySQL real: 1 docena de $11.930 la unidad = $143.160).

## Fuera de alcance
- Cambiar la unidad de un producto general y sus variantes a la vez.
- Unidades por empresa (el catálogo de unidades sigue siendo común).
