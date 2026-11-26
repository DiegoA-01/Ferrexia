import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AtributoVariante, Producto, VarianteNueva, Variantes } from '../../core/api/modelos';
import { NumeroPipe } from '../../core/formato/formato';
import { InventarioService } from '../../core/servicios/inventario.service';
import { Avisos } from '../../core/ui/avisos';
import { Cifra } from '../../core/ui/cifra';

/** Atributo mientras se escribe: los valores van separados por coma. */
interface AtributoEnEdicion {
  nombre: string;
  valores: string;
}

/** Una combinación de valores: nueva (con sus datos) o ya existente (solo se muestra). */
export interface FilaVariante {
  clave: string;
  valores: Record<string, string>;
  codigo: string;
  codigoBarras: string;
  costoReferencia: number | null;
  precio: number | null;
  stockMinimo: number | null;
  stockMaximo: number | null;
  /** Variante que ya existe (al editar un producto general). */
  existente?: Producto;
  /** {@code true} mientras el código sea el propuesto (sigue al código del producto general). */
  auto?: boolean;
}

/** Datos que se copian a todas las filas con "Llenar todos". */
interface Relleno {
  costoReferencia: number | null;
  precio: number | null;
  stockMinimo: number | null;
  stockMaximo: number | null;
}

/**
 * @param valores valor por atributo
 * @returns clave para comparar combinaciones sin distinguir mayúsculas
 */
function clave(valores: Record<string, string>): string {
  return Object.entries(valores).map(([a, v]) => `${a.trim().toLowerCase()}=${v.trim().toLowerCase()}`).join('|');
}

/**
 * @param texto valores separados por coma
 * @returns valores limpios, sin vacíos ni repetidos
 */
export function separarValores(texto: string): string[] {
  const vistos = new Set<string>();
  return texto.split(',').map(v => v.trim()).filter(v => v && !vistos.has(v.toLowerCase()) && vistos.add(v.toLowerCase()));
}

/**
 * @param atributos atributos con sus valores
 * @returns cada combinación como valor por atributo, en el orden de los atributos (como las arma el backend)
 */
export function combinar(atributos: AtributoVariante[]): Record<string, string>[] {
  let resultado: Record<string, string>[] = [{}];
  for (const a of atributos) {
    resultado = resultado.flatMap(parcial => (a.valores ?? []).map(v => ({ ...parcial, [a.nombre]: v })));
  }
  return resultado;
}

/**
 * Variantes de un producto (spec 028): atributos con sus valores y una tabla con una fila por combinación para código,
 * código de barras, costo, precio, mínimo y máximo. Al crear, el formulario del producto pide {@link datos}; al editar un
 * producto general, guarda aquí mismo los valores nuevos (las combinaciones que salen se desactivan, nada se borra).
 * La pantalla solo arma las combinaciones para mostrarlas: el backend las vuelve a armar y valida todo.
 */
@Component({
  selector: 'sc-producto-variantes',
  imports: [FormsModule, Cifra, NumeroPipe],
  template: `
    <div class="variantes">
      <h3>Atributos</h3>
      <p class="texto-chico texto-2">
        Por ejemplo Medida: 1", 1 1/2", 2". Separe los valores con comas. Máximo 3 atributos y 100 combinaciones.
        @if (productoId()) { Los nombres de los atributos no cambian: solo se agregan o quitan valores. }
      </p>
      @for (a of atributos(); track $index; let i = $index) {
        <div class="atributo">
          <input class="entrada nombre" [attr.aria-label]="'Atributo ' + (i + 1)" placeholder="Atributo (Medida, Color…)"
            [disabled]="!!productoId()" [ngModel]="a.nombre" (ngModelChange)="cambiarAtributo(i, 'nombre', $event)">
          <input class="entrada" [attr.aria-label]="'Valores de ' + (a.nombre || 'atributo ' + (i + 1))" placeholder="Valores separados por coma"
            [ngModel]="a.valores" (ngModelChange)="cambiarAtributo(i, 'valores', $event)">
          @if (!productoId() && atributos().length > 1) {
            <button type="button" class="boton chico" (click)="quitarAtributo(i)" aria-label="Quitar atributo">Quitar</button>
          }
        </div>
      }
      @if (!productoId() && atributos().length < 3) {
        <button type="button" class="boton chico" (click)="agregarAtributo()">Agregar atributo</button>
      }

      <h3>Combinaciones · {{ filas().length }}</h3>
      @if (demasiadas()) {
        <p class="aviso peligro">Salen {{ cuantas() }} combinaciones; el máximo es 100.</p>
      } @else if (filas().length) {
        @if (nuevas().length) {
          <div class="relleno" role="group" aria-label="Llenar todos">
            <input scCifra class="entrada num" aria-label="Costo para todas" placeholder="Costo" [(ngModel)]="relleno.costoReferencia">
            <input scCifra class="entrada num" aria-label="Precio para todas" placeholder="Precio" [(ngModel)]="relleno.precio">
            <input scCifra [decimales]="4" class="entrada num" aria-label="Mínimo para todas" placeholder="Mínimo" [(ngModel)]="relleno.stockMinimo">
            <input scCifra [decimales]="4" class="entrada num" aria-label="Máximo para todas" placeholder="Máximo" [(ngModel)]="relleno.stockMaximo">
            <button type="button" class="boton chico" (click)="llenarTodos()">Llenar todos</button>
          </div>
        }
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Variante</th><th>Código</th><th>Código de barras</th><th class="num">Costo</th><th class="num">Precio</th>
              <th class="num">Mínimo</th><th class="num">Máximo</th></tr></thead>
            <tbody>
              @for (f of filas(); track f.clave) {
                @if (f.existente; as v) {
                  <tr [class.inactiva]="!v.activo">
                    <td><strong>{{ etiqueta(f) }}</strong></td><td>{{ v.codigo }}</td>
                    <td colspan="3" class="texto-2">Ya existe · stock {{ v.stockTotal | numero }}{{ v.activo ? '' : ' · se reactiva' }}</td>
                    <td class="num">{{ v.stockMinimo | numero }}</td><td class="num">{{ v.stockMaximo | numero }}</td>
                  </tr>
                } @else {
                  <tr>
                    <td><strong>{{ etiqueta(f) }}</strong> <span class="chip azul">Nueva</span></td>
                    <td><input class="entrada" [attr.aria-label]="'Código de ' + etiqueta(f)" [ngModel]="f.codigo" (ngModelChange)="f.codigo = $event; f.auto = false"></td>
                    <td><input class="entrada" [attr.aria-label]="'Código de barras de ' + etiqueta(f)" [(ngModel)]="f.codigoBarras"></td>
                    <td><input scCifra class="entrada num" [attr.aria-label]="'Costo de ' + etiqueta(f)" [(ngModel)]="f.costoReferencia"></td>
                    <td><input scCifra class="entrada num" [attr.aria-label]="'Precio de ' + etiqueta(f)" [(ngModel)]="f.precio"></td>
                    <td><input scCifra [decimales]="4" class="entrada num" [attr.aria-label]="'Mínimo de ' + etiqueta(f)" [(ngModel)]="f.stockMinimo"></td>
                    <td><input scCifra [decimales]="4" class="entrada num" [attr.aria-label]="'Máximo de ' + etiqueta(f)" [(ngModel)]="f.stockMaximo"></td>
                  </tr>
                }
              }
              @for (v of salen(); track v.id) {
                <tr class="inactiva">
                  <td><strong>{{ v.nombre }}</strong></td><td>{{ v.codigo }}</td>
                  <td colspan="5" class="texto-2">{{ v.activo ? 'Se desactiva (su valor se quitó)' : 'Inactiva' }} · stock {{ v.stockTotal | numero }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="texto-chico texto-2">Cada variante es un producto con su propio stock, kardex y precio. El precio, si lo escribes, queda en la lista General.</p>
      } @else {
        <p class="texto-2">Escribe al menos un atributo con sus valores.</p>
      }
      @if (productoId()) {
        <div class="fila fin">
          <button type="button" class="boton primario" [disabled]="!valido() || trabajando()" (click)="guardar()">Guardar variantes</button>
        </div>
      }
    </div>
  `,
  styles: `
    .variantes { display: grid; gap: 10px; margin-top: 12px; }
    h3 { margin: 6px 0 0; font-size: 1rem; }
    .atributo { display: grid; grid-template-columns: minmax(120px, 200px) 1fr auto; gap: 8px; align-items: center; }
    .relleno { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    .relleno .entrada { max-width: 130px; }
    .tabla .entrada { min-width: 90px; }
    tr.inactiva td { opacity: .65; }
    @media (max-width: 640px) { .atributo { grid-template-columns: 1fr; } }
  `,
})
export class ProductoVariantes implements OnInit {
  private readonly servicio = inject(InventarioService);
  private readonly avisos = inject(Avisos);

  /** Código del producto general: de él salen los códigos propuestos (TD-NEGRO). */
  readonly codigoBase = input('');
  /** Producto general que se edita; {@code null} = producto nuevo. */
  readonly productoId = input<number | null>(null);
  /** Se emite al guardar los valores de un producto general. */
  readonly guardado = output<Variantes>();

  protected readonly atributos = signal<AtributoEnEdicion[]>([{ nombre: '', valores: '' }]);
  /** Variantes que ya existen (al editar). */
  private readonly existentes = signal<Producto[]>([]);
  /** Datos escritos por combinación, para no perderlos al cambiar los valores. */
  private readonly escritas = new Map<string, FilaVariante>();
  protected readonly trabajando = signal(false);
  protected relleno: Relleno = { costoReferencia: null, precio: null, stockMinimo: null, stockMaximo: null };

  /** Atributos limpios (con nombre y al menos un valor). */
  protected readonly limpios = computed<AtributoVariante[]>(() => this.atributos()
    .map(a => ({ nombre: a.nombre.trim(), valores: separarValores(a.valores) }))
    .filter(a => a.nombre && a.valores.length));
  protected readonly cuantas = computed(() => this.limpios().reduce((n, a) => n * a.valores.length, this.limpios().length ? 1 : 0));
  protected readonly demasiadas = computed(() => this.cuantas() > 100);
  /** Una fila por combinación; conserva lo ya escrito y marca las que ya existen. */
  readonly filas = computed<FilaVariante[]>(() => {
    if (this.demasiadas()) {
      return [];
    }
    const porClave = new Map(this.existentes().map(v => [clave(v.combinacion ?? {}), v]));
    return combinar(this.limpios()).map(valores => {
      const k = clave(valores);
      const existente = porClave.get(k);
      if (existente) {
        return { clave: k, valores, codigo: existente.codigo ?? '', codigoBarras: '', costoReferencia: null, precio: null,
          stockMinimo: null, stockMaximo: null, existente };
      }
      let fila = this.escritas.get(k);
      if (!fila) {
        fila = { clave: k, valores, codigo: '', codigoBarras: '', costoReferencia: null, precio: null, stockMinimo: 0,
          stockMaximo: null, auto: true };
        this.escritas.set(k, fila);
      }
      if (fila.auto) {
        fila.codigo = this.codigoPropuesto(valores);
      }
      return fila;
    });
  });
  protected readonly nuevas = computed(() => this.filas().filter(f => !f.existente));
  /** Variantes que ya existen y cuya combinación ya no sale (se desactivan al guardar). */
  protected readonly salen = computed(() => {
    const quedan = new Set(this.filas().map(f => f.clave));
    return this.existentes().filter(v => !quedan.has(clave(v.combinacion ?? {})));
  });

  ngOnInit(): void {
    const id = this.productoId();
    if (id) {
      this.servicio.variantes(id).subscribe({ next: v => this.aplicar(v), error: e => this.avisos.error(e) });
    }
  }

  /** @returns {@code true} si hay combinaciones y cada fila nueva tiene su código y su mínimo */
  valido(): boolean {
    const filas = this.filas();
    return filas.length > 0 && !this.demasiadas()
      && filas.every(f => !!f.existente || (!!f.codigo.trim() && f.stockMinimo != null && f.stockMinimo >= 0));
  }

  /** @returns atributos y filas nuevas tal como los recibe el backend */
  datos(): { atributos: AtributoVariante[]; variantes: VarianteNueva[] } {
    return {
      atributos: this.limpios(),
      variantes: this.nuevas().map(f => ({
        valores: f.valores, codigo: f.codigo.trim(), codigoBarras: f.codigoBarras.trim() || undefined,
        costoReferencia: f.costoReferencia ?? undefined, precio: f.precio ?? undefined, stockMinimo: f.stockMinimo ?? 0,
        stockMaximo: f.stockMaximo ?? undefined,
      })),
    };
  }

  /** @param f fila; valores separados por " · " */
  protected etiqueta(f: FilaVariante): string {
    return Object.values(f.valores).join(' · ');
  }

  /** @param i atributo; @param campo nombre o valores; @param valor texto escrito */
  protected cambiarAtributo(i: number, campo: keyof AtributoEnEdicion, valor: string): void {
    this.atributos.update(l => l.map((a, j) => (j === i ? { ...a, [campo]: valor } : a)));
  }

  protected agregarAtributo(): void {
    this.atributos.update(l => [...l, { nombre: '', valores: '' }]);
  }

  /** @param i atributo que se quita */
  protected quitarAtributo(i: number): void {
    this.atributos.update(l => l.filter((_, j) => j !== i));
  }

  /** Copia costo, precio, mínimo y máximo escritos arriba a todas las filas nuevas (lo vacío no se copia). */
  protected llenarTodos(): void {
    for (const f of this.nuevas()) {
      for (const campo of ['costoReferencia', 'precio', 'stockMinimo', 'stockMaximo'] as const) {
        if (this.relleno[campo] != null) {
          f[campo] = this.relleno[campo];
        }
      }
    }
    this.atributos.update(l => [...l]);
  }

  /** Guarda los valores de un producto general: crea las combinaciones nuevas y desactiva las que salen. */
  protected guardar(): void {
    const id = this.productoId();
    if (!id || !this.valido()) {
      return;
    }
    const salen = this.salen().filter(v => v.activo).length;
    if (salen && !window.confirm(`Se desactivan ${salen} variante(s) cuyo valor quitaste. Su historial y su stock se conservan. ¿Seguir?`)) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.cambiarVariantes(id, this.datos()).subscribe({
      next: v => {
        this.trabajando.set(false);
        this.escritas.clear();
        this.aplicar(v);
        this.avisos.ok('Variantes guardadas.');
        this.guardado.emit(v);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param v producto general con sus atributos y variantes */
  private aplicar(v: Variantes): void {
    this.existentes.set(v.variantes ?? []);
    this.atributos.set((v.atributos ?? []).map(a => ({ nombre: a.nombre, valores: (a.valores ?? []).join(', ') })));
  }

  /** @param valores combinación @returns código propuesto: el del general y los valores, sin espacios ni símbolos */
  private codigoPropuesto(valores: Record<string, string>): string {
    const base = this.codigoBase().trim().toUpperCase();
    const partes = Object.values(valores).map(v => v.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase()
      .replace(/[^A-Z0-9.]+/g, '').slice(0, 12));
    return [base, ...partes].filter(Boolean).join('-').slice(0, 50);
  }
}
