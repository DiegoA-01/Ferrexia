# 035 - Sedes como empresas separadas con un super administrador

**Estado:** Propuesta (requiere aclarar D12 antes de seguir) · **Rama:** `feature/sedes-super-admin` · **Depende de:**
020, 022 · **Responsable:** Persona 2 · **Puntos de la reunión 2:** 14 · **Migración:** sí (se reserva al empezar)

## Objetivo
En la reunión se pidió que las sedes funcionen como empresas por separado y que una persona administre todas. Hoy una
empresa tiene varias sedes con datos compartidos, y cada usuario pertenece a una sola empresa. Esta spec define cómo
agrupar empresas bajo un mismo dueño.

## Historias
- Como dueño de varios almacenes, quiero que cada uno tenga su propio inventario, caja y usuarios.
- Como dueño, quiero entrar una sola vez y pasar de un almacén a otro.
- Como dueño, quiero ver reportes de todos mis almacenes juntos.

## Comportamiento (propuesta, se confirma en D12)
- **Grupo de empresas:** la consola de plataforma puede agrupar varias empresas bajo un **grupo**. Cada empresa del
  grupo sigue aislada (sus datos, usuarios, cajas e inventario).
- **Super administrador del grupo:** un usuario que entra una vez y elige en qué empresa trabajar, sin volver a
  ingresar. En cada empresa actúa con la plantilla Dueño.
- **Reportes del grupo:** el super administrador ve un tablero con las ventas, compras e inventario de todas las
  empresas del grupo, sumadas y por empresa.
- La opción de sedes dentro de una misma empresa (spec 020) se mantiene para quien la necesite, o se quita si D12 lo
  decide.

## Preguntas para D12
1. ¿Cada sede debe ser una empresa aparte, o basta con separar inventario y caja por sede dentro de la misma empresa?
2. ¿Los productos y precios se comparten entre las empresas del grupo o cada una tiene los suyos?
3. ¿Se pueden trasladar productos entre empresas del grupo?

## Criterios de aceptación
- [ ] Se definen con el equipo las respuestas de D12.
- [ ] Un super administrador cambia de empresa sin volver a ingresar.
- [ ] Ningún usuario normal ve datos de otra empresa del grupo.
- [ ] El tablero del grupo suma bien por empresa.
- [ ] Pruebas de fuga entre empresas del mismo grupo y de grupos distintos.

## Fuera de alcance
- Facturación consolidada del grupo.
