import { Component, HostListener, afterNextRender, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { TablasResponsivas } from './core/ui/tablas-responsivas';

/** Raíz: cada zona (empresa, consola del dueño y la página pública para cotizar) trae su propio marco. */
@Component({
  selector: 'sc-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  constructor() {
    // spec 017: en celular las tablas pasan a tarjetas; el servicio pone el encabezado en cada celda
    const tablas = inject(TablasResponsivas);
    afterNextRender(() => tablas.iniciar());
  }

  /**
   * Red de seguridad de la spec 017: si queda algún {@code <input type="number">}, la rueda del mouse no cambia su
   * valor (el campo pierde el foco y la página baja normalmente). Los campos de la aplicación usan {@code scCifra}.
   *
   * @param e giro de la rueda
   */
  @HostListener('document:wheel', ['$event'])
  protected rueda(e: WheelEvent): void {
    const activo = document.activeElement;
    if (activo instanceof HTMLInputElement && activo.type === 'number' && e.target === activo) {
      activo.blur();
    }
  }
}
