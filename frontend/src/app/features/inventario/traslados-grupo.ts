import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Bodega, DestinoTraslado, Pagina, TrasladoDetalle, TrasladoResumen } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { mensajeError } from '../../core/http/api';
import { InventarioService } from '../../core/servicios/inventario.service';
import { TrasladosGrupoService } from '../../core/servicios/traslados-grupo.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { BuscadorProducto } from '../../core/ui/buscador-producto';
import { Cifra } from '../../core/ui/cifra';

/** Línea del envío que se está armando: el producto es de la empresa que envía. */
interface LineaEnvio {
  productoId: number;
  codigo: string;
  nombre: string;
  cantidad: number;
}

/**
 * Inventario › Traslados entre empresas (spec 035): lo enviado y lo recibido por la empresa, "Nuevo traslado" con los
 * productos que salen (la empresa destino los empareja por código), "Recibir" con la cantidad que llegó de cada línea y
 * "Anular" mientras esté en tránsito. El stock y el costo los mueve el backend; aquí nunca se calculan.
 */
@Component({
  selector: 'sc-traslados-grupo',
  imports: [Cifra, FormsModule, RouterLink, Estado, FechaPipe, NumeroPipe, PesosPipe],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / <a routerLink="/app/inventario">Inventario</a> / Traslados entre empresas</p>
        <div class="encabezado">
          <div><h1>Traslados entre empresas</h1>
            <p>Mercancía que sale hacia otra empresa de tu grupo y la que te llega. El stock baja al enviar y sube cuando la empresa destino la recibe.</p></div>
          <div class="acciones">
            @if (puedeEnviar()) {
              <button type="button" class="boton primario" (click)="abrirNuevo()"><span class="material-symbols-outlined">add</span> Nuevo traslado</button>
            }
          </div>
        </div>
      </div>

      @if (nuevo()) {
        <section class="tarjeta">
          <h2>Nuevo traslado</h2>
          <form class="formulario dos" (ngSubmit)="enviar()">
            <div class="campo"><label for="destino">Empresa destino</label>
              <select id="destino" name="destino" class="entrada" [ngModel]="destinoId()" (ngModelChange)="destinoId.set($event)">
                <option [ngValue]="null" disabled>Elige</option>
                @for (d of destinos(); track d.id) { <option [ngValue]="d.id">{{ d.nombre }}</option> }
              </select></div>
            <div class="campo"><label for="borigen">Bodega de origen</label>
              <select id="borigen" name="borigen" class="entrada" [ngModel]="bodegaOrigen()" (ngModelChange)="bodegaOrigen.set($event)">
                <option [ngValue]="null" disabled>Elige</option>
                @for (b of bodegas(); track b.id) { <option [ngValue]="b.id">{{ b.nombre }}</option> }
              </select></div>
            <div class="campo ancho"><label for="nota">Nota (opcional)</label>
              <input id="nota" name="nota" class="entrada" maxlength="300" [ngModel]="nota()" (ngModelChange)="nota.set($event)"></div>

            <div class="campo"><label for="prod">Producto</label>
              <input id="prod" name="prod" class="entrada" list="productos-traslado" autocomplete="off" placeholder="Escribe código o nombre"
                     [ngModel]="buscador.texto()" (ngModelChange)="buscador.escribir($event)">
              <datalist id="productos-traslado">
                @for (p of buscador.opciones(); track p.id) { <option [value]="buscador.etiqueta(p)"></option> }
              </datalist></div>
            <div class="campo"><label for="cant">Cantidad</label>
              <input scCifra [decimales]="4" id="cant" name="cant" class="entrada num" [ngModel]="cantidad()" (ngModelChange)="cantidad.set($event)"></div>
            <div class="fila ancho"><button type="button" class="boton" (click)="agregarLinea()">Agregar a la lista</button></div>

            @if (lineas().length) {
              <div class="tabla-contenedor ancho">
                <table class="tabla">
                  <thead><tr><th>Producto</th><th class="num">Cantidad</th><th></th></tr></thead>
                  <tbody>
                    @for (l of lineas(); track l.productoId) {
                      <tr><td><strong>{{ l.codigo }}</strong> <span class="texto-2">{{ l.nombre }}</span></td>
                        <td class="num">{{ l.cantidad | numero }}</td>
                        <td><button type="button" class="boton chico" (click)="quitarLinea(l.productoId)">Quitar</button></td></tr>
                    }
                  </tbody>
                </table>
              </div>
            }

            @if (errorEnvio()) { <div class="aviso ambar ancho" role="alert"><span class="material-symbols-outlined">warning</span><div>{{ errorEnvio() }}</div></div> }
            <div class="fila ancho">
              <button type="submit" class="boton primario" [disabled]="trabajando()">{{ trabajando() ? 'Enviando…' : 'Enviar traslado' }}</button>
              <button type="button" class="boton" (click)="nuevo.set(false)">Cancelar</button>
            </div>
          </form>
        </section>
      }

      @if (detalle(); as d) {
        <section class="tarjeta">
          <div class="fila titulo">
            <h2>{{ d.resumen?.consecutivo }}</h2>
            <sc-estado [valor]="d.resumen?.estado" />
            <span class="espacio"></span>
            <button type="button" class="boton chico" (click)="cerrarDetalle()">Cerrar</button>
          </div>
          <p class="texto-2">{{ d.resumen?.empresaOrigenNombre }} → {{ d.resumen?.empresaDestinoNombre }}
            · enviado {{ d.resumen?.enviadoEn | fecha: 'hora' }}@if (d.resumen?.recibidoEn) { · recibido {{ d.resumen?.recibidoEn | fecha: 'hora' }} }
            @if (d.resumen?.anuladoEn) { · anulado {{ d.resumen?.anuladoEn | fecha: 'hora' }} }</p>
          @if (d.resumen?.nota) { <p>Nota: {{ d.resumen?.nota }}</p> }
          @if (d.notaRecepcion) { <p>Nota de la recepción: {{ d.notaRecepcion }}</p> }
          @if (d.motivoAnulacion) { <p>Motivo de la anulación: {{ d.motivoAnulacion }}</p> }

          <div class="tabla-contenedor">
            <table class="tabla">
              <thead><tr><th>Producto</th><th class="num">Enviado</th><th class="num">Recibido</th><th class="num">Faltante</th><th class="num">Costo unitario</th></tr></thead>
              <tbody>
                @for (l of d.lineas ?? []; track l.id) {
                  <tr><td><strong>{{ l.codigo }}</strong> <span class="texto-2">{{ l.nombre }}</span></td>
                    <td class="num">{{ l.cantidad | numero }}</td>
                    <td class="num">@if (puedeRecibirEste()) {
                        <input scCifra [decimales]="4" class="entrada num corta" [attr.aria-label]="'Recibido de ' + l.codigo"
                               [ngModel]="recibidas()[l.id!]" (ngModelChange)="fijarRecibida(l.id!, $event)" [name]="'rec' + l.id">
                      } @else { {{ l.cantidadRecibida == null ? '—' : (l.cantidadRecibida | numero) }} }</td>
                    <td class="num">{{ l.faltante == null ? '—' : (l.faltante | numero) }}</td>
                    <td class="num">{{ l.costoUnitario | pesos }}</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (puedeRecibirEste()) {
            <div class="formulario dos margen">
              <div class="campo"><label for="bdest">Bodega a la que entra</label>
                <select id="bdest" class="entrada" [ngModel]="bodegaDestino()" (ngModelChange)="bodegaDestino.set($event)">
                  <option [ngValue]="null" disabled>Elige</option>
                  @for (b of bodegas(); track b.id) { <option [ngValue]="b.id">{{ b.nombre }}</option> }
                </select></div>
              <div class="campo"><label for="notarec">Nota (opcional)</label>
                <input id="notarec" class="entrada" maxlength="300" [ngModel]="notaRecepcion()" (ngModelChange)="notaRecepcion.set($event)"></div>
              <div class="fila ancho"><button type="button" class="boton primario" [disabled]="trabajando()" (click)="recibir()">Recibir</button>
                <span class="texto-chico texto-2">Lo que no llegue queda como faltante del traslado.</span></div>
            </div>
          }
          @if (puedeAnularEste()) {
            <div class="formulario dos margen">
              <div class="campo ancho"><label for="motivo">Motivo de la anulación</label>
                <input id="motivo" class="entrada" maxlength="300" [ngModel]="motivo()" (ngModelChange)="motivo.set($event)"></div>
              <div class="fila ancho"><button type="button" class="boton peligro" [disabled]="trabajando()" (click)="anular()">Anular traslado</button>
                <span class="texto-chico texto-2">El stock vuelve a tu bodega.</span></div>
            </div>
          }
          @if (errorAccion()) { <div class="aviso rojo margen" role="alert">{{ errorAccion() }}</div> }
        </section>
      }

      <section class="tarjeta">
        <h2>Enviados y recibidos</h2>
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Traslado</th><th>Tipo</th><th>Con</th><th>Estado</th><th class="num">Líneas</th><th>Enviado</th><th></th></tr></thead>
            <tbody>
              @for (t of pagina()?.contenido ?? []; track t.id) {
                <tr>
                  <td><strong>{{ t.consecutivo }}</strong></td>
                  <td>{{ t.sentido === 'ENVIADO' ? 'Enviado' : 'Recibido' }}</td>
                  <td>{{ t.sentido === 'ENVIADO' ? t.empresaDestinoNombre : t.empresaOrigenNombre }}</td>
                  <td><sc-estado [valor]="t.estado" /></td>
                  <td class="num">{{ t.lineas | numero }}</td>
                  <td class="nowrap">{{ t.enviadoEn | fecha: 'hora' }}</td>
                  <td><button type="button" class="boton chico" (click)="abrir(t)">Ver</button></td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="vacio">{{ pagina() ? 'Todavía no hay traslados.' : 'Cargando…' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
        @if (pagina(); as p) {
          @if ((p.totalPaginas ?? 0) > 1) {
            <div class="fila paginacion">
              <button type="button" class="boton chico" [disabled]="numeroPagina() === 0" (click)="cargar(numeroPagina() - 1)">Anterior</button>
              <span class="texto-chico texto-2">Página {{ numeroPagina() + 1 }} de {{ p.totalPaginas }}</span>
              <button type="button" class="boton chico" [disabled]="numeroPagina() + 1 >= (p.totalPaginas ?? 0)" (click)="cargar(numeroPagina() + 1)">Siguiente</button>
            </div>
          }
        }
      </section>
    </div>
  `,
  styles: `
    .campo.ancho, .ancho { grid-column: 1 / -1; }
    .espacio { flex: 1; }
    .titulo h2 { margin: 0; }
    .margen { margin-top: 14px; }
    .corta { width: 110px; }
    .nowrap { white-space: nowrap; }
    .paginacion { justify-content: center; margin-top: 14px; }
  `,
})
export class TrasladosGrupo implements OnInit {
  private readonly servicio = inject(TrasladosGrupoService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly pagina = signal<Pagina<TrasladoResumen> | null>(null);
  protected readonly numeroPagina = signal(0);
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly destinos = signal<DestinoTraslado[]>([]);
  protected readonly trabajando = signal(false);

  // Nuevo traslado
  protected readonly nuevo = signal(false);
  protected readonly buscador = new BuscadorProducto(this.inventario);
  protected readonly destinoId = signal<number | null>(null);
  protected readonly bodegaOrigen = signal<number | null>(null);
  protected readonly nota = signal('');
  protected readonly cantidad = signal<number | null>(null);
  protected readonly lineas = signal<LineaEnvio[]>([]);
  /** Error del envío (p. ej. {@code PRODUCTOS_SIN_PAREJA} con la lista de códigos), visible hasta el siguiente intento. */
  protected readonly errorEnvio = signal<string | null>(null);

  // Detalle, recepción y anulación
  protected readonly detalle = signal<TrasladoDetalle | null>(null);
  protected readonly recibidas = signal<Record<number, number | null>>({});
  protected readonly bodegaDestino = signal<number | null>(null);
  protected readonly notaRecepcion = signal('');
  protected readonly motivo = signal('');
  protected readonly errorAccion = signal<string | null>(null);

  protected readonly puedeEnviar = computed(() => this.sesion.tienePermiso('TRASLADO_GRUPO_ENVIAR') && !this.sesion.soloLectura());
  protected readonly puedeRecibir = computed(() => this.sesion.tienePermiso('TRASLADO_GRUPO_RECIBIR') && !this.sesion.soloLectura());
  /** Solo la empresa destino recibe, y solo mientras el traslado está en tránsito. */
  protected readonly puedeRecibirEste = computed(() => {
    const r = this.detalle()?.resumen;
    return this.puedeRecibir() && r?.sentido === 'RECIBIDO' && r.estado === 'EN_TRANSITO';
  });
  /** Solo la empresa origen anula, y solo mientras el traslado está en tránsito. */
  protected readonly puedeAnularEste = computed(() => {
    const r = this.detalle()?.resumen;
    return this.puedeEnviar() && r?.sentido === 'ENVIADO' && r.estado === 'EN_TRANSITO';
  });

  ngOnInit(): void {
    this.cargar(0);
    this.inventario.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
  }

  /** @param p número de página desde 0 */
  protected cargar(p: number): void {
    this.numeroPagina.set(p);
    this.servicio.listar(p).subscribe({
      next: r => this.pagina.set(r),
      error: e => {
        this.pagina.set({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
        this.avisos.error(e);
      },
    });
  }

  /** Abre el formulario del envío y trae las empresas destino. */
  protected abrirNuevo(): void {
    this.nuevo.set(true);
    this.errorEnvio.set(null);
    this.destinoId.set(null);
    this.bodegaOrigen.set(null);
    this.nota.set('');
    this.cantidad.set(null);
    this.lineas.set([]);
    this.buscador.limpiar();
    this.servicio.destinos().subscribe({ next: l => this.destinos.set(l), error: e => this.avisos.error(e) });
  }

  /** Suma el producto y la cantidad escritos a la lista (si el producto ya está, reemplaza su cantidad). */
  protected agregarLinea(): void {
    const p = this.buscador.producto();
    const c = this.cantidad();
    if (!p?.id) {
      this.avisos.ok('Elige un producto de la lista.');
      return;
    }
    if (!c || c <= 0) {
      this.avisos.ok('Escribe una cantidad mayor que cero.');
      return;
    }
    const linea: LineaEnvio = { productoId: p.id, codigo: p.codigo ?? '', nombre: p.nombre ?? '', cantidad: c };
    this.lineas.update(l => [...l.filter(x => x.productoId !== p.id), linea]);
    this.buscador.limpiar();
    this.cantidad.set(null);
  }

  /** @param id producto que sale de la lista */
  protected quitarLinea(id: number): void {
    this.lineas.update(l => l.filter(x => x.productoId !== id));
  }

  /** Envía el traslado. Un 409 (sin pareja, sin stock) se muestra completo para poder corregirlo. */
  protected enviar(): void {
    this.errorEnvio.set(null);
    if (!this.destinoId() || !this.bodegaOrigen()) {
      this.errorEnvio.set('Elige la empresa destino y la bodega de origen.');
      return;
    }
    if (!this.lineas().length) {
      this.errorEnvio.set('Agrega al menos un producto.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.enviar({
      empresaDestinoId: this.destinoId()!,
      bodegaOrigenId: this.bodegaOrigen()!,
      lineas: this.lineas().map(l => ({ productoId: l.productoId, cantidad: l.cantidad })),
      nota: this.nota().trim() || undefined,
    }).subscribe({
      next: d => {
        this.trabajando.set(false);
        this.nuevo.set(false);
        this.avisos.ok(`Traslado ${d.resumen?.consecutivo} enviado.`);
        this.cargar(0);
        this.mostrar(d);
      },
      error: e => {
        this.trabajando.set(false);
        this.errorEnvio.set(mensajeError(e));
      },
    });
  }

  /** @param t traslado de la lista que se abre */
  protected abrir(t: TrasladoResumen): void {
    this.servicio.obtener(t.id!).subscribe({ next: d => this.mostrar(d), error: e => this.avisos.error(e) });
  }

  /** Cierra el detalle. */
  protected cerrarDetalle(): void {
    this.detalle.set(null);
  }

  /** @param id línea; @param valor cantidad que llegó */
  protected fijarRecibida(id: number, valor: number | null): void {
    this.recibidas.update(m => ({ ...m, [id]: valor }));
  }

  /** Recibe el traslado con lo escrito por línea (por defecto, todo lo enviado). */
  protected recibir(): void {
    const d = this.detalle();
    if (!d) {
      return;
    }
    if (!this.bodegaDestino()) {
      this.errorAccion.set('Elige la bodega a la que entra la mercancía.');
      return;
    }
    const lineas = (d.lineas ?? []).map(l => ({ lineaId: l.id!, cantidadRecibida: this.recibidas()[l.id!] ?? 0 }));
    this.accion(this.servicio.recibir(d.resumen!.id!, { bodegaDestinoId: this.bodegaDestino()!, lineas, nota: this.notaRecepcion().trim() || undefined }),
      'Traslado recibido.');
  }

  /** Anula el traslado en tránsito; el motivo es obligatorio. */
  protected anular(): void {
    const d = this.detalle();
    if (!d) {
      return;
    }
    if (!this.motivo().trim()) {
      this.errorAccion.set('Escribe el motivo de la anulación.');
      return;
    }
    this.accion(this.servicio.anular(d.resumen!.id!, this.motivo().trim()), 'Traslado anulado.');
  }

  private accion(llamada: ReturnType<TrasladosGrupoService['recibir']>, ok: string): void {
    this.trabajando.set(true);
    this.errorAccion.set(null);
    llamada.subscribe({
      next: d => {
        this.trabajando.set(false);
        this.avisos.ok(ok);
        this.mostrar(d);
        this.cargar(this.numeroPagina());
      },
      error: e => {
        this.trabajando.set(false);
        this.errorAccion.set(mensajeError(e));
      },
    });
  }

  /** Deja el detalle a la vista y prepara los campos de recepción con lo enviado como valor por defecto. */
  private mostrar(d: TrasladoDetalle): void {
    this.errorAccion.set(null);
    this.motivo.set('');
    this.notaRecepcion.set('');
    this.bodegaDestino.set(null);
    this.recibidas.set(Object.fromEntries((d.lineas ?? []).map(l => [l.id!, l.cantidad ?? 0])));
    this.detalle.set(d);
  }
}
