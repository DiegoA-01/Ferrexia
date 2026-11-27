# 035 - Sedes como empresas separadas con un super administrador

**Estado:** Hecha (2026-11-27; falta revisión en Docker) · **Rama:** `feature/sedes-super-admin` · **Depende de:** 020, 022, 007 ·
**Responsable:** Persona 2 · **Puntos de la reunión 2:** 14 · **Migración:** V57 · **Decisión:** D12 (tomada)

## Objetivo
En la reunión se pidió que las sedes funcionen como empresas por separado y que una persona administre todas. Esta spec
agrupa empresas bajo un mismo dueño: cada una sigue aislada, un super administrador entra a cualquiera sin volver a
ingresar, ve un tablero del grupo y las empresas del grupo pueden trasladarse mercancía.

## Decisiones (2026-11-27, D12)
- **Cada sede es una empresa aparte** (sus usuarios, inventario, caja, compras y ventas), agrupadas en un grupo.
- **Productos y precios propios de cada empresa** (no se comparten).
- **Sí hay traslados entre empresas del grupo:** el producto se empareja por el **mismo código** (o código de barras); si
  no existe en la destino, el traslado no se deja enviar y dice cuáles faltan. Sale de la origen al enviar (queda en
  tránsito) y **entra en la destino cuando alguien de allá lo confirma**, pudiendo anotar faltantes. El costo de entrada es
  el costo promedio de la origen.
- Todo en una sola entrega (un PR), con revisión de seguridad antes de mezclar.

## Historias
- Como dueño de varios almacenes, quiero que cada uno tenga su propio inventario, caja y usuarios.
- Como dueño, quiero entrar a cualquiera de mis almacenes sin volver a iniciar sesión y ver el total del grupo.
- Como bodeguero, quiero enviar mercancía a otro almacén del grupo y que allá la reciban.

## Comportamiento
- **Grupo (consola de plataforma):** el dueño del software crea un grupo, le asigna empresas y crea su super
  administrador (correo, clave fuerte y segundo factor obligatorio). Cada empresa sigue aislada.
- **Super administrador:** entra por su propia pantalla de ingreso (`/grupo/ingreso`), ve las empresas de su grupo y
  entra a cualquiera sin volver a escribir la clave; dentro actúa como Dueño de esa empresa. Desde la barra puede volver a
  "Cambiar de empresa". Cada entrada queda auditada en la empresa.
- **Tablero del grupo:** ventas netas, compras e inventario valorizado del periodo, sumados y por empresa.
- **Traslado entre empresas:** quien tiene el permiso en la origen elige la empresa destino del grupo, la bodega de salida y
  los productos con cantidad; al enviar, el stock baja en la origen (kardex) y el traslado queda "En tránsito". En la
  destino, quien tiene el permiso lo recibe en una bodega, anota lo que llegó y el stock sube con kardex al costo promedio
  de la origen. Se puede anular mientras está en tránsito (el stock vuelve a la origen). Ninguna otra empresa lo ve.

## Criterios de aceptación
- [x] D12 respondida y documentada.
- [x] El super administrador cambia de empresa sin volver a ingresar ("Cambiar de empresa" en el menú).
- [x] Un usuario normal no puede ver otra empresa del grupo; el usuario Dueño vinculado solo lo administra la
  plataforma (409 `USUARIO_DE_GRUPO`) y no entra por el login normal.
- [x] El tablero del grupo suma correctamente (empresas con vínculo activo y suscripción vigente).
- [x] Un traslado baja el stock en la origen al enviar y lo sube en la destino al recibir, con kardex en las dos.
- [x] Prueba de fuga entre empresas del mismo grupo y de grupos distintos (también para los traslados):
  tres pruebas nuevas en `AislamientoEmpresasTest`.

## Fuera de alcance
- Facturación consolidada del grupo.
- Catálogo o precios compartidos entre empresas del grupo.
- Usuarios normales que trabajen en varias empresas con un solo login (solo el super administrador lo hace).
