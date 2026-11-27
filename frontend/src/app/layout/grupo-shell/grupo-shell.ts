import { Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { iniciales } from '../../core/formato/formato';
import { GrupoSesionService } from '../../core/sesion/grupo-sesion.service';

/** Marco simple de la zona del grupo (spec 035): barra con el grupo y la persona, y la pantalla debajo. */
@Component({
  selector: 'sc-grupo-shell',
  imports: [RouterOutlet],
  template: `
    <div class="marco">
      <header class="superior">
        <span class="marca"><span class="escudo material-symbols-outlined">hub</span><strong>{{ sesion.grupoNombre() }}</strong></span>
        <span class="espacio"></span>
        <span class="chip verde"><span class="material-symbols-outlined">check</span>MFA activo</span>
        <span class="avatar">{{ siglas() }}</span>
        <span class="datos"><strong>{{ sesion.nombre() }}</strong><span>Super administrador del grupo</span></span>
        <button type="button" class="boton chico" (click)="sesion.salir()">Salir</button>
      </header>
      <main><router-outlet /></main>
    </div>
  `,
  styles: `
    .marco { min-height: 100vh; padding: 16px; display: flex; flex-direction: column; gap: 20px;
      background: radial-gradient(900px 500px at 0% 0%, #f6c8b7 0%, transparent 60%), linear-gradient(180deg, #fbf4f1, #ececec 45%); }
    .superior { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; padding: 12px 16px; border-radius: 20px; background: rgb(255 255 255 / 80%); }
    .marca { display: flex; align-items: center; gap: 10px; font-size: 18px; }
    .escudo { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 8px; background: var(--sc-primario); color: #fff; font-size: 20px; }
    .espacio { flex: 1; }
    .datos { display: flex; flex-direction: column; font-size: 13px; color: var(--sc-texto-2); strong { font-size: 15px; color: var(--sc-texto); } }
    .avatar { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: #eee; font-weight: 600; font-size: 14px; }
    main { padding: 4px 8px 32px; max-width: 1200px; width: 100%; margin: 0 auto; }
    @media (max-width: 600px) { .datos { display: none; } }
  `,
})
export class GrupoShell {
  protected readonly sesion = inject(GrupoSesionService);
  protected readonly siglas = computed(() => iniciales(this.sesion.nombre()));
}
