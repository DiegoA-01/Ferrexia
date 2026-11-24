# Plan de trabajo — Reunión N.° 2 (Ferrexia)

> Specs creadas a partir de este plan: 016 a 035 (índice en `agents/specs/README.md`, todas en Propuesta).
>
> Borrador para revisar. Agrupa los 55 puntos de la reunión por tema, marca lo que ya está hecho, lo que
> necesita una decisión antes de programarse y propone cómo repartirlo entre **dos personas** sin pisarse.
> Basado en el estado de `develop` al 2026-11-23 (specs 000 a 015 hechas, próxima migración libre **V40**).

---

## 1. Ya resuelto (no requiere trabajo)

| # | Punto | Estado |
|---|---|---|
| 1 | Ventas en el plan básico | Definido que **sí** (se aplica junto con el tema de planes, ver D11) |
| 8 | Categorías del menú desplegables | Hecho (spec 013) |
| 9 | Desde Inicio entrar directo a cada categoría | Hecho (spec 013) |
| 15 | Menú lateral se puede desplegar u ocultar | Hecho (spec 013) |

---

## 2. Decisiones que hay que tomar antes de programar

Algunos puntos chocan con decisiones o reglas de seguridad que ya existen. Conviene cerrarlas en una reunión corta
antes de empezar, y anotarlas en `agents/specs/README.md` (ya están ahí como D10 a D20, en estado Recomendada o Pendiente).

| D | Puntos | Qué hay que decidir | Propuesta |
|---|---|---|---|
| D10 | 3 | El jefe "ve" las contraseñas del personal. Hoy las claves se guardan cifradas (BCrypt) y **no se pueden ver**, ni deberían. | Dueño y administradores: clave fuerte (mín. 12, mayúscula, número, símbolo) + MFA. Personal: **clave corta o PIN de 4–6 dígitos** que no puede cambiar; si la olvida, el jefe la **restablece** (no la ve). Se audita cada restablecimiento. |
| D11 | 1, 4, 7 | "Los planes ya no van a existir / deben ser simbólicos". | Dejar los planes solo como **nombre comercial**: todas las empresas con todos los módulos activos; la consola deja de bloquear por plan. Se conserva la tabla por si se cobra después (SaaS 4). Quitar la opción de crear/eliminar planes de la consola. |
| D12 | 14 | Sedes "solo cuando se crea una empresa por separado y un super admin de ambas". | Aclarar: ¿cada sede pasa a ser una **empresa aparte** con un usuario que administra varias empresas? Es un cambio grande de arquitectura (hoy un usuario pertenece a una sola empresa). Propuesta: un **grupo de empresas** con un super admin que cambia de empresa sin volver a ingresar. |
| D13 | 19 | Vender un producto sin stock. La decisión anterior (D1 de ventas) fue **no**. | Permitirlo **solo con permiso**, sin dejar el stock negativo en silencio: la venta queda marcada "sin existencia", se crea un pendiente de compra y sale en un **reporte nuevo de ventas sin stock**. |
| D14 | 36, 39 | "El proveedor no tenga perfil en el sistema", pero hoy existe el portal del proveedor (cotiza, ve órdenes y pagos). | Reemplazar el portal por un **enlace de un solo uso** por solicitud (cotizar sin cuenta), con formulario mínimo y todo opcional salvo el nombre, **límite de peticiones** y anti-bot (Cloudflare Turnstile o reCAPTCHA + campo trampa). Definir por dónde llega el pedido: correo, WhatsApp o enlace. |
| D15 | 54 | Retiros y gastos sin PIN, con permiso. Hoy piden PIN de supervisor (spec 003). | Si el usuario tiene el permiso "registrar retiro/gasto", no se pide PIN; si no lo tiene, se sigue pidiendo PIN. Todo queda en el libro de caja y en auditoría. |
| D16 | 43 | "Cambiar método de pago solo a facturas de prueba y no a electrónicas". | Permitir **cambiar el método de pago** de un tiquete/factura POS ya cobrado (con permiso y auditoría). Cuando exista factura electrónica DIAN, ese cambio quedará bloqueado para ellas. |
| D17 | 30 | Stock máximo: ¿se puede pasar? | Sí se puede pasar, pero con **alerta** (no bloquea la recepción ni la compra). |
| D18 | 5, 12, 34 | Qué roles quedan y cómo se dan permisos sueltos. | Pasar de roles fijos a **permisos por acción** agrupados en **plantillas de rol** por defecto (Dueño, Administrador, Cajero, Vendedor, Bodega, Compras, Contador). A cualquier trabajador se le pueden sumar o quitar permisos. Proponer cuáles de los 12 roles actuales se fusionan (p. ej. SOLICITANTE + ALMACENISTA → Bodega; APROBADOR dentro de Administrador). |
| D19 | 6 | Alcance del módulo de domicilios. | Mínimo: venta tipo domicilio con dirección y teléfono, costo de envío, domiciliario asignado, estados (pendiente → en camino → entregado / devuelto), pago contraentrega que entra a la caja del turno. |
| D20 | 53 | Ver lo esperado antes de cerrar, cuando hoy el cierre es ciego (spec 003). | El cajero cuenta primero y **después** ve lo esperado y la diferencia; quien tenga el permiso "ver esperado" lo ve siempre. |

---

## 3. Los puntos agrupados por tema

### Tema A — Experiencia general de la interfaz (transversal)
| # | Punto | Tamaño |
|---|---|---|
| 16 | Las pestañas de la categoría se quedan fijas arriba (en forma mínima) al bajar | S |
| 25 | Punto de miles en todas las cifras (mostrar y escribir) | M |
| 26 | Quitar las flechas de los campos numéricos en toda la app | S |
| 38 | Ninguna pantalla con scroll horizontal | M |
| 41 | En el menú, al abrir una categoría se cierra la otra (nunca dos abiertas) | S |
| 10 | Reportes como categoría propia (sacarlos de Ventas) | S |
| 11 | Informe de compras dentro de Reportes | S |
| 28 | Carga masiva dentro de Configuración › Importación de datos | S |

### Tema B — Usuarios, roles, permisos y seguridad
| # | Punto | Tamaño |
|---|---|---|
| 3 | Reglas de contraseña distintas para dueño/admin y para el personal (ver D10) | M |
| 5 | Definir qué roles quedan (ver D18) | Decisión |
| 12 | Roles por defecto con permisos predeterminados | L |
| 34 | Dar cualquier permiso a cualquier trabajador | L |
| 51 | Permiso para usar la caja 1 o la caja 2 | S (después de 34) |
| 54 | Retiro o gasto con permiso en vez de PIN (ver D15) | S (después de 34) |
| 18 | Que el dueño pueda vender | S (después de 34) |

### Tema C — Plataforma: planes, empresas y sedes
| # | Punto | Tamaño |
|---|---|---|
| 1, 4, 7 | Planes simbólicos; quitar crear/eliminar planes (ver D11) | M |
| 2 | Quitar el código de ingreso al crear la empresa (se genera solo desde el nombre) | S |
| 13 | Sede con enlace de Google Maps en vez de latitud y longitud | S (migración) |
| 14 | Sedes como empresas separadas con un super admin de ambas (ver D12) | L |

### Tema D — Catálogo y productos
| # | Punto | Tamaño |
|---|---|---|
| 27 | Crear una categoría nueva sin salir del formulario del producto | S |
| 35 | Crear un proveedor desde el formulario del producto | S |
| 29 | Unidad de medida ajustable | M |
| 30 | Stock máximo y si se puede pasar (ver D17) | S |
| 31 | Impuesto del producto al crearlo | S |
| 32 | Variantes del producto (talla, color, medida…) | L (migración) |
| 33 | Proveedor y fecha de referencia del costo al crear el producto | S |

### Tema E — Proveedores y compras
| # | Punto | Tamaño |
|---|---|---|
| 39 | El proveedor no tiene perfil en el sistema (ver D14) | L |
| 36 | Pedido al proveedor: solo nombre obligatorio, límite de peticiones, anti-bot, canal del pedido | M |
| 37 | Solicitud de compra a un proveedor específico o abierta a todos los de referencia | M |
| 40 | Historial y detalle de cada solicitud; editar o quitar productos | M |
| 42 | Si sale la alerta en la solicitud (duplicada o presupuesto), no dejar crearla | S |

### Tema F — Ventas y POS
| # | Punto | Tamaño |
|---|---|---|
| 17 | Pedidos de venta con categorías para que el vendedor encuentre rápido | M |
| 20 | Código del voucher opcional | S |
| 22 | Método de pago obligatorio | S |
| 23 | En la venta, cliente y método de pago en un mismo paso | M |
| 24 | Quitar los 4 últimos dígitos de la tarjeta | S |
| 21 | Anular la factura (también después del turno, con permiso) | M |
| 43 | Cambiar el método de pago de una factura POS (ver D16) | M |
| 48 | Ver los pedidos eliminados en ventas | S |
| 19 | Vender sin stock con permiso + reporte (ver D13) | M |

### Tema G — Caja y turnos (rediseño de "Mi turno")
| # | Punto | Tamaño |
|---|---|---|
| 52 | Revisar a fondo el flujo de cajas y turnos (se hace primero, como spec) | Análisis |
| 49 | Con turno abierto se ve el turno, no "crear turno"; botón claro de cerrar turno/caja | S |
| 55 | Acciones de "Mi turno" en un menú desplegable, no todas a la vista | S |
| 44 | Desde "Mi turno" ver las facturas del turno y registrar ingresos y egresos | M |
| 45 | Configurar categorías de ingresos y egresos | M (migración) |
| 46 | Facturas del día con paginación: reimprimir, editar, detalle y estado | M |
| 53 | Antes de cerrar, mostrar lo que debería haber en caja | S (revisar con cierre ciego) |
| 50 | Cierre contando billetes y monedas por denominación; vista de ventas por turno | M (migración) |

> Ojo con el punto 53: hoy el cierre es **ciego** a propósito (el cajero no ve el esperado para que no "cuadre" a
> la fuerza). Propuesta: el cajero cuenta primero y **después** ve el esperado y la diferencia, o solo lo ve quien
> tenga permiso.

### Tema H — Reportes
| # | Punto | Tamaño |
|---|---|---|
| 47 | Ventas por categoría, empleado, insumo, medio de pago, tipo de servicio (domicilio o mostrador) y caja | L |
| 19 | Reporte de ventas sin stock | S (después de F-19) |

### Tema I — Domicilios (módulo nuevo)
| # | Punto | Tamaño |
|---|---|---|
| 6 | Módulo de domicilios (ver D19) | L (migración) |

---

## 4. Reparto entre dos personas

El reparto sigue **zonas del código** para que cada uno trabaje en archivos distintos y casi no haya conflictos.

| | **Persona 1 — Ventas, caja y reportes** | **Persona 2 — Plataforma, usuarios, catálogo y compras** |
|---|---|---|
| Temas | F, G, H, I | A, B, C, D, E |
| Zona del backend | `venta/`, `caja/`, `cartera/`, `reporte/`, nuevo `domicilio/` | `security/`, `usuario/`, `plataforma/`, `empresa/`, `inventario/`, `catalogo/`, `proveedor/`, `compra/` |
| Zona del frontend | `features/pos`, `features/caja`, `features/ventas`, `features/reportes` | `layout/`, `core/navegacion`, estilos globales, `features/configuracion`, `features/inventario`, `features/compras`, `features/plataforma` |
| Dueño de archivos compartidos | — | `menu.ts`, `shell`, estilos globales, `Roles.java`/permisos |

**Archivos que tocan los dos** (avisar antes y hacer cambios pequeños): `frontend/src/app/core/api/esquema.ts`,
`agents/specs/README.md` (reserva de migraciones), `AislamientoEmpresasTest`.

---

## 5. Orden propuesto (por fases)

### Fase 0 — Decisiones (1 reunión, los dos)
- Cerrar D10 a D20 y anotarlas en `agents/specs/README.md`.
- Reservar números de spec (016 en adelante) y de migración (V40 en adelante).

### Fase 1 — Arreglos rápidos (≈ 1 semana)
| Persona 1 | Persona 2 |
|---|---|
| **Spec 016 POS rápido:** 20, 22, 24, 48 | **Spec 017 Interfaz general:** 16, 25, 26, 38, 41 |
| **Spec 018 Mi turno, parte 1:** 49, 55 (y escribir la revisión del punto 52) | **Spec 019 Reorganizar el menú:** 10, 11, 28 · **Spec 020 Empresa:** 2, 13 |

> La persona 2 hace primero la spec 017, porque toca estilos y componentes que usan todas las pantallas.

### Fase 2 — Base de permisos y caja (≈ 2 semanas)
| Persona 1 | Persona 2 |
|---|---|
| **Spec 021 Mi turno, parte 2:** 44, 45, 46, 53 | **Spec 022 Permisos y plantillas de rol:** 3, 5, 12, 34 (es la pieza más grande y desbloquea a la persona 1) |
| **Spec 023 Cierre por denominación:** 50 | **Spec 024 Planes simbólicos:** 1, 4, 7 |

### Fase 3 — Ventas con permisos y catálogo (≈ 2 semanas)
| Persona 1 | Persona 2 |
|---|---|
| **Spec 025 Caja con permisos:** 18, 51, 54 (usa la 022) | **Spec 026 Producto completo:** 27, 29, 30, 31, 33, 35 |
| **Spec 027 Venta ágil:** 17, 23 | **Spec 028 Variantes de producto:** 32 |
| **Spec 029 Facturas:** 21, 43 | |

### Fase 4 — Módulos nuevos (≈ 2–3 semanas)
| Persona 1 | Persona 2 |
|---|---|
| **Spec 030 Venta sin stock:** 19 (+ su reporte) | **Spec 031 Proveedor sin perfil:** 36, 39 |
| **Spec 032 Domicilios:** 6 | **Spec 033 Solicitudes de compra:** 37, 40, 42 |
| **Spec 034 Reportes de ventas:** 47 (al final, porque usa domicilios y caja) | **Spec 035 Sedes y super admin:** 14 (según D12) |

---

## 6. Cómo trabajamos (igual que hasta ahora)

1. Una spec = una rama desde `develop` actualizado = una persona responsable.
2. Primero `spec.md` → se aprueba → `plan.md` → `tasks.md`, y cada tarea con sus pruebas.
3. La migración se reserva en `agents/specs/README.md` en el primer commit de la rama.
4. Antes del PR: `git pull` de `develop`, correr pruebas del backend y del frontend, y revisar que el CI quede en verde.
5. PR hacia `develop`; el dueño del repositorio lo revisa y lo mezcla con "Create a merge commit".
6. Toda entidad nueva lleva `@TenantId` y su prueba en `AislamientoEmpresasTest`.

---

## 7. Lista completa (para no perder ningún punto)

| # | Tema | Persona | Spec | # | Tema | Persona | Spec |
|---|---|---|---|---|---|---|---|
| 1 | C | 2 | 024 | 29 | D | 2 | 026 |
| 2 | C | 2 | 020 | 30 | D | 2 | 026 |
| 3 | B | 2 | 022 | 31 | D | 2 | 026 |
| 4 | C | 2 | 024 | 32 | D | 2 | 028 |
| 5 | B | 2 | 022 | 33 | D | 2 | 026 |
| 6 | I | 1 | 032 | 34 | B | 2 | 022 |
| 7 | C | 2 | 024 | 35 | D | 2 | 026 |
| 8 | — | — | Hecho | 36 | E | 2 | 031 |
| 9 | — | — | Hecho | 37 | E | 2 | 033 |
| 10 | A | 2 | 019 | 38 | A | 2 | 017 |
| 11 | A | 2 | 019 | 39 | E | 2 | 031 |
| 12 | B | 2 | 022 | 40 | E | 2 | 033 |
| 13 | C | 2 | 020 | 41 | A | 2 | 017 |
| 14 | C | 2 | 035 | 42 | E | 2 | 033 |
| 15 | — | — | Hecho | 43 | F | 1 | 029 |
| 16 | A | 2 | 017 | 44 | G | 1 | 021 |
| 17 | F | 1 | 027 | 45 | G | 1 | 021 |
| 18 | B | 1 | 025 | 46 | G | 1 | 021 |
| 19 | F/H | 1 | 030 | 47 | H | 1 | 034 |
| 20 | F | 1 | 016 | 48 | F | 1 | 016 |
| 21 | F | 1 | 029 | 49 | G | 1 | 018 |
| 22 | F | 1 | 016 | 50 | G | 1 | 023 |
| 23 | F | 1 | 027 | 51 | B | 1 | 025 |
| 24 | F | 1 | 016 | 52 | G | 1 | 018 |
| 25 | A | 2 | 017 | 53 | G | 1 | 021 |
| 26 | A | 2 | 017 | 54 | B | 1 | 025 |
| 27 | D | 2 | 026 | 55 | G | 1 | 018 |
| 28 | A | 2 | 019 | | | | |

**Carga aproximada:** Persona 1 → 22 puntos (menos, pero más pesados: dinero, caja y el módulo de domicilios).
Persona 2 → 30 puntos (muchos son pequeños de interfaz; incluye la base de permisos, que es la pieza más grande). Los puntos 8, 9 y 15 ya están hechos y el 1 queda definido.
