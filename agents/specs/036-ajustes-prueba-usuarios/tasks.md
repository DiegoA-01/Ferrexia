# 036 - Tareas

Solo frontend; no toca el backend ni la base de datos.

- [x] T1 · Crear empresa: la contraseña inicial usa la misma regla del backend (8 a 72, con letra y número); cada
      campo malo dice su problema debajo; "Crear empresa" ya no se apaga sin módulos y al oprimirlo lista lo que falta
      (`plataforma/empresas.ts`, `empresas.html`).
- [x] T2 · POS: la cantidad vacía o en 0 no quita la línea; no se cotiza ni se cobra mientras falte; "Cobrar" se apaga
      y la fila dice "Escribe la cantidad" (`pos/pos.ts`, `pos.html`).
- [x] T3 · POS: con más cantidad que existencia la fila se pinta de rojo y dice cuánto hay; no bloquea (sigue la
      spec 030 al cobrar) (`pos.html`, `pos.scss`).
- [x] T4 · Ventas: el detalle se despliega en la misma fila; "Detalle" pasa a "Ocultar" y lo cierra
      (`ventas/ventas.html`, `ventas.ts`).
- [x] T5 · Inicio: solo categorías y "Requiere tu atención". Salen las cifras, gráficas, gasto por categoría, reorden y
      mejores proveedores. Cada categoría muestra lo pendiente de sus pantallas (por aprobar, facturas en revisión,
      proveedores por habilitar, bajo el mínimo). Lo bajo el mínimo y la reorden pasan a "Requiere tu atención"
      (`inicio/inicio.ts`, `inicio.html`, `inicio.scss`).
- [x] T6 · El periodo (Este mes, Trimestre, Año) y el selector de sede salen del Inicio; quedan en Reportes.
- [x] T7 · Barra lateral: "Suscripción activa" en vez de "Plan EMPRESARIAL · activo" (sobrante de la spec 024).
- [x] T8 · Pruebas: `empresas.spec`, `pos.spec`, `ventas.spec`, `inicio.spec` (333 en verde).
- [ ] T9 · Revisión en Docker con cada rol y aprobación del usuario.

## Revisión contra specs futuras
Ninguno de los 6 puntos estaba en las specs 033, 034 o 035. La spec 030 solo cubre el cobro sin existencia; el aviso
en rojo del punto 3 es nuevo y no la cambia. No se quitó ni se marcó nada en otras specs.
