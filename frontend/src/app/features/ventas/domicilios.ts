import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Domiciliario, Domicilio, EnvioDomicilio, EstadoDomicilio, MedioPagoVenta } from '../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { DomiciliosService } from '../../core/servicios/domicilios.service';
import { iso } from '../../core/servicios/reportes.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { Cifra } from '../../core/ui/cifra';
import { MEDIOS_VENTA } from '../pos/pos';
import { MEDIOS_CAJA } from './cambio-pago';

/** Nombre y tono de cada estado. */
export const ESTADOS_DOMICILIO: Record<EstadoDomicilio, { texto: string; tono: string }> = {
  PENDIENTE: { texto: 'Pendiente', tono: 'ambar' }, ASIGNADO: { texto: 'Asignado', tono: 'azul' },
  EN_CAMINO: { texto: 'En camino', tono: 'azul' }, ENTREGADO: { texto: 'Entregado', tono: 'verde' },
  DEVUELTO: { texto: 'Devuelto', tono: 'rojo' }, CANCELADO: { texto: 'Cancelado', tono: 'gris' },
};

/** Acción abierta sobre un domicilio. */
type Accion = { tipo: 'asignar' | 'devolver' | 'cancelar' | 'cobrar'; domicilio: Domicilio } | null;

/**
 * Tablero de domicilios del día (spec 032): por estado, con tiempos. Quien gestiona asigna el domiciliario (interno o
 * externo), devuelve o cancela con motivo (la venta se anula y el stock vuelve) y fija el precio del envío; el
 * domiciliario ve solo los suyos, con el teléfono y "Abrir en el mapa", y los marca en camino y entregados; quien cobra
 * recibe en su turno el dinero de la contraentrega. Las cifras vienen del backend.
 */
@Component({
  selector: 'sc-domicilios',
  imports: [FormsModule, RouterLink, PesosPipe, FechaPipe, Cifra],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / Domicilios</p>
        <div class="encabezado">
          <div><h1>Domicilios</h1><p>{{ gestiona() ? 'Los domicilios del día por estado: asigna, sigue y cobra la contraentrega.' : 'Tus domicilios del día.' }}</p></div>
          <div class="acciones">
            <input type="date" class="entrada" aria-label="Día" [ngModel]="fecha()" (ngModelChange)="fecha.set($event); cargar()">
            <button type="button" class="boton" (click)="cargar()">Actualizar</button>
          </div>
        </div>
      </div>

      @if (gestiona()) {
        <section class="tarjeta envio">
          <strong>Envío a domicilio</strong>
          <span class="texto-2">{{ envio()?.productoId ? 'Precio actual: ' : 'Sin configurar: el punto de venta no agrega el envío.' }}
            @if (envio()?.precio != null) { <strong>{{ envio()!.precio | pesos }}</strong> }</span>
          <input scCifra class="entrada num" aria-label="Precio del envío" placeholder="Precio" [ngModel]="precioEnvio()" (ngModelChange)="precioEnvio.set($event)">
          <button type="button" class="boton chico" [disabled]="!precioEnvio()" (click)="guardarEnvio()">Guardar precio</button>
        </section>
      }

      <div class="pastillas" role="tablist">
        <button type="button" class="pastilla" [class.activa]="!filtro()" (click)="filtro.set(null)">Todos · {{ lista().length }}</button>
        @for (e of estados; track e) {
          <button type="button" class="pastilla" [class.activa]="filtro() === e" (click)="filtro.set(e)">{{ textos[e].texto }} · {{ cuenta(e) }}</button>
        }
      </div>

      <div class="tarjetas">
        @for (d of visibles(); track d.id) {
          <article class="tarjeta domicilio">
            <div class="fila entre">
              <strong>{{ d.consecutivo }} · {{ d.venta }}</strong>
              <span class="chip" [class]="'chip ' + textos[d.estado!].tono">{{ textos[d.estado!].texto }}</span>
            </div>
            <div>{{ d.cliente }} · <strong>{{ d.total | pesos }}</strong>
              @if (d.pago === 'CONTRAENTREGA') { <span class="chip ambar">Contraentrega{{ d.cobradoEn ? ' · cobrado' : '' }}</span> }</div>
            <div class="direccion">{{ d.direccion }}@if (d.referencia) { <span class="sub">{{ d.referencia }}</span> }</div>
            <div class="fila">
              <a class="boton chico" [href]="'tel:' + d.telefono">Llamar {{ d.telefono }}</a>
              <a class="boton chico" target="_blank" rel="noopener" [href]="mapa(d)">Abrir en el mapa</a>
            </div>
            <div class="sub">Vendido {{ d.creadoEn | fecha: 'hora' }} · hace {{ minutos(d) }} min
              @if (d.domiciliario) { · {{ d.domiciliario }}@if (d.domiciliarioTelefono) { ({{ d.domiciliarioTelefono }}) } }
              @if (d.motivo) { · {{ d.motivo }} }</div>
            <div class="fila">
              @if (gestiona() && (d.estado === 'PENDIENTE' || d.estado === 'ASIGNADO')) {
                <button type="button" class="boton chico" (click)="abrir('asignar', d)">{{ d.estado === 'PENDIENTE' ? 'Asignar' : 'Reasignar' }}</button>
              }
              @if (d.estado === 'ASIGNADO' && puedeMover(d)) {
                <button type="button" class="boton chico primario" (click)="cambiar(d, 'EN_CAMINO', null)">Salió (en camino)</button>
              }
              @if (d.estado === 'EN_CAMINO' && puedeMover(d)) {
                <button type="button" class="boton chico primario" (click)="cambiar(d, 'ENTREGADO', null)">Entregado</button>
              }
              @if (d.estado === 'EN_CAMINO' && gestiona()) {
                <button type="button" class="boton chico peligro" (click)="abrir('devolver', d)">Devuelto</button>
              }
              @if (gestiona() && (d.estado === 'PENDIENTE' || d.estado === 'ASIGNADO')) {
                <button type="button" class="boton chico peligro" (click)="abrir('cancelar', d)">Cancelar</button>
              }
              @if (d.porCobrar && cobra()) {
                <button type="button" class="boton chico primario" (click)="abrir('cobrar', d)">Cobrar {{ d.total | pesos }}</button>
              }
            </div>

            @if (accion()?.domicilio?.id === d.id) {
              <div class="accion">
                @switch (accion()!.tipo) {
                  @case ('asignar') {
                    <select class="entrada" aria-label="Domiciliario" [ngModel]="domiciliarioId()" (ngModelChange)="domiciliarioId.set($event)">
                      <option [ngValue]="null">Externo (escribe nombre y teléfono)</option>
                      @for (u of domiciliarios(); track u.id) { <option [ngValue]="u.id">{{ u.nombre }}</option> }
                    </select>
                    @if (domiciliarioId() == null) {
                      <input class="entrada" maxlength="150" aria-label="Nombre del domiciliario externo" placeholder="Nombre" [ngModel]="externoNombre()" (ngModelChange)="externoNombre.set($event)">
                      <input class="entrada" maxlength="30" aria-label="Teléfono del domiciliario externo" placeholder="Teléfono" [ngModel]="externoTelefono()" (ngModelChange)="externoTelefono.set($event)">
                    }
                    <div class="fila"><button type="button" class="boton chico primario" (click)="asignar(d)">Asignar</button>
                      <button type="button" class="boton chico" (click)="accion.set(null)">Cerrar</button></div>
                  }
                  @case ('cobrar') {
                    <span class="sub">El dinero entra a tu turno. Debe sumar {{ d.total | pesos }}.</span>
                    <div class="fila">
                      <select class="entrada" aria-label="Medio del cobro" [ngModel]="medioCobro()" (ngModelChange)="medioCobro.set($event)">
                        @for (m of mediosCaja; track m) { <option [ngValue]="m">{{ textoMedio(m) }}</option> }
                      </select>
                      <input class="entrada" maxlength="40" aria-label="Referencia del cobro" placeholder="Referencia (opcional)" [ngModel]="referenciaCobro()" (ngModelChange)="referenciaCobro.set($event)">
                    </div>
                    <div class="fila"><button type="button" class="boton chico primario" (click)="cobrar(d)">Recibir {{ d.total | pesos }}</button>
                      <button type="button" class="boton chico" (click)="accion.set(null)">Cerrar</button></div>
                  }
                  @default {
                    <input class="entrada" maxlength="300" [attr.aria-label]="'Motivo para ' + (accion()!.tipo === 'devolver' ? 'devolver' : 'cancelar')"
                      placeholder="Motivo (no estaba el cliente, ya no lo quiere…)" [ngModel]="motivo()" (ngModelChange)="motivo.set($event)">
                    <span class="sub">La venta se anula y el stock vuelve{{ d.pago === 'PAGADO' ? '; el dinero sale de tu turno abierto' : '' }}.</span>
                    <div class="fila"><button type="button" class="boton chico peligro" [disabled]="motivo().trim().length < 5"
                      (click)="cambiar(d, accion()!.tipo === 'devolver' ? 'DEVUELTO' : 'CANCELADO', motivo())">{{ accion()!.tipo === 'devolver' ? 'Marcar devuelto' : 'Cancelar domicilio' }}</button>
                      <button type="button" class="boton chico" (click)="accion.set(null)">Cerrar</button></div>
                  }
                }
              </div>
            }
          </article>
        } @empty {
          <p class="vacio">{{ cargando() ? 'Cargando…' : 'No hay domicilios con este filtro.' }}</p>
        }
      </div>
    </div>
  `,
  styles: `
    .envio { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; .entrada { max-width: 140px; } }
    .tarjetas { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
    .domicilio, .accion { display: grid; gap: 8px; }
    .accion { border-top: 1px solid var(--sc-borde); padding-top: 8px; }
    .direccion { font-weight: 600; }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); font-weight: 400; }
    @media (max-width: 400px) { .tarjetas { grid-template-columns: 1fr; } }
  `,
})
export class Domicilios implements OnInit {
  private readonly servicio = inject(DomiciliosService);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(Avisos);

  protected readonly estados = Object.keys(ESTADOS_DOMICILIO) as EstadoDomicilio[];
  protected readonly textos = ESTADOS_DOMICILIO;
  protected readonly mediosCaja = MEDIOS_CAJA;
  protected readonly fecha = signal(iso(new Date()));
  protected readonly lista = signal<Domicilio[]>([]);
  protected readonly filtro = signal<EstadoDomicilio | null>(null);
  protected readonly cargando = signal(false);
  protected readonly accion = signal<Accion>(null);
  protected readonly domiciliarios = signal<Domiciliario[]>([]);
  protected readonly domiciliarioId = signal<number | null>(null);
  protected readonly externoNombre = signal('');
  protected readonly externoTelefono = signal('');
  protected readonly motivo = signal('');
  protected readonly medioCobro = signal<MedioPagoVenta>('EFECTIVO');
  protected readonly referenciaCobro = signal('');
  protected readonly envio = signal<EnvioDomicilio | null>(null);
  protected readonly precioEnvio = signal<number | null>(null);

  protected readonly gestiona = computed(() => this.sesion.tienePermiso('DOMICILIO_GESTIONAR') && !this.sesion.soloLectura());
  protected readonly cobra = computed(() => this.sesion.tienePermiso('VENTA_COBRAR') && !this.sesion.soloLectura());
  protected readonly visibles = computed(() => this.lista().filter(d => !this.filtro() || d.estado === this.filtro()));

  ngOnInit(): void {
    this.cargar();
    if (this.gestiona()) {
      this.servicio.envio().subscribe({ next: e => this.envio.set(e), error: () => undefined });
    }
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.servicio.listar(this.fecha() || null, null).subscribe({
      next: l => {
        this.cargando.set(false);
        this.lista.set(l);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param e estado @returns cuántos hay */
  protected cuenta(e: EstadoDomicilio): number {
    return this.lista().filter(d => d.estado === e).length;
  }

  /** @param d domicilio @returns minutos desde que se vendió */
  protected minutos(d: Domicilio): number {
    return Math.max(0, Math.round((Date.now() - new Date(d.creadoEn!).getTime()) / 60000));
  }

  /** @param d domicilio @returns enlace de Google Maps con la dirección */
  protected mapa(d: Domicilio): string {
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(d.direccion ?? '')}`;
  }

  /** @param d domicilio @returns si la sesión lo marca en camino o entregado (quien gestiona o el domiciliario asignado) */
  protected puedeMover(d: Domicilio): boolean {
    return this.gestiona() || (this.sesion.tienePermiso('DOMICILIO_REPARTIR') && d.domiciliarioUsuarioId === this.sesion.usuario()?.id);
  }

  /** @param m medio @returns texto */
  protected textoMedio(m: MedioPagoVenta): string {
    return MEDIOS_VENTA[m] ?? m;
  }

  /** @param tipo acción; @param d domicilio */
  protected abrir(tipo: NonNullable<Accion>['tipo'], d: Domicilio): void {
    this.accion.set({ tipo, domicilio: d });
    this.motivo.set('');
    this.referenciaCobro.set('');
    this.medioCobro.set('EFECTIVO');
    this.domiciliarioId.set(d.domiciliarioUsuarioId ?? null);
    this.externoNombre.set(d.domiciliarioUsuarioId ? '' : d.domiciliario ?? '');
    this.externoTelefono.set(d.domiciliarioTelefono ?? '');
    if (tipo === 'asignar' && !this.domiciliarios().length) {
      this.servicio.domiciliarios().subscribe({ next: l => this.domiciliarios.set(l), error: () => undefined });
    }
  }

  /** @param d domicilio que se asigna */
  protected asignar(d: Domicilio): void {
    const id = this.domiciliarioId();
    if (id == null && !this.externoNombre().trim()) {
      this.avisos.ok('Elige un domiciliario o escribe el nombre del externo.');
      return;
    }
    this.servicio.asignar(d.id!, id != null ? { domiciliarioUsuarioId: id }
      : { nombre: this.externoNombre().trim(), telefono: this.externoTelefono().trim() || undefined })
      .subscribe({ next: r => this.listo(r, `Domicilio ${r.consecutivo} asignado.`), error: e => this.avisos.error(e) });
  }

  /** @param d domicilio; @param estado estado nuevo; @param nota motivo */
  protected cambiar(d: Domicilio, estado: EstadoDomicilio, nota: string | null): void {
    this.servicio.cambiarEstado(d.id!, estado, nota?.trim() || null)
      .subscribe({ next: r => this.listo(r, `Domicilio ${r.consecutivo}: ${ESTADOS_DOMICILIO[r.estado!].texto.toLowerCase()}.`), error: e => this.avisos.error(e) });
  }

  /** @param d domicilio contraentrega que se cobra en la caja de la sesión */
  protected cobrar(d: Domicilio): void {
    this.servicio.cobrar(d.id!, [{ medio: this.medioCobro(), monto: d.total!, referencia: this.referenciaCobro().trim() || undefined }])
      .subscribe({ next: r => this.listo(r, `Domicilio ${r.consecutivo} cobrado en tu turno.`), error: e => this.avisos.error(e) });
  }

  protected guardarEnvio(): void {
    const precio = this.precioEnvio();
    if (!precio) {
      return;
    }
    this.servicio.configurarEnvio(precio).subscribe({
      next: e => {
        this.envio.set(e);
        this.precioEnvio.set(null);
        this.avisos.ok('Precio del envío guardado.');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param r domicilio actualizado; @param texto aviso */
  private listo(r: Domicilio, texto: string): void {
    this.accion.set(null);
    this.lista.update(l => l.map(x => (x.id === r.id ? { ...x, ...r } : x)));
    this.avisos.ok(texto);
  }
}
