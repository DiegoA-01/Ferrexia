import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { VentasService } from '../../../core/servicios/ventas.service';
import { SesionService } from '../../../core/sesion/sesion.service';
import { CargaPreciosExcel } from './carga-precios';
import { CargaProductos } from './carga-productos';

/**
 * Configuración › Importación de datos (spec 019): un solo lugar para traer datos desde Excel. Cada carga se muestra
 * solo a quien hoy la puede usar:
 * <ul>
 *   <li>Productos: el administrador de compras, con el módulo de inventario.</li>
 *   <li>Precios de venta: el dueño o, si el dueño se los delegó, el administrador de compras (lo dice el backend con
 *       {@code puedeEditar}), con el módulo de ventas.</li>
 * </ul>
 */
@Component({
  selector: 'sc-importacion',
  imports: [RouterLink, CargaProductos, CargaPreciosExcel],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / <a routerLink="/app/configuracion">Configuración</a> / Importación de datos</p>
        <div class="encabezado"><div><h1>Importación de datos</h1>
          <p>Trae productos y precios desde Excel. Cada archivo se revisa primero y, si una fila tiene un error, no se cambia nada.</p></div></div>
      </div>

      @if (conProductos()) { <sc-carga-productos /> }
      @if (conPrecios()) { <sc-carga-precios /> }
      @if (!conProductos() && !conPrecios() && preciosRevisados()) {
        <section class="tarjeta"><p class="sin-margen">No tienes cargas por Excel disponibles. Pídele al administrador de la empresa que te dé acceso.</p></section>
      }
    </div>
  `,
})
export class Importacion implements OnInit {
  private readonly sesion = inject(SesionService);
  private readonly ventas = inject(VentasService);

  /** El administrador de compras le puede cambiar precios (lo decide el dueño; lo informa el backend). */
  private readonly comprasFijaPrecios = signal(false);
  /** Ya se sabe si el usuario puede cargar precios. */
  protected readonly preciosRevisados = signal(false);

  private readonly escribe = computed(() => !this.sesion.soloLectura());
  protected readonly conProductos = computed(() =>
    this.escribe() && this.sesion.tieneRol(['ADMIN_COMPRAS']) && this.sesion.tieneModulo('INVENTARIO'));
  protected readonly conPrecios = computed(() =>
    this.escribe() && this.sesion.tieneModulo('VENTAS')
    && (this.sesion.tieneRol(['ADMIN_SISTEMA']) || this.comprasFijaPrecios()));

  ngOnInit(): void {
    const preguntar = this.escribe() && this.sesion.tieneModulo('VENTAS')
      && !this.sesion.tieneRol(['ADMIN_SISTEMA']) && this.sesion.tieneRol(['ADMIN_COMPRAS']);
    if (!preguntar) {
      this.preciosRevisados.set(true);
      return;
    }
    this.ventas.precios(null).subscribe({
      next: d => {
        this.comprasFijaPrecios.set(!!d.puedeEditar);
        this.preciosRevisados.set(true);
      },
      error: () => this.preciosRevisados.set(true),
    });
  }
}
