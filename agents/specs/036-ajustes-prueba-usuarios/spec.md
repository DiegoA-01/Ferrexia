# 036 - Ajustes de la prueba con usuarios

**Estado:** En construcción (2026-11-26) · **Rama:** `fix/ajustes-prueba-usuarios` · **Depende de:** 005, 012, 020, 024
· **Responsable:** Persona 1 · **Origen:** prueba del sistema en Docker con cada rol (2026-11-26) · **Migración:** no

## Objetivo
Corregir lo que se vio al usar el sistema con cada rol: formularios que no dicen por qué no guardan, el carrito del POS
que borra el producto al editar la cantidad, el detalle de las ventas que se abre fuera de la vista y un Inicio con
demasiadas cosas.

## Comportamiento
1. **Crear empresa (consola de la plataforma):** si algún campo está vacío o mal (por ejemplo la contraseña inicial del
   administrador), al oprimir "Crear empresa" sale un aviso con lo que hay que corregir y cada campo malo muestra su
   mensaje debajo. Lo mismo si no hay módulos marcados.
2. **Cantidad en el POS:** se puede borrar la cantidad para escribir otra sin que el producto se quite del carrito. La
   línea solo se quita con el botón de quitar. Mientras la cantidad está vacía no se puede cobrar.
3. **Cantidad sobre la existencia:** si la cantidad pasa la existencia conocida del producto, la fila se pinta de rojo
   y dice cuánto hay. No bloquea: al cobrar sigue la regla de la spec 030 (vender sin existencia con permiso).
4. **Detalle de la venta:** al oprimir "Detalle" se despliega en la misma fila de la lista, sin tener que bajar.
5. **Inicio:** solo muestra las categorías y "Requiere tu atención". Las cifras, gráficas y listas que ya están en su
   categoría salen del Inicio. Cada categoría con algo pendiente lo dice en su tarjeta (por ejemplo "2 por aprobar").
6. **Periodo (Este mes, Trimestre, Año):** sale del Inicio; queda solo en las pantallas de reportes que lo usan.

Además se corrige un sobrante de la spec 024: la barra lateral de la empresa ya no muestra el plan ("Plan EMPRESARIAL").

## Criterios de aceptación
- [ ] Crear empresa con datos malos dice qué corregir.
- [ ] Borrar la cantidad en el POS no quita el producto; no se cobra con una cantidad vacía.
- [ ] La fila con más cantidad que existencia sale en rojo con la existencia.
- [ ] El detalle de la venta se ve en la misma fila.
- [ ] El Inicio solo tiene categorías (con sus pendientes) y "Requiere tu atención", para cada rol.
- [ ] El periodo ya no está en el Inicio.

## Fuera de alcance
- Cambiar qué pantallas tiene cada categoría.
- La regla de la spec 030 al cobrar sin existencia.
