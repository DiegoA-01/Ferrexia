import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import {
  Categoria, InventarioItem, InventarioResumen, Producto, Proveedor, SugerenciaReorden, TarifaImpuesto, UnidadMedida,
} from '../../core/api/modelos';
import { NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { InventarioService } from '../../core/servicios/inventario.service';
import { VentasService } from '../../core/servicios/ventas.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos, descargar } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

import { Cifra } from '../../core/ui/cifra';
import { ProductoVariantes } from './producto-variantes';
/** Fila de la tabla: viene del reporte de inventario o, si el rol no lo ve, del listado de productos. */
/** Spec 038: qué puede medir el material de una categoría. */
const MEDIDAS: { valor: NonNullable<Categoria['magnitud']>; texto: string }[] = [
  { valor: 'CANTIDAD', texto: 'Unidades (piezas, docenas, cajas)' }, { valor: 'LONGITUD', texto: 'Longitud (metros, centímetros)' },
  { valor: 'AREA', texto: 'Área (metros cuadrados)' }, { valor: 'VOLUMEN', texto: 'Volumen (litros, galones, metros cúbicos)' },
  { valor: 'MASA', texto: 'Peso (kilos, libras, toneladas)' },
];

type Fila = Pick<InventarioItem, 'productoId' | 'codigo' | 'nombre' | 'categoria' | 'unidad' | 'stock' | 'stockMinimo' | 'stockMaximo' | 'valor' | 'bajoMinimo' | 'esAgrupador' | 'agrupadorId'>;

/**
 * Vista 7 · Productos y stock. El stock no se edita aquí: solo cambia con recepciones, ajustes con motivo y
 * traslados. La reorden la calcula el backend y solo deja un borrador.
 */
@Component({
  selector: 'sc-inventario',
  imports: [Cifra, FormsModule, ReactiveFormsModule, RouterLink, PesosPipe, NumeroPipe, ProductoVariantes],
  templateUrl: './inventario.html',
  styleUrl: './inventario.scss',
})
export class Inventario implements OnInit {
  private readonly servicio = inject(InventarioService);
  private readonly ventas = inject(VentasService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly resumen = signal<InventarioResumen | null>(null);
  protected readonly filas = signal<Fila[] | null>(null);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly unidades = signal<UnidadMedida[]>([]);
  protected readonly sugerencias = signal<SugerenciaReorden[]>([]);
  protected readonly categoriaId = signal<number | null>(null);
  protected readonly soloBajo = signal(false);
  protected readonly panel = signal<'ninguno' | 'nuevo' | 'reorden' | 'inactivos'>('ninguno');
  protected readonly trabajando = signal(false);
  /** Producto que se está editando ({@code null} = el formulario crea uno nuevo). */
  protected readonly editandoId = signal<number | null>(null);
  protected readonly inactivos = signal<Producto[] | null>(null);

  protected readonly conReporte = computed(() => this.sesion.tieneRol(['AUDITOR', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO', 'ALMACENISTA']) && this.sesion.tieneModulo('REPORTES'));
  protected readonly esAdminCompras = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());
  protected readonly esSolicitante = computed(() => this.sesion.tieneRol(['SOLICITANTE']) && !this.sesion.soloLectura());
  protected readonly porReponer = computed(() => this.sugerencias().filter(s => !s.yaCubierto && (s.cantidadSugerida ?? 0) > 0));
  protected readonly totalBajo = computed(() => this.resumen()?.bajoMinimo ?? this.filas()?.filter(f => f.bajoMinimo && !f.esAgrupador).length ?? 0);
  /** Productos con stock propio (sin contar los generales que agrupan variantes). */
  protected readonly totalProductos = computed(() => this.resumen()?.productos ?? this.filas()?.filter(f => !f.esAgrupador).length);
  protected readonly sinExistencias = computed(() => (this.filas() ?? []).filter(f => !f.esAgrupador && (f.stock ?? 0) <= 0).length);
  protected readonly ejemplo = computed(() => this.porReponer()[0] ?? null);

  protected readonly nuevo = inject(FormBuilder).nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(40)]],
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    categoriaId: [0, [Validators.required, Validators.min(1)]],
    unidadMedidaId: [0, [Validators.required, Validators.min(1)]],
    stockMinimo: [0, [Validators.required, Validators.min(0)]],
    stockMaximo: [null as number | null],
    costoReferencia: [null as number | null],
    // spec 026: impuesto, proveedor y fecha del costo de referencia
    tarifaImpuestoId: [null as number | null],
    proveedorReferenciaId: [null as number | null],
    costoReferenciaFecha: [null as string | null],
  });

  /** Spec 026: impuestos de venta y proveedores para el formulario, y el alta corta "Nueva" encima del formulario. */
  protected readonly impuestos = signal<TarifaImpuesto[]>([]);
  protected readonly proveedores = signal<Proveedor[]>([]);
  protected readonly alVuelo = signal<'categoria' | 'proveedor' | null>(null);
  protected readonly nombreNuevo = signal('');
  protected readonly nitNuevo = signal('');
  /** Spec 026 (D17): filtro de productos por encima del máximo. */
  protected readonly soloSobreMaximo = signal(false);
  protected readonly totalSobreMaximo = computed(() => (this.filas() ?? []).filter(f => this.sobreMaximo(f)).length);
  /**
   * Spec 028: cada producto general sale con sus variantes debajo, que se despliegan con un clic. Con "sobre el máximo"
   * el general queda si alguna de sus variantes lo pasa.
   */
  protected readonly filasVisibles = computed(() => {
    const todas = this.filas() ?? [];
    const pasa = (f: Fila) => !this.soloSobreMaximo() || this.sobreMaximo(f);
    const generales = new Set(todas.filter(f => f.esAgrupador).map(f => f.productoId));
    const visibles: Fila[] = [];
    for (const f of todas) {
      if (f.agrupadorId && generales.has(f.agrupadorId)) {
        continue;
      }
      if (f.esAgrupador) {
        const hijas = todas.filter(v => v.agrupadorId === f.productoId && pasa(v));
        if (hijas.length || !this.soloSobreMaximo()) {
          visibles.push(f);
          if (this.abiertos().has(f.productoId!)) {
            visibles.push(...hijas);
          }
        }
      } else if (pasa(f)) {
        visibles.push(f);
      }
    }
    return visibles;
  });
  /** Productos generales desplegados (spec 028). */
  protected readonly abiertos = signal<Set<number>>(new Set());
  /** Cuántas variantes tiene cada producto general en la tabla. */
  protected readonly variantesPorGeneral = computed(() => {
    const cuenta = new Map<number, number>();
    for (const f of this.filas() ?? []) {
      if (f.agrupadorId) {
        cuenta.set(f.agrupadorId, (cuenta.get(f.agrupadorId) ?? 0) + 1);
      }
    }
    return cuenta;
  });
  /** Spec 028: el producto nuevo tiene variantes (se arma con atributos y combinaciones). */
  protected readonly conVariantes = signal(false);
  /** Producto que se está editando, para saber si es un producto general o una variante. */
  protected readonly editando = signal<Producto | null>(null);
  protected readonly variantes = viewChild(ProductoVariantes);
  /** El formulario no pide stock ni costo para un producto general (nuevo con variantes o ya existente). */
  protected readonly sinStockPropio = computed(() => (this.conVariantes() && !this.editandoId()) || !!this.editando()?.esAgrupador);
  protected readonly hoy = iso(new Date());
  /** Código escrito, para proponer el de cada variante. */
  protected readonly codigoActual = toSignal(this.nuevo.controls.codigo.valueChanges, { initialValue: '' });
  /** Spec 038: categoría y unidad elegidas en el formulario. */
  private readonly categoriaElegida = toSignal(this.nuevo.controls.categoriaId.valueChanges, { initialValue: 0 });
  private readonly unidadElegida = toSignal(this.nuevo.controls.unidadMedidaId.valueChanges, { initialValue: 0 });
  /** Spec 038: ver todas las unidades aunque la categoría diga qué mide. */
  protected readonly todasLasUnidades = signal(false);
  /** Spec 038: lo que se elige como medida de la categoría (alta "Nueva" y categoría existente). */
  protected readonly magnitudNueva = signal<Categoria['magnitud'] | null>(null);
  protected readonly MEDIDAS = MEDIDAS;
  /** Unidad que tenía el producto al abrirlo para editar (para avisar de la conversión). */
  protected readonly unidadOriginal = signal<number | null>(null);
  /** Lo que mide la categoría elegida ({@code null} = no lo dice). */
  protected readonly medidaCategoria = computed(() =>
    this.categorias().find(c => c.id === this.categoriaElegida())?.magnitud ?? null);
  /** Unidades que se ofrecen: las de la medida de la categoría, salvo que se pidan todas (siempre con la elegida). */
  protected readonly unidadesOfrecidas = computed(() => {
    const medida = this.medidaCategoria();
    const elegida = this.unidadElegida();
    return this.unidades().filter(u => this.todasLasUnidades() || !medida || u.magnitud === medida || u.id === elegida);
  });
  /** Al editar se cambió la unidad: con movimientos solo pasa a una que mida lo mismo y se convierte todo. */
  protected readonly cambiaUnidad = computed(() => !!this.editandoId() && !!this.unidadOriginal()
    && this.unidadElegida() !== this.unidadOriginal());
  /** Spec 038: la unidad nueva mide otra cosa que la actual (unidades a metros): se pide la equivalencia. */
  protected readonly otraMedida = computed(() => {
    if (!this.cambiaUnidad()) {
      return false;
    }
    const antes = this.unidades().find(u => u.id === this.unidadOriginal());
    const despues = this.unidades().find(u => u.id === this.unidadElegida());
    return !!antes && !!despues && antes.magnitud !== despues.magnitud;
  });
  protected readonly codigoUnidadOriginal = computed(() => this.unidades().find(u => u.id === this.unidadOriginal())?.codigo ?? '');
  protected readonly codigoUnidadElegida = computed(() => this.unidades().find(u => u.id === this.unidadElegida())?.codigo ?? '');
  /** Spec 038: cuántas unidades nuevas equivale 1 unidad actual (1 UND = 1 M). */
  protected readonly equivalencia = signal<number | null>(null);
  protected readonly puedeEditarCategorias = computed(() => this.sesion.tienePermiso('CATALOGO_BASE_GESTIONAR'));

  ngOnInit(): void {
    this.servicio.categorias().subscribe({ next: l => this.categorias.set(l), error: () => undefined });
    this.servicio.sugerencias().subscribe({ next: l => this.sugerencias.set(l), error: () => undefined });
    this.cargar();
  }

  /** @param bajo solo los que están bajo el mínimo */
  protected filtrarBajo(bajo: boolean): void {
    this.soloBajo.set(bajo);
    this.cargar();
  }

  /** @param id categoría ({@code null} = todas) */
  protected filtrarCategoria(id: number | null): void {
    this.categoriaId.set(id);
    this.cargar();
  }

  /** @param f fila; ancho de la barra de stock frente al máximo (solo dibujo) */
  protected ancho(f: Fila): number {
    const tope = f.stockMaximo || (f.stockMinimo ?? 0) * 2 || 1;
    return Math.min(100, Math.round(((f.stock ?? 0) / tope) * 100));
  }

  /** @param f fila @returns si tiene máximo y su stock lo pasa (D17: se permite, se avisa) */
  protected sobreMaximo(f: Fila): boolean {
    return f.stockMaximo != null && (f.stock ?? 0) > f.stockMaximo;
  }

  /** @param f producto general; despliega o recoge sus variantes */
  protected alternar(f: Fila): void {
    const id = f.productoId!;
    this.abiertos.update(s => {
      const nuevo = new Set(s);
      if (!nuevo.delete(id)) {
        nuevo.add(id);
      }
      return nuevo;
    });
  }

  /** @param f fila */
  protected estado(f: Fila): { texto: string; tono: string } {
    if (f.esAgrupador) {
      return { texto: `${this.variantesPorGeneral().get(f.productoId!) ?? 0} variantes`, tono: 'gris' };
    }
    if ((f.stock ?? 0) <= 0) {
      return { texto: 'Sin existencias', tono: 'rojo' };
    }
    if (this.sobreMaximo(f)) {
      return { texto: 'Sobre el máximo', tono: 'azul' };
    }
    return f.bajoMinimo ? { texto: 'Bajo el mínimo', tono: 'ambar' } : { texto: 'Normal', tono: 'verde' };
  }

  /** @param tipo abre el alta corta de una categoría o un proveedor, sin perder lo escrito en el producto */
  protected abrirAlVuelo(tipo: 'categoria' | 'proveedor'): void {
    this.alVuelo.set(tipo);
    this.magnitudNueva.set(null);
    this.nombreNuevo.set('');
    this.nitNuevo.set('');
  }

  /** Guarda la categoría o el proveedor nuevo y lo deja elegido en el formulario del producto (spec 026). */
  protected guardarAlVuelo(): void {
    const nombre = this.nombreNuevo().trim();
    if (!nombre) {
      this.avisos.ok('Escribe el nombre.');
      return;
    }
    if (this.alVuelo() === 'categoria') {
      this.servicio.crearCategoria(nombre, this.magnitudNueva()).subscribe({
        next: c => {
          this.categorias.update(l => [...l, c].sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '')));
          this.nuevo.controls.categoriaId.setValue(c.id!);
          this.alVuelo.set(null);
          this.avisos.ok(`Categoría ${c.nombre} creada y elegida.`);
        },
        error: e => this.avisos.error(e),
      });
      return;
    }
    const nit = this.nitNuevo().trim();
    this.servicio.crearProveedor({ razonSocial: nombre, nit: nit || undefined }).subscribe({
      next: p => {
        this.proveedores.update(l => [...l, p].sort((a, b) => (a.razonSocial ?? '').localeCompare(b.razonSocial ?? '')));
        this.nuevo.controls.proveedorReferenciaId.setValue(p.id!);
        this.alVuelo.set(null);
        this.avisos.ok(`Proveedor ${p.razonSocial} creado y elegido.`);
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param medida lo que mide la categoría elegida en el formulario (spec 038); se guarda en la categoría */
  protected guardarMedidaCategoria(medida: Categoria['magnitud'] | null): void {
    const c = this.categorias().find(x => x.id === this.categoriaElegida());
    if (!c) {
      return;
    }
    this.servicio.actualizarCategoria({ ...c, magnitud: medida ?? undefined }).subscribe({
      next: r => {
        this.categorias.update(l => l.map(x => (x.id === r.id ? r : x)));
        this.todasLasUnidades.set(false);
        this.avisos.ok(medida ? `${r.nombre} ahora mide ${MEDIDAS.find(m => m.valor === medida)?.texto.split(' (')[0].toLowerCase()}.`
          : `${r.nombre} ya no dice qué mide.`);
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param p panel que se abre (o se cierra si ya estaba abierto) */
  protected abrir(p: 'nuevo' | 'reorden' | 'inactivos'): void {
    this.panel.set(this.panel() === p ? 'ninguno' : p);
    if (p === 'nuevo') {
      this.editandoId.set(null);
      this.editando.set(null);
      this.conVariantes.set(false);
      this.heredados(false);
      this.unidadOriginal.set(null);
      this.todasLasUnidades.set(false);
      this.nuevo.reset({ codigo: '', nombre: '', categoriaId: 0, unidadMedidaId: 0, stockMinimo: 0, stockMaximo: null, costoReferencia: null,
        tarifaImpuestoId: null, proveedorReferenciaId: null, costoReferenciaFecha: null });
      this.alVuelo.set(null);
      this.cargarUnidades();
    }
    if (p === 'inactivos' && this.panel() === 'inactivos') {
      this.cargarInactivos();
    }
  }

  /** @param f fila; abre el formulario con los datos del producto para corregirlos (el stock no se edita aquí) */
  protected editar(f: Fila): void {
    if (!f.productoId) {
      return;
    }
    this.cargarUnidades();
    this.servicio.producto(f.productoId).subscribe({
      next: p => {
        this.editandoId.set(p.id ?? null);
        this.editando.set(p);
        this.conVariantes.set(false);
        this.unidadOriginal.set(p.unidadMedida?.id ?? null);
        this.todasLasUnidades.set(false);
        this.equivalencia.set(null);
        this.nuevo.reset({
          codigo: p.codigo ?? '', nombre: p.nombre ?? '', categoriaId: p.categoria?.id ?? 0, unidadMedidaId: p.unidadMedida?.id ?? 0,
          stockMinimo: p.stockMinimo ?? 0, stockMaximo: p.stockMaximo ?? null, costoReferencia: p.costoReferencia ?? null,
          tarifaImpuestoId: p.tarifaImpuestoId ?? null, proveedorReferenciaId: p.proveedorReferenciaId ?? null,
          costoReferenciaFecha: p.costoReferenciaFecha ?? null,
        });
        // spec 028: una variante hereda nombre, categoría, unidad e impuesto de su producto general
        this.heredados(!!p.agrupadorId);
        this.alVuelo.set(null);
        this.panel.set('nuevo');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param f fila; desactiva el producto (deja de ofrecerse; su historial y stock se conservan) */
  protected desactivar(f: Fila): void {
    const aviso = f.esAgrupador ? ' También se desactivan todas sus variantes.' : '';
    if (!f.productoId || !window.confirm(`¿Desactivar "${f.nombre}"? Deja de ofrecerse en solicitudes y cotizaciones; su historial se conserva.${aviso}`)) {
      return;
    }
    this.servicio.cambiarEstadoProducto(f.productoId, false).subscribe({
      next: () => {
        this.avisos.ok(`${f.nombre} quedó inactivo.`);
        this.cargar();
        if (this.panel() === 'inactivos') {
          this.cargarInactivos();
        }
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param p producto inactivo; lo vuelve a activar */
  protected activar(p: Producto): void {
    if (!p.id) {
      return;
    }
    this.servicio.cambiarEstadoProducto(p.id, true).subscribe({
      next: () => {
        this.avisos.ok(`${p.nombre} quedó activo.`);
        this.cargarInactivos();
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param bloquear {@code true} deja fijos los datos que una variante hereda de su producto general */
  private heredados(bloquear: boolean): void {
    for (const c of [this.nuevo.controls.nombre, this.nuevo.controls.categoriaId, this.nuevo.controls.unidadMedidaId, this.nuevo.controls.tarifaImpuestoId]) {
      if (bloquear) {
        c.disable();
      } else {
        c.enable();
      }
    }
  }

  /** Crea el producto general con sus variantes (spec 028): datos comunes del formulario y combinaciones de la tabla. */
  protected crearConVariantes(): void {
    const tabla = this.variantes();
    const v = this.nuevo.getRawValue();
    if (this.nuevo.controls.codigo.invalid || this.nuevo.controls.nombre.invalid || !v.categoriaId || !v.unidadMedidaId) {
      this.nuevo.markAllAsTouched();
      this.avisos.ok('Completa código, nombre, categoría y unidad del producto.');
      return;
    }
    if (!tabla?.valido()) {
      this.avisos.ok('Revisa las variantes: cada una necesita su código y su mínimo.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.crearConVariantes({ codigo: v.codigo, nombre: v.nombre, categoriaId: v.categoriaId, unidadMedidaId: v.unidadMedidaId,
      tarifaImpuestoId: v.tarifaImpuestoId ?? undefined, ...tabla.datos() }).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.avisos.ok(`Producto ${r.agrupador?.codigo} creado con ${r.variantes?.length ?? 0} variantes.`);
        this.conVariantes.set(false);
        this.nuevo.reset();
        this.panel.set('ninguno');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Tras guardar las variantes de un producto general, recarga la tabla. */
  protected alGuardarVariantes(): void {
    this.cargar();
  }

  private cargarUnidades(): void {
    if (!this.unidades().length) {
      this.servicio.unidades().subscribe({ next: l => this.unidades.set(l), error: () => undefined });
    }
    // spec 026: impuestos y proveedores para el formulario (si el rol no los ve, quedan vacíos y no se muestran)
    if (!this.impuestos().length) {
      this.ventas.impuestos().subscribe({ next: l => this.impuestos.set(l.filter(t => t.activo !== false)), error: () => undefined });
    }
    if (!this.proveedores().length) {
      this.servicio.proveedoresHabilitados().subscribe({ next: p => this.proveedores.set(p.contenido ?? []), error: () => undefined });
    }
  }

  private cargarInactivos(): void {
    this.inactivos.set(null);
    this.servicio.productos({ activo: false }).subscribe({ next: p => this.inactivos.set(p.contenido ?? []), error: e => { this.inactivos.set([]); this.avisos.error(e); } });
  }

  protected exportarPorCorreo(): void {
    this.servicio.stockPorCorreo().subscribe({
      next: r => this.avisos.ok(`Enviamos ${r.archivo ?? 'el Excel'} a ${r.para}.`),
      error: e => this.avisos.error(e),
    });
  }

  protected exportar(): void {
    this.servicio.exportarStock().subscribe({ next: b => descargar(b, 'inventario-stock.xlsx'), error: e => this.avisos.error(e) });
  }

  protected crearProducto(): void {
    if (this.nuevo.invalid) {
      this.nuevo.markAllAsTouched();
      return;
    }
    const v = this.nuevo.getRawValue();
    const id = this.editandoId();
    if (id && this.otraMedida() && !((this.equivalencia() ?? 0) > 0)) {
      this.avisos.ok(`Escribe cuántos ${this.codigoUnidadElegida()} equivale 1 ${this.codigoUnidadOriginal()}.`);
      return;
    }
    const datos = { ...v, stockMaximo: v.stockMaximo ?? undefined, costoReferencia: v.costoReferencia ?? undefined,
      tarifaImpuestoId: v.tarifaImpuestoId ?? undefined, proveedorReferenciaId: v.proveedorReferenciaId ?? undefined,
      costoReferenciaFecha: v.costoReferenciaFecha || undefined,
      ...(id && this.otraMedida() ? { equivalenciaUnidad: this.equivalencia()! } : {}) };
    this.trabajando.set(true);
    (id ? this.servicio.actualizarProducto(id, datos) : this.servicio.crear(datos)).subscribe({
      next: p => {
        this.trabajando.set(false);
        this.avisos.ok(`Producto ${p.codigo} ${id ? 'actualizado' : 'creado'}.`);
        this.editandoId.set(null);
        this.nuevo.reset();
        this.panel.set('ninguno');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected crearBorrador(): void {
    const fecha = new Date();
    fecha.setDate(fecha.getDate() + 7);
    this.trabajando.set(true);
    this.servicio.borradorReorden(iso(fecha), this.porReponer().map(s => s.productoId!)).subscribe({
      next: s => {
        this.trabajando.set(false);
        this.avisos.ok(`Borrador ${s.consecutivo} creado. Revísalo y envíalo cuando quieras.`);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    this.filas.set(null);
    if (this.conReporte()) {
      this.servicio.resumen(this.categoriaId(), this.soloBajo()).subscribe({
        next: r => {
          this.resumen.set(r);
          this.filas.set(r.items ?? []);
        },
        error: e => {
          this.filas.set([]);
          this.avisos.error(e);
        },
      });
      return;
    }
    this.servicio.productos({ categoriaId: this.categoriaId() }).subscribe({
      next: p => this.filas.set((p.contenido ?? [])
        .filter(x => !this.soloBajo() || x.bajoStockMinimo)
        .map(x => ({
          productoId: x.id, codigo: x.codigo, nombre: x.nombre, categoria: x.categoria?.nombre, unidad: x.unidadMedida?.codigo,
          stock: x.stockTotal, stockMinimo: x.stockMinimo, stockMaximo: x.stockMaximo, bajoMinimo: x.bajoStockMinimo && !x.esAgrupador,
          esAgrupador: x.esAgrupador, agrupadorId: x.agrupadorId,
        }))),
      error: e => {
        this.filas.set([]);
        this.avisos.error(e);
      },
    });
  }
}
