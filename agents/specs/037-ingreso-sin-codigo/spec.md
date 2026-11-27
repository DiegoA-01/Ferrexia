# 037 - Ingreso sin código de empresa

**Estado:** Hecha (2026-11-27; falta revisión en Docker) · **Rama:** `feature/ingreso-sin-codigo` · **Depende de:** 020, 035 ·
**Responsable:** Persona 1 · **Migración:** V58

## Objetivo
Que las personas de una empresa entren solo con su correo y su contraseña (o PIN), sin escribir un código de empresa.
El dueño del software crea la empresa y le asigna al administrador su correo y su clave; el administrador crea su equipo.

## Decisiones (2026-11-27)
- **Correo único en todo el sistema** para los usuarios de empresa: un correo sirve para una sola empresa. Excepción:
  el usuario interno del super administrador de un grupo (spec 035), que repite su correo en cada empresa y nunca entra
  por el login normal.
- El código público de la empresa (`empresa.codigo_publico`) se conserva por dentro (lo usa el enlace de cotización y es
  histórico), pero ya no se pide ni se muestra en ninguna pantalla de ingreso, recuperación ni consola.

## Comportamiento
- **Ingreso** (`/ingreso`): correo y contraseña o PIN. El sistema encuentra la empresa por el correo. Mismos mensajes
  de error que hoy (no revela si el correo existe), mismo bloqueo por intentos, mismo segundo factor.
- **Olvidé mi contraseña:** solo el correo.
- **Crear o editar un usuario** con un correo que ya usa otra empresa: error "ese correo ya está registrado" (sin decir en
  qué empresa).
- **Consola de plataforma:** al crear la empresa se asignan el correo y la clave del administrador; ya no se muestra
  ningún código para entrar.

## Criterios de aceptación
- [x] Se entra solo con correo y contraseña (o PIN), y con segundo factor cuando aplica.
- [x] "Olvidé mi contraseña" pide solo el correo (y restablecer, solo el código y la clave nueva).
- [x] No se puede repetir un correo entre empresas (salvo el usuario interno del super administrador): lo frena el
  servicio y, si se lo salta, el índice único `correo_login` de la V58.
- [x] Ninguna pantalla pide ni muestra el código de empresa.
- [x] Prueba de fuga: el login con el correo de un usuario de A nunca entra a B (`AislamientoEmpresasTest`).

## Fuera de alcance
- Que una misma persona trabaje en varias empresas con un solo correo (eso solo lo hace el super administrador, 035).
