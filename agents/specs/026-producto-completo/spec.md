# 026 - Crear un producto completo sin salir del formulario

**Estado:** Propuesta · **Rama:** `feature/producto-completo` · **Depende de:** 001 · **Responsable:** Persona 2 ·
**Puntos de la reunión 2:** 27, 29, 30, 31, 33, 35 · **Migración:** posible (se reserva al empezar) · **Decisión:** D17

## Objetivo
Que crear un producto se haga en un solo paso, con todo lo que el negocio necesita: categoría y proveedor aunque aún no
existan, impuesto, unidad de medida ajustable, stock máximo y de dónde salió el costo.

## Historias
- Como administrador, quiero crear una categoría o un proveedor nuevo desde el formulario del producto, sin perder lo
  que ya escribí.
- Como administrador, quiero elegir el impuesto del producto al crearlo.
- Como administrador, quiero ajustar la unidad de medida del producto.
- Como administrador, quiero poner el stock máximo y saber qué pasa si se supera.
- Como administrador, quiero anotar quién fue el proveedor y la fecha del costo de referencia.

## Comportamiento
- **Categoría y proveedor al vuelo:** junto al campo de categoría y al de proveedor hay un botón "Nueva". Se abre un
  formulario corto encima, se guarda, queda elegido y el formulario del producto sigue con lo escrito.
- **Impuesto:** el formulario pide el impuesto (IVA 19 %, 5 %, exento, excluido) con los impuestos que ya maneja la
  spec 001.
- **Unidad de medida ajustable:** se puede cambiar la unidad del producto mientras no tenga movimientos de inventario;
  si ya los tiene, el sistema lo explica y ofrece convertir dentro de la misma magnitud. (El detalle exacto se revisa en
  el plan, porque hoy la unidad no cambia si hay movimientos.)
- **Stock máximo (D17):** el máximo es opcional. Se puede superar al recibir mercancía, pero sale una alerta y el
  producto aparece en un aviso de "sobre el máximo".
- **Proveedor y fecha de referencia:** el producto guarda el proveedor y la fecha de su costo de referencia; la
  recepción los actualiza junto con el costo.

## Criterios de aceptación
- [ ] Se crean categoría y proveedor desde el producto sin perder lo escrito.
- [ ] El producto se guarda con su impuesto.
- [ ] La unidad de medida se puede ajustar según la regla acordada.
- [ ] Superar el máximo no bloquea y genera una alerta.
- [ ] El producto muestra el proveedor y la fecha del costo de referencia.
- [ ] Prueba de fuga entre empresas para los datos nuevos del producto.

## Fuera de alcance
- Variantes del producto (spec 028).
- Carga masiva de estos datos nuevos (se puede sumar después).
