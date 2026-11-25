import { Component, inject, output, signal } from '@angular/core';

import { CargaPrecios } from '../../../core/api/modelos';
import { VentasService } from '../../../core/servicios/ventas.service';
import { Avisos, descargar } from '../../../core/ui/avisos';

/**
 * Carga de precios de venta por Excel (spec 001). Al elegir el archivo se revisa sin guardar; si no tiene errores se
 * ofrece fijar los precios (todo o nada). Desde la spec 019 vive en Configuración › Importación de datos.
 */
@Component({
  selector: 'sc-carga-precios',
  template: `
    <section class="tarjeta">
      <h2>Precios de venta</h2>
      <p class="texto-chico texto-2">Sube un Excel con las columnas codigo, lista, unidad, precio, precio_minimo y motivo. Primero se revisa todo el archivo: si una fila tiene un error no se cambia ningún precio. Lista vacía = General; unidad vacía = la del producto.</p>
      <div class="fila">
        <button type="button" class="boton" (click)="bajarPlantilla()">Descargar plantilla</button>
        <label class="boton" for="excel-precios">Elegir archivo</label>
        <input id="excel-precios" class="oculto" type="file" accept=".xlsx" (change)="elegirArchivo($event)">
        @if (archivo(); as f) { <span class="texto-chico">{{ f.name }}</span> }
      </div>
      @if (cargando()) { <p class="cargando">Revisando el archivo…</p> }
      @if (carga(); as r) {
        @if ((r.errores ?? []).length > 0) {
          <div class="resultado-carga error">
            <p class="sin-margen"><strong>No se cambió ningún precio.</strong> Corrige estas filas y vuelve a subir el archivo:</p>
            <ul>@for (x of r.errores ?? []; track x.fila) { <li>Fila {{ x.fila }}: {{ x.mensaje }}</li> }</ul>
          </div>
        } @else if (r.soloValidar) {
          <div class="resultado-carga">
            <p class="sin-margen">El archivo está bien: se fijarán {{ r.precios }} precios en {{ r.filas }} filas.</p>
            <button type="button" class="boton primario" [disabled]="cargando()" (click)="subir(false)">Fijar los precios</button>
          </div>
        } @else if (r.aplicado) {
          <div class="resultado-carga"><p class="sin-margen">{{ r.precios }} precios fijados. El anterior de cada uno quedó en el historial.</p></div>
        }
      }
    </section>
  `,
  styles: `
    .fila { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    .oculto { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    label.boton { cursor: pointer; }
    .resultado-carga { display: grid; gap: 8px; margin-top: 12px; padding: 12px 14px; border-radius: 10px; background: #f4f8f6;
      justify-items: start; ul { margin: 0; padding-left: 20px; font-size: 13px; }
      &.error { background: #fdf0ec; color: var(--sc-rojo); } }
  `,
})
export class CargaPreciosExcel {
  private readonly ventas = inject(VentasService);
  private readonly avisos = inject(Avisos);

  /** Los precios quedaron fijados. */
  readonly aplicada = output<CargaPrecios>();

  /** Archivo de precios elegido y lo que dijo la revisión (todo o nada). */
  protected readonly archivo = signal<File | null>(null);
  protected readonly carga = signal<CargaPrecios | null>(null);
  protected readonly cargando = signal(false);

  /** @param evento cambio del campo de archivo; guarda el Excel elegido y lo revisa sin guardar */
  protected elegirArchivo(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const f = entrada.files?.[0] ?? null;
    entrada.value = '';
    this.archivo.set(f);
    this.carga.set(null);
    if (f) {
      this.subir(true);
    }
  }

  /** @param soloValidar true para revisar; false para fijar los precios (todo o nada) */
  protected subir(soloValidar: boolean): void {
    const f = this.archivo();
    if (!f) {
      return;
    }
    this.cargando.set(true);
    this.ventas.cargarPrecios(f, soloValidar).subscribe({
      next: r => {
        this.cargando.set(false);
        this.carga.set(r);
        if (r.aplicado) {
          this.avisos.ok(`${r.precios} precios fijados. El anterior de cada uno quedó en el historial.`);
          this.archivo.set(null);
          this.aplicada.emit(r);
        }
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected bajarPlantilla(): void {
    this.ventas.plantillaPrecios().subscribe({
      next: b => descargar(b, 'plantilla-precios.xlsx'),
      error: e => this.avisos.error(e),
    });
  }
}
