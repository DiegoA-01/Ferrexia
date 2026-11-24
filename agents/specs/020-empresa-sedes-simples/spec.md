# 020 - Alta de empresa sin código y sedes con enlace de Google Maps

**Estado:** Propuesta · **Rama:** `feature/empresa-sedes-simples` · **Depende de:** SaaS 2 · **Responsable:** Persona 2 ·
**Puntos de la reunión 2:** 2, 13 · **Migración:** sí (se reserva al empezar)

## Objetivo
Hacer más simple crear una empresa y sus sedes: que el dueño de la plataforma no tenga que inventar el código de
ingreso, y que la ubicación de una sede se ponga pegando un enlace de Google Maps en vez de latitud y longitud.

## Historias
- Como dueño de la plataforma, quiero crear una empresa sin escribir su código de ingreso.
- Como administrador, quiero poner la ubicación de la sede con el enlace de Google Maps que ya tengo.

## Comportamiento
- **Código de ingreso automático:** al crear la empresa, el código se genera solo a partir del nombre (sin tildes ni
  espacios, en minúscula; si ya existe, se le agrega un número). Se muestra al terminar el alta y se puede consultar en
  el detalle de la empresa.
- **Sede con enlace:** el formulario de sede pide la dirección y, opcional, el **enlace de Google Maps**. Ya no pide
  latitud ni longitud. El enlace se valida (debe ser de Google Maps) y en la lista de sedes sale un botón "Ver en el
  mapa".
- Las sedes que ya tenían latitud y longitud conservan esos datos guardados, aunque no se muestren.

## Criterios de aceptación
- [ ] El alta de empresa no pide código y genera uno único.
- [ ] El código generado se muestra al terminar y en el detalle de la empresa.
- [ ] La sede se guarda con el enlace de Google Maps y sin latitud ni longitud.
- [ ] Un enlace que no es de Google Maps se rechaza con un mensaje claro.
- [ ] Prueba de fuga entre empresas para las sedes.

## Fuera de alcance
- Editar el código de ingreso de una empresa existente.
- Sedes como empresas separadas (spec 035).
