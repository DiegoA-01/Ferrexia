import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { CategoriaMovimientoCaja, TipoCategoriaMovimiento } from '../../core/api/modelos';
import { CajaService } from '../../core/servicios/caja.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/** Texto de cada tipo de categoría. */
const TIPOS: Record<TipoCategoriaMovimiento, string> = { EGRESO: 'Egreso (gastos y retiros)', INGRESO: 'Ingreso' };

/**
 * Categorías de ingresos y egresos de caja (spec 021), en Cajas y turnos › Cajas. El dueño y el supervisor de ventas
 * crean, renombran y desactivan; no se borran, porque los movimientos ya registrados las siguen nombrando.
 */
@Component({
  selector: 'sc-categorias-caja',
  imports: [FormsModule],
  template: `
    <section class="tarjeta">
      <h2>Categorías de ingresos y egresos</h2>
      <p class="texto-chico texto-2">El cajero elige una al registrar un gasto o un ingreso. Desactivar una la oculta sin borrar lo ya registrado.</p>
      @if (puedeEditar()) {
        <div class="fila nueva">
          <select class="entrada" aria-label="Tipo de la categoría nueva" [ngModel]="tipoNuevo()" (ngModelChange)="tipoNuevo.set($event)">
            @for (t of tipos; track t) { <option [ngValue]="t">{{ textoTipo(t) }}</option> }
          </select>
          <input class="entrada" maxlength="80" placeholder="Pago a domiciliario" aria-label="Nombre de la categoría nueva"
            [ngModel]="nombreNuevo()" (ngModelChange)="nombreNuevo.set($event)">
          <button type="button" class="boton primario" [disabled]="!nombreNuevo().trim() || guardando()" (click)="crear()">Agregar</button>
        </div>
      }
      <div class="tabla-contenedor"><table class="tabla">
        <thead><tr><th>Categoría</th><th>Tipo</th><th>Estado</th><th></th></tr></thead>
        <tbody>
          @for (c of categorias(); track c.id) {
            <tr [class.inactiva]="!c.activa">
              <td>
                @if (editando() === c.id) {
                  <input class="entrada" maxlength="80" [attr.aria-label]="'Nombre nuevo de ' + c.nombre" [ngModel]="nombreEditado()" (ngModelChange)="nombreEditado.set($event)">
                } @else { {{ c.nombre }} }
              </td>
              <td>{{ textoTipo(c.tipo!) }}</td>
              <td>{{ c.activa ? 'Activa' : 'Inactiva' }}</td>
              <td class="num acciones">
                @if (puedeEditar()) {
                  @if (editando() === c.id) {
                    <button type="button" class="boton chico primario" [disabled]="!nombreEditado().trim()" (click)="guardar(c, c.activa!)">Guardar</button>
                    <button type="button" class="boton chico" (click)="editando.set(null)">Cancelar</button>
                  } @else {
                    <button type="button" class="boton chico" (click)="renombrar(c)">Renombrar</button>
                    <button type="button" class="boton chico" (click)="guardar(c, !c.activa)">{{ c.activa ? 'Desactivar' : 'Activar' }}</button>
                  }
                }
              </td>
            </tr>
          } @empty { <tr><td colspan="4" class="vacio">No hay categorías. Agregue la primera.</td></tr> }
        </tbody>
      </table></div>
    </section>
  `,
  styles: `
    h2 { margin: 0 0 4px; }
    .nueva { flex-wrap: wrap; gap: 8px; margin: 8px 0 12px; input { flex: 1 1 200px; } }
    .acciones { white-space: nowrap; button + button { margin-left: 4px; } }
    tr.inactiva td { color: var(--sc-texto-3); }
  `,
})
export class CategoriasCaja implements OnInit {
  private readonly servicio = inject(CajaService);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(Avisos);

  protected readonly tipos = Object.keys(TIPOS) as TipoCategoriaMovimiento[];
  protected readonly categorias = signal<CategoriaMovimientoCaja[]>([]);
  protected readonly tipoNuevo = signal<TipoCategoriaMovimiento>('EGRESO');
  protected readonly nombreNuevo = signal('');
  protected readonly editando = signal<number | null>(null);
  protected readonly nombreEditado = signal('');
  protected readonly guardando = signal(false);

  /** Las administran el dueño y el supervisor de ventas; auditoría solo las ve. */
  protected readonly puedeEditar = computed(() =>
    this.sesion.tieneRol(['ADMIN_SISTEMA', 'SUPERVISOR_VENTAS']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.cargar();
  }

  protected crear(): void {
    this.guardando.set(true);
    this.servicio.crearCategoria(this.tipoNuevo(), this.nombreNuevo().trim()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.nombreNuevo.set('');
        this.avisos.ok('Categoría creada.');
        this.cargar();
      },
      error: e => {
        this.guardando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param c categoría que se va a renombrar */
  protected renombrar(c: CategoriaMovimientoCaja): void {
    this.editando.set(c.id!);
    this.nombreEditado.set(c.nombre ?? '');
  }

  /**
   * Guarda el nombre (si se está renombrando) y el estado.
   *
   * @param c      categoría
   * @param activa estado nuevo
   */
  protected guardar(c: CategoriaMovimientoCaja, activa: boolean): void {
    const nombre = this.editando() === c.id ? this.nombreEditado().trim() : c.nombre!;
    this.servicio.editarCategoria(c.id!, nombre, activa).subscribe({
      next: () => {
        this.editando.set(null);
        this.avisos.ok(activa === c.activa ? 'Categoría renombrada.' : activa ? 'Categoría activada.' : 'Categoría desactivada.');
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param t tipo @returns texto */
  protected textoTipo(t: TipoCategoriaMovimiento): string {
    return TIPOS[t];
  }

  private cargar(): void {
    this.servicio.categorias().subscribe({ next: l => this.categorias.set(l), error: e => this.avisos.error(e) });
  }
}
