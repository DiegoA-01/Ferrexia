import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { Caja, MedioPago, Turno } from '../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { CajaService } from '../../core/servicios/caja.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { Cifra } from '../../core/ui/cifra';
import { MEDIOS } from './caja';
import { AbonoTurno } from './turno/abono-turno';
import { CierreTurno } from './turno/cierre-turno';
import { FacturasTurno } from './turno/facturas-turno';
import { MovimientoCaja, TipoMovimiento } from './turno/movimiento-caja';
import { MovimientosTurno } from './turno/movimientos-turno';

/** Lo que puede hacer el cajero con su turno abierto (spec 018). */
export type AccionTurno = 'facturas' | 'retiro' | 'gasto' | 'ingreso' | 'abono' | 'cerrar';

/** Texto de cada acción en el selector. */
const ACCIONES: Record<AccionTurno, string> = {
  facturas: 'Ver facturas del turno',
  retiro: 'Registrar retiro a la bóveda',
  gasto: 'Registrar gasto menor',
  ingreso: 'Registrar ingreso de efectivo',
  abono: 'Recibir abono de cliente',
  cerrar: 'Cerrar turno',
};

/** Tipo de movimiento de caja de cada acción que lo es. */
const MOVIMIENTO: Partial<Record<AccionTurno, TipoMovimiento>> = { retiro: 'RETIRO', gasto: 'GASTO', ingreso: 'INGRESO' };

/** Estado de la pantalla. */
type Vista = 'CARGANDO' | 'SIN_TURNO' | 'ABIERTO' | 'RECIEN_CERRADO';

/**
 * Mi turno (specs 005, 018 y 021). Sin turno, solo se abre uno. Con el turno abierto se ve su resumen, los botones "Ir a
 * vender" y "Cerrar turno", y un selector con lo que se puede hacer: elegir una acción muestra solo su formulario (y
 * queda en la URL, `?accion=gasto`, para que recargar no la pierda). Entre las acciones están las facturas del turno y el
 * gasto o ingreso con su categoría (spec 021). Al cerrar se ve lo esperado, lo contado y la diferencia (el cierre sigue
 * ciego, decisión D20) y, si se quiere, "Abrir otro turno".
 */
@Component({
  selector: 'sc-mi-turno',
  imports: [Cifra, FormsModule, RouterLink, PesosPipe, FechaPipe, MovimientoCaja, AbonoTurno, CierreTurno, FacturasTurno, MovimientosTurno],
  template: `
    <div class="pagina">
      <div class="encabezado"><div><h1>Mi turno</h1><p>Abra su caja, registre el efectivo que sale o entra y ciérrela contando a ciegas.</p></div></div>

      @switch (vista()) {
        @case ('CARGANDO') {
          <p class="cargando">Buscando su turno…</p>
        }
        @case ('RECIEN_CERRADO') {
          @if (cerrado(); as c) {
            <section class="tarjeta">
              <h2>Turno cerrado</h2>
              <p class="sin-margen">{{ c.caja }} · cerrado {{ c.cerradoEn | fecha: 'hora' }}</p>
              <div class="tabla-contenedor"><table class="tabla">
                <thead><tr><th>Medio</th><th class="num">Esperado</th><th class="num">Contado</th><th class="num">Diferencia</th></tr></thead>
                <tbody>@for (m of medios; track m) {
                  <tr><td>{{ textoMedio(m) }}</td><td class="num">{{ valor(c.esperado, m) | pesos }}</td><td class="num">{{ valor(c.contado, m) | pesos }}</td>
                    <td class="num" [class.rojo]="valor(c.diferencias, m) !== 0">{{ valor(c.diferencias, m) | pesos }}</td></tr>
                }</tbody>
              </table></div>
              @if (c.requiereAprobacion) {
                <div class="aviso ambar" role="status"><span class="material-symbols-outlined">gavel</span> La diferencia pasa la tolerancia: un supervisor debe aprobarla.</div>
              } @else { <div class="aviso verde" role="status"><span class="material-symbols-outlined">check_circle</span> El turno cuadró dentro de la tolerancia.</div> }
              <button type="button" class="boton" (click)="nuevo()">Abrir otro turno</button>
            </section>
          }
        }
        @case ('ABIERTO') {
          @if (turno(); as t) {
            <section class="tarjeta turno-abierto">
              <div class="resumen">
                <div><span class="sub">Caja</span><strong>{{ t.caja }}</strong></div>
                <div><span class="sub">Abierto</span><strong>{{ t.abiertoEn | fecha: 'hora' }}</strong></div>
                <div><span class="sub">Base</span><strong>{{ t.baseApertura | pesos }}</strong></div>
              </div>
              <div class="acciones-turno">
                <a class="boton primario" routerLink="/app/pos"><span class="material-symbols-outlined">point_of_sale</span> Ir a vender</a>
                <button type="button" class="boton peligro" (click)="elegir('cerrar')"><span class="material-symbols-outlined">lock</span> Cerrar turno</button>
              </div>
            </section>
            @if (t.debeRetirar) {
              <div class="aviso ambar" role="alert"><span class="material-symbols-outlined">savings</span> La caja pasó su tope de efectivo: haga un retiro a la bóveda.
                <button type="button" class="boton chico" (click)="elegir('retiro')">Hacer retiro</button></div>
            }

            <section class="tarjeta selector">
              <label for="mt-accion">¿Qué quiere hacer?</label>
              <select id="mt-accion" class="entrada" [ngModel]="accion()" (ngModelChange)="elegir($event)">
                <option [ngValue]="null">Elija una acción</option>
                @for (a of acciones(); track a) { <option [ngValue]="a">{{ textoAccion(a) }}</option> }
              </select>
            </section>

            @switch (accion()) {
              @case ('facturas') { <sc-facturas-turno [turnoId]="t.id!" /> }
              @case ('abono') { <sc-abono-turno (registrado)="refrescar()" /> }
              @case ('cerrar') { <sc-cierre-turno [turnoId]="t.id!" (cerrado)="alCerrar($event)" /> }
              @case (null) { }
              @default {
                @if (movimientoDe(accion()); as tipo) {
                  <sc-movimiento-caja [turnoId]="t.id!" [tipo]="tipo" (actualizado)="turno.set($event)" />
                }
              }
            }

            @if (movimientos().length) { <sc-movimientos-turno [movimientos]="movimientos()" /> }
          }
        }
        @case ('SIN_TURNO') {
          <section class="tarjeta">
            <h2>Abrir turno</h2>
            @if (cajasCargadas() && !cajasLibres().length) {
              <div class="sin-cajas" role="alert">
                <strong>Todas las cajas tienen un turno abierto.</strong>
                <p>Pide a quien la está usando que cierre su turno, o pide al administrador que cree otra caja en
                  Configuración, Cajas.</p>
                <ul>
                  @for (c of cajas(); track c.id) {
                    <li>{{ c.nombre }}: ocupada por {{ c.turnoAbiertoPor ?? 'otra persona' }} desde {{ c.turnoAbiertoDesde | fecha: 'hora' }}</li>
                  }
                </ul>
              </div>
            } @else {
              <div class="formulario">
                <div class="campo"><label for="ab-caja">Caja</label>
                  <select id="ab-caja" class="entrada" [ngModel]="cajaId()" (ngModelChange)="cajaId.set($event)">
                    <option [ngValue]="null">Elija la caja</option>
                    @for (c of cajas(); track c.id) {
                      <option [ngValue]="c.id" [disabled]="c.turnoAbierto">{{ textoCaja(c) }}</option>
                    }
                  </select></div>
                <div class="campo"><label for="ab-base">Base en efectivo</label>
                  <input scCifra id="ab-base" min="0" class="entrada" [ngModel]="base()" (ngModelChange)="base.set($event)"></div>
              </div>
              <button type="button" class="boton primario" [disabled]="!cajaId() || base() == null || enviando()" (click)="abrir()">Abrir turno</button>
            }
          </section>
        }
      }
    </div>
  `,
  styles: `
    h2 { margin: 0 0 8px; }
    .sin-cajas { padding: 14px 16px; border-radius: 12px; background: #fff7ed; border: 1px solid #fed7aa; color: #7c2d12;
      p { margin: 6px 0; } ul { margin: 6px 0 0; padding-left: 18px; } }
    .turno-abierto { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; }
    .resumen { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 12px; flex: 1 1 320px; }
    .acciones-turno { display: flex; flex-wrap: wrap; gap: 8px; }
    .selector { display: grid; gap: 6px; max-width: 420px; label { font-weight: 600; } }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .formulario { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 12px; }
    .lista { list-style: none; padding: 0; margin: 0; display: grid; gap: 4px; li { display: flex; justify-content: space-between; gap: 12px; } }
    td.rojo { color: #b3261e; font-weight: 600; }
  `,
})
export class MiTurno implements OnInit {
  private readonly caja = inject(CajaService);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(Avisos);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);
  private readonly fecha = new FechaPipe();

  protected readonly medios = Object.keys(MEDIOS) as MedioPago[];
  protected readonly cargado = signal(false);
  protected readonly turno = signal<Turno | null>(null);
  protected readonly cerrado = signal<Turno | null>(null);
  protected readonly cajas = signal<Caja[]>([]);
  protected readonly cajasCargadas = signal(false);
  /** Cajas sin turno abierto: las únicas que se pueden elegir (spec 015). */
  protected readonly cajasLibres = computed(() => this.cajas().filter(c => !c.turnoAbierto));
  protected readonly cajaId = signal<number | null>(null);
  protected readonly base = signal<number | null>(null);
  protected readonly enviando = signal(false);

  /** Acción elegida en el selector (spec 018); {@code null} = ninguna. */
  protected readonly accion = signal<AccionTurno | null>(null);
  /** Abonos de cartera (spec 007): solo con el módulo de cartera. */
  protected readonly conCartera = computed(() => this.sesion.tieneModulo('CARTERA'));
  /** Acciones que ofrece el selector. */
  protected readonly acciones = computed(() =>
    (Object.keys(ACCIONES) as AccionTurno[]).filter(a => a !== 'abono' || this.conCartera()));

  protected readonly vista = computed<Vista>(() => {
    if (!this.cargado()) {
      return 'CARGANDO';
    }
    if (this.cerrado()) {
      return 'RECIEN_CERRADO';
    }
    return this.turno() ? 'ABIERTO' : 'SIN_TURNO';
  });

  protected readonly movimientos = computed(() => (this.turno()?.movimientos ?? []).filter(m => m.tipo !== 'BASE'));

  ngOnInit(): void {
    const pedida = this.ruta.snapshot.queryParamMap.get('accion') as AccionTurno | null;
    if (pedida && pedida in ACCIONES) {
      this.accion.set(pedida);
    }
    this.caja.actual().subscribe({
      next: t => {
        this.turno.set(t);
        this.cargado.set(true);
      },
      error: (e: HttpErrorResponse) => {
        this.cargado.set(true);
        this.accion.set(null);
        if (e.status !== 404) {
          this.avisos.error(e);
        }
        this.cargarCajas();
      },
    });
  }

  /**
   * Elige la acción del turno y la deja en la URL.
   *
   * @param a acción ({@code null} = ninguna)
   */
  protected elegir(a: AccionTurno | null): void {
    const valida = a && this.acciones().includes(a) ? a : null;
    this.accion.set(valida);
    this.router.navigate([], { relativeTo: this.ruta, queryParams: { accion: valida }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  /** @param a acción @returns tipo de movimiento de caja, si la acción es uno */
  protected movimientoDe(a: AccionTurno | null): TipoMovimiento | null {
    return a ? MOVIMIENTO[a] ?? null : null;
  }

  protected abrir(): void {
    this.enviando.set(true);
    this.caja.abrir(this.cajaId()!, this.base()!).subscribe({
      next: t => {
        this.enviando.set(false);
        this.turno.set(t);
        this.avisos.ok('Turno abierto.');
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Vuelve a leer el turno (después de un abono). */
  protected refrescar(): void {
    this.caja.actual().subscribe({ next: t => this.turno.set(t), error: () => undefined });
  }

  /** @param t turno cerrado con lo esperado y las diferencias */
  protected alCerrar(t: Turno): void {
    this.turno.set(null);
    this.cerrado.set(t);
    this.elegir(null);
  }

  protected nuevo(): void {
    this.cerrado.set(null);
    this.cargarCajas();
  }

  /** @param a acción @returns texto */
  protected textoAccion(a: AccionTurno): string {
    return ACCIONES[a];
  }

  /** @param m medio @returns texto */
  protected textoMedio(m: MedioPago): string {
    return MEDIOS[m];
  }

  /** @param mapa valores por medio; @param m medio @returns valor (0 si no hay) */
  protected valor(mapa: Record<string, number> | undefined, m: MedioPago): number {
    return mapa?.[m] ?? 0;
  }

  /**
   * @param c caja
   * @returns nombre y sede; si está ocupada, quién la tiene y desde cuándo (spec 015)
   */
  protected textoCaja(c: Caja): string {
    const base = `${c.nombre} · ${c.sede}`;
    return c.turnoAbierto
      ? `${base} · ocupada por ${c.turnoAbiertoPor ?? 'otra persona'} desde ${this.fecha.transform(c.turnoAbiertoDesde, 'hora')}`
      : base;
  }

  private cargarCajas(): void {
    // spec 025: solo las cajas asignadas a la persona (o todas, si no tiene ninguna)
    this.caja.misCajas().subscribe({
      next: c => {
        this.cajas.set(c);
        this.cajasCargadas.set(true);
        const libres = c.filter(x => !x.turnoAbierto);
        if (libres.length === 1 && this.cajaId() == null) {
          this.cajaId.set(libres[0].id ?? null);
        }
      },
      error: e => this.avisos.error(e),
    });
  }
}
