# 020 - Alta de empresa sin código y sedes con enlace de Google Maps

**Estado:** Hecha (V41; aprobada en la revisión del 2026-11-25; falta PR) · **Rama:** `feature/empresa-sedes-simples` · **Depende de:** SaaS 2 · **Responsable:** Persona 2 ·
**Puntos de la reunión 2:** 2, 13 · **Migración:** V41 (`sede.enlace_mapa`)

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
- [x] El alta de empresa no pide código y genera uno único.
- [x] El código generado se muestra al terminar y en el detalle de la empresa.
- [x] La sede se guarda con el enlace de Google Maps y sin latitud ni longitud.
- [x] Un enlace que no es de Google Maps se rechaza con un mensaje claro.
- [x] Prueba de fuga entre empresas para las sedes.

> Verificado el 2026-11-25 con pruebas (falta la revisión en Docker y el CI):
> - Backend: `GeneradorCodigoEmpresaTest` (tildes, siglas S.A.S./Ltda./E.U./y Cía., corte a 30 caracteres sin conector
>   suelto, respaldo `empresa`, sufijo `-N`), `EmpresaPlataformaServiceTest` (sin código o en blanco se genera; si existe
>   pasa a `-2`, `-3`…; si dos altas chocan se reintenta con el siguiente; el escrito se respeta),
>   `EmpresaCrearRequestTest` (el código es opcional), `EnlaceGoogleMapsValidatorTest` (acepta los enlaces de compartir;
>   rechaza `http`, otros dominios, `javascript:`, usuario y puerto), `EmpresaModuloControllerTest` (400 con mensaje) y
>   `AislamientoEmpresasTest` con MySQL real (la sede con enlace de A no la ve, ni la lee, ni la edita B).
> - Frontend: `empresas.spec.ts` (el alta no pide código, no lo envía y muestra el generado con "Copiar"; "Escribir el
>   código a mano" sigue disponible) y `catalogo.spec.ts` (la sede pide el enlace en vez de latitud y longitud, "Ver en el
>   mapa" abre en otra pestaña con `noopener`, un enlace que no es `https` no se vuelve enlace). 285 pruebas en verde y
>   `tsc` sin errores.

## Fuera de alcance
- Editar el código de ingreso de una empresa existente.
- Sedes como empresas separadas (spec 035).
