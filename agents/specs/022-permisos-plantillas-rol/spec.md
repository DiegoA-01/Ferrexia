# 022 - Permisos por acción, plantillas de rol y contraseñas del personal

**Estado:** Aprobada (Partes A y B hechas) · **Rama:** `feature/permisos-plantillas-rol` · **Depende de:** SaaS 3 · **Responsable:** Persona 2
· **Puntos de la reunión 2:** 3, 5, 12, 34 · **Migración:** V43 (Parte A) y V44 (Parte B) · **Decisiones:** D10, D18 (tomadas, tal como están)

## Objetivo
Hoy cada pantalla y acción se permite según 12 roles fijos. El negocio necesita dar a cualquier trabajador justo los
permisos que necesita, empezar rápido con roles ya armados y que el personal entre con una clave sencilla. Es la pieza
más grande de esta tanda y la base de las specs 025 y 029.

## Historias
- Como dueño, quiero crear un trabajador eligiendo un rol ya armado (Cajero, Vendedor, Bodega…) y listo.
- Como dueño, quiero sumarle o quitarle permisos sueltos a un trabajador, sin crear un rol nuevo.
- Como dueño, quiero crear mis propios roles a partir de los que vienen.
- Como trabajador, quiero entrar con una clave corta que me asigna el jefe.
- Como dueño, si un trabajador olvida su clave, quiero restablecerla yo.

## Comportamiento
- **Permisos por acción:** cada acción sensible tiene su permiso (por ejemplo "vender", "anular venta", "registrar
  gasto", "ver esperado de caja", "aprobar solicitud", "crear producto"). El backend revisa el permiso en cada acción,
  no el rol. Se mantiene el control por módulo contratado y por empresa.
- **Plantillas de rol (D18):** vienen por defecto Dueño, Administrador, Cajero, Vendedor, Supervisor de ventas, Bodega,
  Compras y Contador/Auditor, cada una con sus permisos. Los roles actuales se pasan a la plantilla equivalente sin
  que nadie gane ni pierda acceso el día del cambio.
- **Permisos sueltos:** en la ficha del trabajador se ven sus permisos agrupados por módulo, los que le da su rol y los
  sumados o quitados a mano.
- **Contraseñas (D10):** dueño y administradores usan clave fuerte (mínimo 12 caracteres con mayúscula, minúscula,
  número y símbolo) y MFA. El personal usa una **clave corta o PIN de 6 dígitos** que asigna el jefe y que el
  trabajador no puede cambiar. Si la olvida, quien tenga el permiso "restablecer claves" le pone una nueva. **Las
  claves nunca se pueden ver**: se guardan cifradas. Cada restablecimiento queda en la auditoría.
- El bloqueo por intentos fallidos y el límite de peticiones siguen igual para todos.

## Criterios de aceptación
- [x] El acceso a cada acción depende del permiso y no del rol (endpoints, reglas de los servicios, menú y rutas;
  quedan por rol los avisos por notificación y algunos botones internos de pantallas, ver `plan.md`).
- [x] Las plantillas por defecto existen en cada empresa, nueva o existente (son del sistema y las ven todas).
- [x] Al migrar, cada usuario conserva exactamente lo que podía hacer (prueba de equivalencia de los 310 endpoints).
- [x] Se pueden sumar y quitar permisos a un trabajador y crear roles propios.
- [x] El personal entra con su clave corta; dueño y administradores con clave fuerte y MFA (las claves que ya
  existían siguen sirviendo; la regla aplica al crear, restablecer o cambiar).
- [x] Ninguna pantalla ni API devuelve una contraseña; el restablecimiento queda auditado.
- [x] Prueba de fuga entre empresas para roles y permisos.

## Fuera de alcance
- Permisos por caja (spec 025).
- Usuarios que administran varias empresas (spec 035).
