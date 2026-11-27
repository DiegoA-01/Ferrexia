import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, Subject, catchError, debounceTime, map, of, switchMap } from 'rxjs';

import { Categoria, Producto, ProveedorElegido, Solicitud, UnidadMedida } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { codigoError, mensajeError } from '../../core/http/api';
import { ComprasService } from '../../core/servicios/compras.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { iso } from '../../core/servicios/reportes.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

import { Cifra } from '../../core/ui/cifra';
/** Producto pedido en la solicitud. */
interface LineaPedida {
  producto: Producto;
  cantidad: number | null;
}

/**
 * Crear o editar una solicitud de compra (UC-04). Se guarda en borrador; el monto estimado lo calcula el backend
 * con el costo de referencia, y al enviarla valida presupuesto y duplicados.
 */
@Component({
  selector: 'sc-solicitud-form',
  imports: [Cifra, FormsModule, RouterLink, Estado, PesosPipe, NumeroPipe],
  templateUrl: './solicitud-form.html',
  styleUrl: './solicitud-form.scss',
})
export class SolicitudForm implements OnInit {
  private readonly compras = inject(ComprasService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);

  /** Id de la solicitud a editar (de la ruta); vacío al crear. */
  readonly id = input<string>();

  protected readonly guardada = signal<Solicitud | null>(null);
  protected readonly motivo = signal('');
  protected readonly fecha = signal(iso(new Date(Date.now() + 7 * 86_400_000)));
  protected readonly lineas = signal<LineaPedida[]>([]);
  protected readonly busqueda = signal('');
  protected readonly resultados = signal<Producto[]>([]);
  protected readonly trabajando = signal(false);
  protected readonly listaAbierta = signal(false);
  protected readonly cargandoLista = signal(false);
  /** La lista no es la coincidencia exacta sino productos parecidos (primera palabra). */
  protected readonly parecidos = signal(false);
  // Alta de un producto nuevo desde la misma pantalla (solo quien administra compras)
  protected readonly creando = signal(false);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly unidades = signal<UnidadMedida[]>([]);
  protected readonly nuevoCodigo = signal('');
  protected readonly nuevoNombre = signal('');
  protected readonly nuevaCategoria = signal<number | null>(null);
  protected readonly nuevaUnidad = signal<number | null>(null);
  protected readonly nuevoCosto = signal<number | null>(null);
  protected readonly puedeCrear = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']));
  protected readonly sinGuardar = signal(false);
  /** El envío respondió que excede el presupuesto o que está duplicada: se puede reenviar con justificación. */
  protected readonly excede = signal<string | null>(null);
  protected readonly justificacion = signal('');
  protected readonly hoy = iso(new Date());
  /** Spec 033: a quién se le pide y los proveedores elegidos. */
  protected readonly modo = signal<'ABIERTA' | 'ESPECIFICA'>('ABIERTA');
  protected readonly elegidos = signal<number[]>([]);
  protected readonly proveedores = signal<ProveedorElegido[]>([]);
  protected readonly filtroProveedor = signal('');
  protected readonly proveedoresFiltrados = computed(() => {
    const t = this.filtroProveedor().trim().toLowerCase();
    return this.proveedores().filter(p => !t || (p.razonSocial ?? '').toLowerCase().includes(t));
  });
  /** Nombres de los elegidos (del catálogo o de la solicitud guardada). */
  protected readonly nombresElegidos = computed(() => {
    const nombres = new Map<number, string>();
    (this.guardada()?.proveedores ?? []).forEach(p => nombres.set(p.id!, p.razonSocial ?? ''));
    this.proveedores().forEach(p => nombres.set(p.id!, p.razonSocial ?? ''));
    return this.elegidos().map(id => nombres.get(id) ?? `Proveedor ${id}`);
  });
  /** Spec 033 (D21): solo quien tiene el permiso puede enviar con justificación una solicitud con alerta. */
  protected readonly puedeExceptuar = computed(() => this.sesion.tienePermiso('SOLICITUD_EXCEPCION'));
  /** La alerta bloquea: quien no tiene el permiso no puede seguir. */
  protected readonly bloqueada = computed(() => !!this.excede() && !this.puedeExceptuar());

  protected readonly editable = computed(() => {
    const e = this.guardada()?.estado;
    return !e || e === 'BORRADOR' || e === 'DEVUELTA';
  });
  protected readonly devueltaCon = computed(() =>
    this.guardada()?.estado === 'DEVUELTA'
      ? [...(this.guardada()?.aprobaciones ?? [])].reverse().find(a => a.decision === 'DEVUELTA')?.observaciones ?? null
      : null);

  private readonly buscar$ = new Subject<string>();

  ngOnInit(): void {
    this.buscar$.pipe(debounceTime(200), switchMap(t => this.consultar(t)))
      .subscribe(r => {
        this.resultados.set(r.lista);
        this.parecidos.set(r.parecidos);
        this.cargandoLista.set(false);
      });
    this.compras.proveedoresElegibles().subscribe({ next: l => this.proveedores.set(l), error: () => undefined });
    const id = Number(this.id());
    if (id) {
      this.compras.obtener(id).subscribe({ next: s => this.aplicar(s), error: e => this.avisos.error(e) });
    }
  }

  /** Busca por código o nombre; sin texto trae los primeros del catálogo. Si nada coincide con varias palabras, prueba con la primera. */
  private consultar(texto: string): Observable<{ lista: Producto[]; parecidos: boolean }> {
    this.cargandoLista.set(true);
    return this.inventario.productos({ texto: texto || null, tamano: 10, agrupadores: false }).pipe(
      switchMap(p => {
        const lista = p.contenido ?? [];
        const primera = texto.split(/\s+/)[0];
        if (!lista.length && primera && primera !== texto) {
          return this.inventario.productos({ texto: primera, tamano: 10, agrupadores: false }).pipe(map(q => ({ lista: q.contenido ?? [], parecidos: true })));
        }
        return of({ lista, parecidos: false });
      }),
      catchError(() => of({ lista: [] as Producto[], parecidos: false })));
  }

  /** @param texto lo que se busca (código o nombre) */
  protected buscar(texto: string): void {
    this.busqueda.set(texto);
    this.listaAbierta.set(true);
    this.cargandoLista.set(true);
    this.buscar$.next(texto.trim());
  }

  /** Al poner el cursor en el campo se abre el catálogo, aunque no se haya escrito nada. */
  protected abrirLista(): void {
    this.listaAbierta.set(true);
    this.cargandoLista.set(true);
    this.buscar$.next(this.busqueda().trim());
  }

  /** Cierra la lista cuando el foco sale del bloque de búsqueda. */
  protected alSalir(e: FocusEvent): void {
    const bloque = e.currentTarget as HTMLElement;
    if (!bloque.contains(e.relatedTarget as Node | null)) {
      this.listaAbierta.set(false);
    }
  }

  /** @param p producto elegido de la búsqueda */
  protected agregar(p: Producto): void {
    if (!this.lineas().some(l => l.producto.id === p.id)) {
      this.lineas.update(l => [...l, { producto: p, cantidad: 1 }]);
      this.sinGuardar.set(true);
    }
    this.busqueda.set('');
    this.resultados.set([]);
    this.listaAbierta.set(false);
  }

  /** Abre el formulario de producto nuevo (con el texto buscado como nombre) y trae categorías y unidades. */
  protected abrirCrear(): void {
    this.listaAbierta.set(false);
    this.creando.set(true);
    this.nuevoNombre.set(this.busqueda().trim());
    if (!this.categorias().length) {
      this.inventario.categorias().subscribe({ next: l => this.categorias.set(l), error: e => this.avisos.error(e) });
      this.inventario.unidades().subscribe({ next: l => this.unidades.set(l), error: e => this.avisos.error(e) });
    }
  }

  /** Crea el producto en el catálogo y lo deja agregado a la solicitud. */
  protected crearProducto(): void {
    if (!this.nuevoCodigo().trim() || !this.nuevoNombre().trim() || !this.nuevaCategoria() || !this.nuevaUnidad()) {
      this.avisos.ok('Completa código, nombre, categoría y unidad.');
      return;
    }
    this.trabajando.set(true);
    this.inventario.crear({
      codigo: this.nuevoCodigo().trim(), nombre: this.nuevoNombre().trim(), categoriaId: this.nuevaCategoria()!,
      unidadMedidaId: this.nuevaUnidad()!, stockMinimo: 0, costoReferencia: this.nuevoCosto() ?? undefined,
    }).subscribe({
      next: p => {
        this.trabajando.set(false);
        this.creando.set(false);
        this.nuevoCodigo.set('');
        this.nuevoNombre.set('');
        this.nuevoCosto.set(null);
        this.agregar(p);
        this.avisos.ok(`${p.nombre} creado y agregado.`);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param i posición; @param cantidad cantidad pedida */
  protected cambiarCantidad(i: number, cantidad: number | null): void {
    this.lineas.update(l => l.map((x, j) => (j === i ? { ...x, cantidad } : x)));
    this.sinGuardar.set(true);
  }

  /** @param modo a quién se le pide (spec 033) */
  protected cambiarModo(modo: 'ABIERTA' | 'ESPECIFICA'): void {
    this.modo.set(modo);
    this.sinGuardar.set(true);
  }

  /** @param id proveedor que se marca o desmarca */
  protected alternarProveedor(id: number): void {
    this.elegidos.update(l => (l.includes(id) ? l.filter(x => x !== id) : [...l, id]));
    this.sinGuardar.set(true);
  }

  /** @param i posición de la línea a quitar */
  protected quitar(i: number): void {
    this.lineas.update(l => l.filter((_, j) => j !== i));
    this.sinGuardar.set(true);
  }

  /** Guarda el borrador; si {@code luegoEnviar}, lo manda a aprobación. */
  protected guardar(luegoEnviar = false): void {
    if (!this.motivo().trim() || !this.fecha() || !this.lineas().length || this.lineas().some(l => !l.cantidad || l.cantidad <= 0)) {
      this.avisos.ok('Completa la justificación, la fecha y al menos un producto con cantidad mayor que cero.');
      return;
    }
    if (this.modo() === 'ESPECIFICA' && !this.elegidos().length) {
      this.avisos.ok('Elige al menos un proveedor o deja la solicitud abierta a todos.');
      return;
    }
    const datos = {
      motivo: this.motivo().trim(),
      fechaRequerida: this.fecha(),
      lineas: this.lineas().map(l => ({ productoId: l.producto.id!, unidadMedidaId: l.producto.unidadMedida!.id!, cantidad: l.cantidad! })),
      modoProveedor: this.modo(),
      proveedorIds: this.modo() === 'ESPECIFICA' ? this.elegidos() : [],
    };
    const id = this.guardada()?.id;
    this.trabajando.set(true);
    (id ? this.compras.actualizar(id, datos) : this.compras.crear(datos)).subscribe({
      next: s => {
        this.aplicar(s);
        this.sinGuardar.set(false);
        if (luegoEnviar) {
          this.enviar();
        } else {
          this.trabajando.set(false);
          this.avisos.ok(`${s.consecutivo} guardada como borrador.`);
          if (!id) {
            void this.router.navigate(['/app/solicitudes', s.id, 'editar'], { replaceUrl: true });
          }
        }
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Envía a aprobación; con justificación, como excepción al presupuesto. */
  protected enviar(): void {
    const s = this.guardada();
    if (!s?.id) {
      return;
    }
    if (this.bloqueada()) {
      return;
    }
    if (this.excede() && !this.justificacion().trim()) {
      this.avisos.ok('Escribe por qué se necesita aunque exceda el presupuesto.');
      return;
    }
    this.trabajando.set(true);
    this.compras.enviar(s.id, this.excede() ? this.justificacion().trim() : undefined).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.avisos.ok(`${r.consecutivo} enviada a aprobación.`);
        void this.router.navigate(['/app/solicitudes'], { queryParams: { texto: r.consecutivo } });
      },
      error: e => {
        this.trabajando.set(false);
        const codigo = codigoError(e);
        if (codigo === 'PRESUPUESTO_EXCEDIDO' || codigo === 'SOLICITUD_DUPLICADA') {
          // Spec 033: el backend explica el motivo y qué hacer. Solo quien tiene el permiso la envía con justificación.
          this.excede.set(mensajeError(e));
          return;
        }
        this.avisos.error(e);
      },
    });
  }

  private aplicar(s: Solicitud): void {
    this.guardada.set(s);
    this.motivo.set(s.motivo ?? '');
    this.modo.set(s.modoProveedor ?? 'ABIERTA');
    this.elegidos.set((s.proveedores ?? []).map(p => p.id!));
    if (s.fechaRequerida) {
      this.fecha.set(s.fechaRequerida);
    }
    // Las líneas guardadas traen código, nombre y costo; se completan como producto para editarlas.
    this.lineas.set((s.lineas ?? []).map(l => ({
      producto: {
        id: l.productoId, codigo: l.productoCodigo, nombre: l.productoNombre, costoReferencia: l.costoReferencia,
        unidadMedida: { id: l.unidadMedidaId, codigo: l.unidadCodigo },
      },
      cantidad: l.cantidad ?? null,
    })));
  }
}
