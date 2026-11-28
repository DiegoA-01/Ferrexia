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
import { ESTILO_TOTALES, VentanaVidrio } from '../../core/ui/ventana-vidrio';
/** @returns la fecha de hoy en hora local del navegador, como la pide el input date (yyyy-MM-dd) */
function fechaHoy(): string {
  const d = new Date();
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}`;
}

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
  imports: [Cifra, FormsModule, RouterLink, PesosPipe, NumeroPipe, FechaPipe, AutorizacionPin, CambioPago, VentanaVidrio],
  templateUrl: './ventas.html',
  styles: [ESTILO_TOTALES, `
    .filtros { display: flex; flex-wrap: wrap; gap: 12px; padding: 14px 20px; border-bottom: 1px solid var(--sc-borde); align-items: flex-end;
      .campo { flex: 0 0 160px; min-width: 0; }
      .campo.ancho { flex-basis: 210px; }
      .entrada, .boton { height: 44px; min-height: 44px; box-sizing: border-box; }
      .boton { flex: 0 0 96px; width: 96px; padding: 0; justify-content: center; }
      .boton.activo { background: var(--sc-primario); border-color: var(--sc-primario); color: #fff; }
      .boton.activo:hover:not(:disabled) { background: var(--sc-primario-hover); } }
    @media (max-width: 640px) {
      .filtros { .campo, .campo.ancho { flex: 1 1 100%; }
        .boton { flex: 1 1 0; width: auto; } } }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .datos-venta { font-size: 12.5px; color: var(--sc-texto-2); line-height: 1.5; margin-top: -6px; }
    .boton.anular { background: #c62828; border-color: #c62828; color: #fff; }
    .boton.anular:hover:not(:disabled) { background: #8e1b1b; border-color: #8e1b1b; }
    .boton.anular:focus-visible { outline: 3px solid rgba(198, 40, 40, .45); outline-offset: 2px; }
    .boton.anular:disabled { background: #e3b4b4; border-color: #e3b4b4; color: #fff; cursor: not-allowed; opacity: .8; }
    .limites { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin: 12px 0; }
    tr.anulada td { color: var(--sc-texto-3); text-decoration: line-through; }
    .correccion { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--sc-borde); border-radius: 12px; }
    .devolver td { vertical-align: middle; }
    .mini { width: 90px; text-align: right; }
  `],
})
export class Ventas implements OnInit {
  private readonly ventas = inject(VentaService);
  private readonly caja = inject(CajaService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly medios = MEDIOS_VENTA;
  protected readonly pagina = signal<Pagina<VentaResumen> | null>(null);
  /** Al abrir solo salen las ventas de hoy; para otros días se cambian las fechas o se busca por consecutivo. */
  protected readonly desde = signal(fechaHoy());
  protected readonly hasta = signal(fechaHoy());
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
  /** Las fechas siguen siendo las de hoy por defecto (el usuario no las tocó). */
  protected readonly esHoy = computed(() => this.desde() === fechaHoy() && this.hasta() === fechaHoy());
  /** El botón Hoy se ve seleccionado cuando el filtro es el de hoy y no hay consecutivo escrito. */
  protected readonly hoyActivo = computed(() => this.esHoy() && !this.consecutivo().trim());
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

  /**
   * Fechas que viajan al servidor: con consecutivo y las fechas de hoy sin tocar se busca en todos los días (sin
   * fechas); si el usuario las cambió, se combinan con el consecutivo.
   *
   * @param consecutivo texto del consecutivo ya recortado
   * @returns desde y hasta (o null)
   */
  private fechas(consecutivo: string): { desde: string | null; hasta: string | null } {
    if (consecutivo && this.esHoy()) {
      return { desde: null, hasta: null };
    }
    return { desde: this.desde() || null, hasta: this.hasta() || null };
  }

  /** Vuelve al filtro inicial (hoy, sin consecutivo, todos los estados) y recarga. */
  protected hoy(): void {
    this.desde.set(fechaHoy());
    this.hasta.set(fechaHoy());
    this.consecutivo.set('');
    this.estado.set(null);
    this.buscar();
  }

  /** @param pagina página */
  protected buscar(pagina = 0): void {
    this.sugerencias.set([]);
    const cons = this.consecutivo().trim();
    this.ventas.buscar({ ...this.fechas(cons), estado: this.estado(), consecutivo: cons || null, pagina }).subscribe({ next: p => this.pagina.set(p), error: e => this.avisos.error(e) });
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
      this.ventas.buscar({ ...this.fechas(t), estado: this.estado(), consecutivo: t, tamano: 10 }).subscribe({
        next: p => this.sugerencias.set((p.contenido ?? []).map(v => v.consecutivo ?? '').filter(Boolean)),
        error: () => this.sugerencias.set([]),
      });
    }, 300);
  }

  /** @param v venta del historial */
  protected ver(v: VentaResumen): void {
    // el detalle se abre en una ventana flotante (el foco lo lleva la ventana)
    this.ventas.obtener(v.id!).subscribe({
      next: d => this.abrir(d),
      error: e => this.avisos.error(e),
    });
  }

  /** Cierra la ventana del detalle y cualquier corrección a medias. */
  protected cerrar(): void {
    this.detalle.set(null);
    this.correccion.set(null);
    this.pidiendoPin.set(false);
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
