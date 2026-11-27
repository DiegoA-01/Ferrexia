import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { HttpErrorResponse } from '@angular/common/http';

import {
  EstadoProductoDevuelto, EstadoVenta, FormaReembolso, LimiteDescuento, Pagina, Turno, Venta, VentaResumen,
} from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { CajaService } from '../../core/servicios/caja.service';
import { VentaService } from '../../core/servicios/venta.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { AutorizacionPin } from '../caja/autorizacion-pin';
import { CambioPago, MEDIOS_CAJA } from './cambio-pago';
import { MEDIOS_VENTA } from '../pos/pos';
import { imprimirTiquete } from '../pos/tiquete';

import { Cifra } from '../../core/ui/cifra';
/** Línea en el formulario de devolución. */
interface LineaDevuelta {
  ventaLineaId: number;
  nombre: string;
  unidad: string;
  maximo: number;
  cantidad: number;
  estado: EstadoProductoDevuelto;
}

/** Texto de cada forma de reembolso. */
const REEMBOLSOS: Record<FormaReembolso, string> = {
  EFECTIVO: 'Efectivo del turno', MISMO_MEDIO: 'Mismo medio de pago', SALDO_A_FAVOR: 'Saldo a favor del cliente',
};

/** Nombre de cada rol en los límites de descuento. */
const ROLES_LIMITE: Record<string, string> = { CAJERO: 'Cajero', SUPERVISOR_VENTAS: 'Supervisor de ventas', VENDEDOR: 'Vendedor' };

/**
 * Historial de ventas (spec 005): supervisor, dueño y auditor ven todas; el vendedor, las suyas. Detalle con líneas y
 * pagos, y reimpresión del tiquete (sale como COPIA y queda en la auditoría). El dueño fija aquí el descuento máximo de
 * cada rol. El cajero ve las ventas que él cobró (filtra por fecha y estado) y, con el consecutivo, encuentra la de otro
 * cajero para anularla en su turno o recibir una devolución (spec 006), siempre con el PIN de un supervisor.
 */
@Component({
  selector: 'sc-ventas',
  imports: [Cifra, FormsModule, RouterLink, PesosPipe, NumeroPipe, FechaPipe, AutorizacionPin, CambioPago],
  templateUrl: './ventas.html',
  host: { '(document:keydown.escape)': 'alEscape()' },
  styles: `
    .filtros { display: flex; flex-wrap: wrap; gap: 12px; padding: 14px 20px; border-bottom: 1px solid var(--sc-borde); align-items: flex-end;
      .campo { min-width: 150px; } }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .detalle { display: grid; gap: 12px; h2 { margin: 0; } }
    .velo-venta { position: fixed; inset: 0; z-index: 30; display: flex; align-items: center; justify-content: center; padding: 16px; overflow-y: auto;
      background: rgba(15, 23, 42, .5); }
    @supports (backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px)) {
      .velo-venta { background: rgba(15, 23, 42, .35); -webkit-backdrop-filter: blur(6px); backdrop-filter: blur(6px); }
    }
    .modal-venta { width: min(820px, 100%); max-height: 100%; overflow-y: auto; margin: auto; outline: none; padding: 24px 26px; gap: 14px;
      border-radius: 24px; color: var(--sc-texto); border: 1px solid rgba(255, 255, 255, .55);
      background: rgba(255, 255, 255, .97);
      box-shadow: 0 24px 60px rgba(15, 23, 42, .28), 0 4px 14px rgba(15, 23, 42, .12), inset 0 1px 0 rgba(255, 255, 255, .9);
      animation: abre-venta .16s ease-out; }
    @supports (backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px)) {
      .modal-venta { background: linear-gradient(180deg, rgba(255, 255, 255, .88), rgba(255, 255, 255, .74));
        -webkit-backdrop-filter: blur(22px) saturate(1.6); backdrop-filter: blur(22px) saturate(1.6); }
    }
    @keyframes abre-venta { from { opacity: 0; transform: scale(.97); } to { opacity: 1; transform: scale(1); } }
    @media (prefers-reduced-motion: reduce) { .modal-venta { animation: none; } }
    @media (max-width: 600px) { .velo-venta { padding: 12px; } .modal-venta { padding: 18px 16px; border-radius: 20px; } }
    .enc-venta { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 16px;
      h2 { display: flex; align-items: center; gap: 10px; font-size: 22px; } }
    .acciones-venta { flex-wrap: wrap; gap: 8px; }
    .datos-venta { font-size: 12.5px; color: var(--sc-texto-2); line-height: 1.5; margin-top: -6px; }
    .modal-venta .boton.anular { background: #c62828; border-color: #c62828; color: #fff; }
    .modal-venta .boton.anular:hover:not(:disabled) { background: #8e1b1b; border-color: #8e1b1b; }
    .modal-venta .boton.anular:focus-visible { outline: 3px solid rgba(198, 40, 40, .45); outline-offset: 2px; }
    .modal-venta .boton.anular:disabled { background: #e3b4b4; border-color: #e3b4b4; color: #fff; cursor: not-allowed; opacity: .8; }
    .totales { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 10px; margin: 0;
      > div { padding: 10px 12px; border-radius: 14px; background: rgba(255, 255, 255, .6); border: 1px solid rgba(255, 255, 255, .8);
        box-shadow: 0 1px 3px rgba(15, 23, 42, .08); }
      dt { font-size: 12px; color: var(--sc-texto-2); } dd { margin: 2px 0 0; font-weight: 700; font-size: 15px; } }
    .limites { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin: 12px 0; }
    tr.anulada td { color: var(--sc-texto-3); text-decoration: line-through; }
    .correccion { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--sc-borde); border-radius: 12px; }
    .devolver td { vertical-align: middle; }
    .mini { width: 90px; text-align: right; }
  `,
})
export class Ventas implements OnInit {
  private readonly ventas = inject(VentaService);
  private readonly caja = inject(CajaService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly medios = MEDIOS_VENTA;
  protected readonly pagina = signal<Pagina<VentaResumen> | null>(null);
  protected readonly desde = signal('');
  protected readonly hasta = signal('');
  protected readonly estado = signal<EstadoVenta | null>(null);
  protected readonly consecutivo = signal('');
  protected readonly detalle = signal<Venta | null>(null);
  protected readonly limites = signal<LimiteDescuento[]>([]);
  protected readonly reembolsos = REEMBOLSOS;
  protected readonly formas = Object.keys(REEMBOLSOS) as FormaReembolso[];
  /** Turno abierto de la sesión (para anular o devolver). */
  protected readonly miTurno = signal<Turno | null>(null);
  protected readonly correccion = signal<'anular' | 'devolver' | 'anularOtro' | 'pago' | null>(null);
  protected readonly motivo = signal('');
  protected readonly pidiendoPin = signal(false);
  protected readonly lineasDevueltas = signal<LineaDevuelta[]>([]);
  protected readonly forma = signal<FormaReembolso>('EFECTIVO');
  protected readonly enviando = signal(false);

  protected readonly esDueno = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly veLimites = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA', 'SUPERVISOR_VENTAS', 'AUDITOR']));
  /** Quien puede cobrar (anular en su turno) o recibir devoluciones necesita saber su turno abierto; lo decide el permiso, no el rol. */
  protected readonly cobra = computed(() => this.sesion.tienePermiso('VENTA_COBRAR') && !this.sesion.soloLectura());
  /** Permiso de devolver (el backend pide DEVOLUCION_REGISTRAR). */
  protected readonly devuelve = computed(() => this.sesion.tienePermiso('DEVOLUCION_REGISTRAR') && !this.sesion.soloLectura());
  /** Quien no tiene VENTA_VER_TODAS ve las ventas que cobró o vendió (el aviso lo explica). */
  protected readonly soloPropias = computed(() => !this.sesion.tienePermiso('VENTA_VER_TODAS'));
  /** Consecutivos sugeridos mientras se escribe en el campo Consecutivo. */
  protected readonly sugerencias = signal<string[]>([]);
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  protected readonly puedeAnular = computed(() => {
    const d = this.detalle();
    return !!d && this.cobra() && d.estado === 'CONFIRMADA' && !!d.turnoAbierto && this.miTurno()?.id === d.turnoId
      && (d.totalDevuelto ?? 0) === 0;
  });
  /** Spec 029: con el permiso se anula una factura de un turno ya cerrado (el dinero sale del turno de la sesión). */
  protected readonly puedeAnularOtro = computed(() => {
    const d = this.detalle();
    return !!d && !this.sesion.soloLectura() && this.sesion.tienePermiso('VENTA_ANULAR_OTRO_TURNO') && d.estado === 'CONFIRMADA'
      && !d.turnoAbierto && (d.totalDevuelto ?? 0) === 0;
  });
  /**
   * Spec 029: cambia el método de pago quien tiene el permiso, o el cajero en su turno abierto (con PIN). Solo facturas
   * pagadas con medios de caja y sin devoluciones.
   */
  protected readonly puedeCambiarPago = computed(() => {
    const d = this.detalle();
    if (!d || this.sesion.soloLectura() || d.estado !== 'CONFIRMADA' || (d.totalDevuelto ?? 0) > 0
      || !(d.pagos ?? []).every(p => MEDIOS_CAJA.includes(p.medio!))) {
      return false;
    }
    return this.sesion.tienePermiso('VENTA_CAMBIAR_PAGO') || (this.cobra() && !!d.turnoAbierto && this.miTurno()?.id === d.turnoId);
  });
  protected readonly puedeDevolver = computed(() => {
    const d = this.detalle();
    return !!d && this.devuelve() && !!this.miTurno() && d.estado === 'CONFIRMADA'
      && (d.lineas ?? []).some(l => (l.cantidad ?? 0) > (l.cantidadDevuelta ?? 0));
  });
  protected readonly totalDevolucionValido = computed(() =>
    this.motivo().trim().length >= 5 && this.lineasDevueltas().some(l => l.cantidad > 0)
    && this.lineasDevueltas().every(l => l.cantidad >= 0 && l.cantidad <= l.maximo));

  ngOnInit(): void {
    if (this.sesion.tienePermiso('VENTA_COBRAR') || this.sesion.tienePermiso('DEVOLUCION_REGISTRAR')) {
      this.caja.actual().subscribe({ next: t => this.miTurno.set(t), error: (e: HttpErrorResponse) => e.status === 404 || this.avisos.error(e) });
    }
    this.buscar();
    if (this.veLimites()) {
      this.ventas.limites().subscribe({ next: l => this.limites.set(l), error: e => this.avisos.error(e) });
    }
  }

  /** @param pagina página */
  protected buscar(pagina = 0): void {
    this.sugerencias.set([]);
    this.ventas.buscar({ desde: this.desde() || null, hasta: this.hasta() || null, estado: this.estado(),
      consecutivo: this.consecutivo().trim() || null, pagina }).subscribe({ next: p => this.pagina.set(p), error: e => this.avisos.error(e) });
  }

  /**
   * Al escribir en Consecutivo: con 2 o más caracteres y una pausa corta pide hasta 10 consecutivos que empiecen por
   * el texto (mismas fechas y estado). Si lo escrito coincide con una sugerencia, busca esa venta.
   *
   * @param texto lo escrito
   */
  protected escribirConsecutivo(texto: string): void {
    this.consecutivo.set(texto);
    if (this.temporizador) {
      clearTimeout(this.temporizador);
    }
    const t = texto.trim();
    if (t.length < 2) {
      this.sugerencias.set([]);
      return;
    }
    if (this.sugerencias().includes(t)) {
      this.buscar();
      return;
    }
    this.temporizador = setTimeout(() => {
      this.ventas.buscar({ desde: this.desde() || null, hasta: this.hasta() || null, estado: this.estado(),
        consecutivo: t, tamano: 10 }).subscribe({
        next: p => this.sugerencias.set((p.contenido ?? []).map(v => v.consecutivo ?? '').filter(Boolean)),
        error: () => this.sugerencias.set([]),
      });
    }, 300);
  }

  /** @param v venta del historial */
  protected ver(v: VentaResumen): void {
    // el detalle se abre en una ventana flotante con el foco dentro
    this.ventas.obtener(v.id!).subscribe({
      next: d => {
        this.abrir(d);
        setTimeout(() => document.querySelector<HTMLElement>('.modal-venta')?.focus());
      },
      error: e => this.avisos.error(e),
    });
  }

  /** Cierra la ventana del detalle y cualquier corrección a medias. */
  protected cerrar(): void {
    this.detalle.set(null);
    this.correccion.set(null);
    this.pidiendoPin.set(false);
  }

  /** Escape cierra la ventana salvo que se esté enviando una corrección. */
  protected alEscape(): void {
    if (this.detalle() && !this.enviando()) {
      this.cerrar();
    }
  }

  /** @param e clic en el fondo: cierra solo si cayó fuera de la ventana y no hay un panel a medio llenar */
  protected clicFondo(e: Event): void {
    if (e.target === e.currentTarget && !this.correccion() && !this.enviando()) {
      this.cerrar();
    }
  }

  /** @param d venta a mostrar; cierra cualquier corrección a medias */
  private abrir(d: Venta): void {
    this.detalle.set(d);
    this.correccion.set(null);
    this.pidiendoPin.set(false);
    this.motivo.set('');
  }

  /** Anula una factura de un turno cerrado (spec 029): con el permiso, sin PIN. */
  protected anularDeOtroTurno(): void {
    const d = this.detalle()!;
    this.enviando.set(true);
    this.ventas.anularDeOtroTurno(d.id!, this.motivo().trim()).subscribe({
      next: v => {
        this.enviando.set(false);
        this.abrir(v);
        this.avisos.ok(`Venta ${v.consecutivo} anulada: el stock volvió y el dinero salió de su turno.`);
        this.buscar(this.pagina()?.pagina ?? 0);
      },
      error: e => this.fallo(e),
    });
  }

  /** @param v venta con el pago corregido (spec 029) */
  protected alCambiarPago(v: Venta): void {
    this.abrir(v);
  }

  /** @param tipo corrección que se empieza */
  protected empezar(tipo: 'anular' | 'devolver' | 'anularOtro' | 'pago'): void {
    const d = this.detalle()!;
    this.correccion.set(tipo);
    this.motivo.set('');
    this.pidiendoPin.set(false);
    this.forma.set('EFECTIVO');
    this.lineasDevueltas.set((d.lineas ?? []).map(l => ({
      ventaLineaId: l.id!, nombre: l.nombre ?? '', unidad: l.unidad ?? '',
      maximo: (l.cantidad ?? 0) - (l.cantidadDevuelta ?? 0), cantidad: 0, estado: 'BUENO' as EstadoProductoDevuelto,
    })).filter(l => l.maximo > 0));
  }

  /** @param i línea; @param campo campo; @param valor valor */
  protected cambiarLinea<K extends keyof LineaDevuelta>(i: number, campo: K, valor: LineaDevuelta[K]): void {
    this.lineasDevueltas.update(l => l.map((x, j) => (j === i ? { ...x, [campo]: valor } : x)));
  }

  /** @param token permiso de un solo uso del supervisor */
  protected autorizado(token: string): void {
    const d = this.detalle()!;
    this.pidiendoPin.set(false);
    this.enviando.set(true);
    if (this.correccion() === 'anular') {
      this.ventas.anular(d.id!, this.motivo().trim(), token).subscribe({
        next: v => {
          this.enviando.set(false);
          this.abrir(v);
          this.avisos.ok(`Venta ${v.consecutivo} anulada: el stock y el dinero volvieron.`);
          this.buscar(this.pagina()?.pagina ?? 0);
        },
        error: e => this.fallo(e),
      });
      return;
    }
    this.ventas.devolver({
      ventaId: d.id!, motivo: this.motivo().trim(), formaReembolso: this.forma(), autorizacion: token,
      lineas: this.lineasDevueltas().filter(l => l.cantidad > 0).map(l => ({ ventaLineaId: l.ventaLineaId, cantidad: l.cantidad, estadoProducto: l.estado })),
    }).subscribe({
      next: dev => {
        this.enviando.set(false);
        this.avisos.ok(`Devolución ${dev.consecutivo} registrada por ${dev.totalReembolso}.`);
        this.ventas.obtener(d.id!).subscribe({ next: v => this.abrir(v), error: e => this.avisos.error(e) });
      },
      error: e => this.fallo(e),
    });
  }

  /** @param e error del servidor */
  private fallo(e: unknown): void {
    this.enviando.set(false);
    this.avisos.error(e);
  }

  /** @param id venta; la reimpresión sale como COPIA */
  protected reimprimir(id: number): void {
    this.ventas.tiquete(id).subscribe({ next: t => imprimirTiquete(t), error: e => this.avisos.error(e) });
  }

  /** @param rol rol; @param valor porcentaje */
  protected cambiarLimite(rol: string, valor: number | null): void {
    this.limites.update(l => l.map(x => (x.rol === rol ? { ...x, descuentoMaximoPct: valor ?? 0 } : x)));
  }

  protected guardarLimites(): void {
    this.ventas.guardarLimites(this.limites()).subscribe({
      next: l => {
        this.limites.set(l);
        this.avisos.ok('Límites de descuento guardados.');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param rol código @returns nombre */
  protected nombreRol(rol: string | undefined): string {
    return ROLES_LIMITE[rol ?? ''] ?? rol ?? '';
  }

  /** @param montos monto por medio @returns "Efectivo $ 20.000" */
  protected textoMontos(montos: Record<string, number> | undefined): string {
    return Object.entries(montos ?? {}).map(([m, v]) => `${this.textoMedio(m)} $ ${Math.round(v).toLocaleString('es-CO')}`).join(' + ');
  }

  /** @param medio medio @returns texto */
  protected textoMedio(medio: string | undefined): string {
    return (this.medios as Record<string, string>)[medio ?? ''] ?? medio ?? '';
  }
}
