# 017 - Ajustes generales de la interfaz

**Estado:** Propuesta · **Rama:** `feature/interfaz-general` · **Depende de:** 013, 014 · **Responsable:** Persona 2 ·
**Puntos de la reunión 2:** 16, 25, 26, 38, 41

## Objetivo
Corregir detalles que se repiten en todas las pantallas: cifras difíciles de leer, flechas en los campos de número que
cambian valores sin querer, scroll horizontal, pestañas que se pierden al bajar y un menú con varias categorías
abiertas a la vez. Se hace primero porque toca estilos y componentes que usan todas las demás specs.

## Historias
- Como usuario, quiero leer y escribir las cifras con punto de miles ("1.250.000"), para no equivocarme.
- Como usuario, no quiero flechas en los campos de número, porque con la rueda del mouse cambio el valor sin darme
  cuenta.
- Como usuario de celular, quiero que ninguna pantalla se desplace de lado.
- Como usuario, quiero que las pestañas de la categoría sigan a la vista cuando bajo en una pantalla larga.
- Como usuario, quiero que en el menú lateral solo haya una categoría abierta a la vez.

## Comportamiento
- **Punto de miles:** todas las cifras de dinero y cantidades grandes se muestran con punto de miles y coma decimal
  (formato colombiano), en pantallas, tablas, tiquetes y exportaciones visibles. Al escribir una cifra el campo pone el
  punto de miles mientras se escribe, y lo que se envía al backend es el número limpio.
- **Sin flechas:** ningún campo numérico de la aplicación muestra las flechas de subir y bajar, y la rueda del mouse no
  cambia su valor.
- **Sin scroll horizontal:** ninguna pantalla tiene desplazamiento lateral en celular (desde 360 px), tableta ni
  escritorio. Las tablas anchas se acomodan (columnas que bajan, tarjetas en celular) en vez de desbordarse.
- **Pestañas fijas:** al bajar en una pantalla, la fila de pestañas de la categoría (spec 014) queda pegada arriba en
  una versión más delgada, sin tener que volver arriba para cambiar de pantalla.
- **Una categoría abierta:** en el menú lateral, abrir una categoría recoge la que estaba abierta. Esto **reemplaza**
  la regla de la spec 013 que permitía varias abiertas.

## Criterios de aceptación
- [ ] Todas las cifras de dinero se ven con punto de miles.
- [ ] Al escribir una cifra se pone el punto de miles y el valor guardado es correcto.
- [ ] Ningún campo numérico tiene flechas ni cambia con la rueda del mouse.
- [ ] Ninguna pantalla tiene scroll horizontal a 360 px, 768 px y 1280 px (revisado pantalla por pantalla).
- [ ] Las pestañas de la categoría quedan visibles al bajar.
- [ ] En el menú lateral nunca hay dos categorías abiertas.

## Fuera de alcance
- Cambiar la paleta, la tipografía o el orden del menú.
- Mover pantallas de categoría (spec 019).
