import { Component, inject, output, signal } from '@angular/core';

import { CargaMasiva } from '../../../core/api/modelos';
import { InventarioService } from '../../../core/servicios/inventario.service';
import { Avisos, descargar } from '../../../core/ui/avisos';

/**
 * Carga masiva de productos por Excel (paso 18-d). Primero se revisa el archivo y, si no tiene errores, se aplica todo
 * junto (todo o nada). Desde la spec 019 vive en Configuración › Importación de datos.
 */
@Component({
  selector: 'sc-carga-productos',
  template: `
    <section class="tarjeta">
      <div class="tarjeta-cabecera"><h2>Productos</h2><button type="button" class="boton chico" (click)="bajarPlantilla()">Bajar plantilla</button></div>
      <p class="texto-2">Crea o actualiza productos desde un Excel. Primero se revisa el archivo; si no hay errores, se aplica todo junto (todo o nada). Un archivo con solo <code>codigo</code> y <code>costo_referencia</code> actualiza costos.</p>
      <div class="fila">
        <input type="file" accept=".xlsx" (change)="elegirArchivo($event)" aria-label="Archivo de Excel de productos">
        <button type="button" class="boton" [disabled]="!archivo() || trabajando()" (click)="cargarExcel(true)">Revisar</button>
        <button type="button" class="boton primario" [disabled]="!carga() || !!carga()?.errores?.length || !!carga()?.aplicado || trabajando()" (click)="cargarExcel(false)">Aplicar</button>
      </div>
      @if (carga(); as c) {
        <div class="aviso margen-arriba" [class.rojo]="c.errores?.length" [class.verde]="!c.errores?.length">
          {{ c.filas }} filas · {{ c.creados }} por crear · {{ c.actualizados }} por actualizar · {{ c.categoriasCreadas }} categorías nuevas
          @if (c.errores?.length) { · {{ c.errores!.length }} errores: corrígelos y vuelve a revisar. }
        </div>
        @for (e of c.errores ?? []; track $index) {
          <p class="texto-chico error-fila">Fila {{ e.fila }}: {{ e.mensaje }}</p>
        }
      }
    </section>
  `,
  styles: `
    .fila { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    .margen-arriba { margin-top: 12px; }
    .error-fila { color: #b3261e; margin: 4px 0 0; }
  `,
})
export class CargaProductos {
  private readonly servicio = inject(InventarioService);
  private readonly avisos = inject(Avisos);

  /** La carga quedó aplicada. */
  readonly aplicada = output<CargaMasiva>();

  protected readonly archivo = signal<File | null>(null);
  protected readonly carga = signal<CargaMasiva | null>(null);
  protected readonly trabajando = signal(false);

  protected bajarPlantilla(): void {
    this.servicio.plantilla().subscribe({ next: b => descargar(b, 'plantilla-productos.xlsx'), error: e => this.avisos.error(e) });
  }

  /** @param evento cambio del selector de archivos */
  protected elegirArchivo(evento: Event): void {
    this.archivo.set((evento.target as HTMLInputElement).files?.[0] ?? null);
    this.carga.set(null);
  }

  /** @param soloValidar primero se revisa; luego se aplica todo o nada */
  protected cargarExcel(soloValidar: boolean): void {
    const a = this.archivo();
    if (!a) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.cargaMasiva(a, soloValidar).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.carga.set(r);
        if (r.aplicado) {
          this.avisos.ok(`Carga aplicada: ${r.creados} creados, ${r.actualizados} actualizados.`);
          this.aplicada.emit(r);
        }
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }
}
