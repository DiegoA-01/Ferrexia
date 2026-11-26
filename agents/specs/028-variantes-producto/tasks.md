# Tasks 028

- [x] T1: Aprobar el diseño "cada variante es un producto" (anotado en la spec, 2026-11-26)
- [x] T2: Reservar la migración y crear `V50__variantes_producto.sql`
- [x] T3: Crear producto con variantes (combinaciones en Java, códigos, límite de 100, precio en la General); pruebas
  (`VarianteServiceTest`)
- [x] T4: Editar valores (agregar crea, quitar desactiva, volver reactiva); editar agrupador propaga; pruebas
  (`VarianteServiceTest`, `ProductoVariantesTest`)
- [x] T5: Bloquear agrupadores en inventario, ventas, compras, contratos, despachos y precios; listado sin agrupadores;
  reporte de inventario y rotación; pruebas
- [x] T6: Pruebas de fuga (`AislamientoEmpresasTest.lasVariantesYSusAtributosSonDeSuEmpresaYElProductoGeneralNoSeMueve`)
- [x] T7: Formulario con "Tiene variantes" y tabla de combinaciones; editar agrupador y variante; pruebas
  (`producto-variantes.spec.ts`)
- [x] T8: Inventario con agrupadores desplegables; selectores sin agrupadores; pruebas
- [ ] T9: `./mvnw verify` (919 en verde, 1 omitida), `npm test` (322 en verde), tipos regenerados (`npm run api:tipos`)
  y `tsc` sin errores. **Falta:** e2e del CI (corre en el PR) y revisión en Docker (crear un producto con variantes,
  comprarlo, recibirlo y venderlo por código de barras).
- [x] T10: Verificar los criterios de aceptación y actualizar el índice de specs
