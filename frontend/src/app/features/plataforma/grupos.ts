import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

import { EmpresaPlataforma, GrupoPlataforma, SuperAdmin } from '../../core/api/modelos';
import { mensajeError } from '../../core/http/api';
import { PlataformaService } from '../../core/servicios/plataforma.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Consola del dueño › Grupos (spec 035): crear grupos, asignar o quitar empresas y crear al super administrador de cada
 * grupo (con su segundo factor). No muestra datos de negocio de ninguna empresa.
 */
@Component({
  selector: 'sc-grupos',
  imports: [FormsModule, ReactiveFormsModule],
  template: `
    <div class="pagina">
      <div class="encabezado">
        <div><h1>Grupos de empresas</h1>
          <p class="texto-2">Un grupo reúne empresas de un mismo dueño. Su super administrador entra a todas con una sola cuenta (con segundo factor) y ve el tablero conjunto.</p></div>
      </div>

      <section class="tarjeta">
        <h2>Crear grupo</h2>
        <form class="fila" (ngSubmit)="crearGrupo()">
          <div class="campo crece"><label for="gnombre">Nombre del grupo</label>
            <input id="gnombre" name="gnombre" class="entrada" maxlength="150" [(ngModel)]="nombreGrupo" required></div>
          <button type="submit" class="boton primario" [disabled]="trabajando()">Crear grupo</button>
        </form>
      </section>

      @if (error()) { <div class="aviso rojo" role="alert">{{ error() }}</div> }

      @for (g of grupos(); track g.id) {
        <section class="tarjeta grupo">
          <h2>{{ g.nombre }}</h2>

          <h3>Empresas</h3>
          @for (e of g.empresas ?? []; track e.id) {
            <div class="linea">
              <div><strong>{{ e.razonSocial }}</strong></div>
              <button type="button" class="boton chico" [disabled]="trabajando()" (click)="quitar(e.id!)">Quitar del grupo</button>
            </div>
          } @empty {
            <p class="texto-2">Sin empresas todavía.</p>
          }
          <div class="fila margen">
            <div class="campo crece"><label [for]="'emp' + g.id">Asignar empresa</label>
              <select [id]="'emp' + g.id" class="entrada" [ngModel]="elegida()[g.id!] ?? null" (ngModelChange)="elegir(g.id!, $event)">
                <option [ngValue]="null">Elige una empresa</option>
                @for (e of disponibles(g); track e.id) { <option [ngValue]="e.id">{{ e.razonSocial }}{{ etiquetaOtroGrupo(e.id!) }}</option> }
              </select></div>
            <button type="button" class="boton primario" [disabled]="trabajando() || !elegida()[g.id!]" (click)="asignar(g.id!)">Asignar</button>
          </div>

          <h3>Super administradores</h3>
          @for (s of g.superAdmins ?? []; track s.id) {
            <div class="linea">
              <div><strong>{{ s.nombre }}</strong> <span class="texto-chico texto-2">{{ s.correo }}</span>
                <div class="chips">
                  <span [class]="s.activo ? 'chip verde' : 'chip gris'">{{ s.activo ? 'Activo' : 'Desactivado' }}</span>
                  <span [class]="s.mfaActivo ? 'chip verde' : 'chip ambar'">{{ s.mfaActivo ? 'Segundo factor configurado' : 'Falta configurar el segundo factor' }}</span>
                </div></div>
              <div class="fila">
                <button type="button" class="boton chico" [disabled]="trabajando()" (click)="cambiarEstado(g, s)">{{ s.activo ? 'Desactivar' : 'Activar' }}</button>
                <button type="button" class="boton chico" [disabled]="trabajando()" (click)="reiniciarMfa(g, s)">Reiniciar segundo factor</button>
              </div>
            </div>
          } @empty {
            <p class="texto-2">Todavía no tiene super administrador.</p>
          }
          @if (formSuper() === g.id) {
            <form class="formulario dos margen" [formGroup]="superForm" (ngSubmit)="crearSuper(g.id!)">
              <div class="campo"><label [for]="'sn' + g.id">Nombre</label>
                <input [id]="'sn' + g.id" class="entrada" formControlName="nombre" maxlength="150"></div>
              <div class="campo"><label [for]="'sc' + g.id">Correo (único en toda la plataforma)</label>
                <input [id]="'sc' + g.id" class="entrada" type="email" formControlName="correo" maxlength="150" autocomplete="off"></div>
              <div class="campo"><label [for]="'sp' + g.id">Clave (12 o más caracteres, con letras y números)</label>
                <input [id]="'sp' + g.id" class="entrada" type="password" formControlName="password" autocomplete="new-password"></div>
              <div class="fila">
                <button type="submit" class="boton primario" [disabled]="trabajando() || superForm.invalid">Crear super administrador</button>
                <button type="button" class="boton" (click)="formSuper.set(null)">Cancelar</button>
              </div>
            </form>
          } @else {
            <div class="margen"><button type="button" class="boton" (click)="abrirSuper(g.id!)">
              <span class="material-symbols-outlined">person_add</span> Crear super administrador</button></div>
          }
        </section>
      } @empty {
        <p class="vacio">{{ grupos() ? 'Todavía no hay grupos.' : 'Cargando…' }}</p>
      }
    </div>
  `,
  styles: `
    .pagina { display: flex; flex-direction: column; gap: 20px; }
    h1, h2, p { margin: 0 0 8px; }
    h3 { margin: 18px 0 6px; font-size: 15px; }
    .crece { flex: 1 1 240px; }
    .margen { margin-top: 12px; }
    .linea { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; padding: 10px 0; border-top: 1px solid #f0f0f0; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
    .fila { align-items: flex-end; }
  `,
})
export class Grupos implements OnInit {
  private readonly servicio = inject(PlataformaService);
  private readonly avisos = inject(Avisos);

  protected readonly grupos = signal<GrupoPlataforma[] | null>(null);
  protected readonly empresas = signal<EmpresaPlataforma[]>([]);
  protected readonly trabajando = signal(false);
  /** Último error del backend (p. ej. {@code TRASLADOS_EN_TRANSITO}), con su texto legible. */
  protected readonly error = signal<string | null>(null);
  /** Empresa elegida en el selector de cada grupo. */
  protected readonly elegida = signal<Record<number, number | null>>({});
  /** Grupo cuyo formulario de super administrador está abierto. */
  protected readonly formSuper = signal<number | null>(null);
  protected nombreGrupo = '';

  /** Grupo al que ya pertenece cada empresa (para avisar al moverla). */
  private readonly grupoDe = computed(() => {
    const m = new Map<number, string>();
    for (const g of this.grupos() ?? []) {
      for (const e of g.empresas ?? []) {
        m.set(e.id!, g.nombre!);
      }
    }
    return m;
  });

  protected readonly superForm = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    // la misma regla del backend: 12 a 72 caracteres con al menos una letra y un número
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(72), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
  });

  ngOnInit(): void {
    this.servicio.empresas().subscribe({ next: l => this.empresas.set(l), error: e => this.avisos.error(e) });
    this.cargar();
  }

  /**
   * @param g grupo
   * @returns empresas de la consola que todavía no están en ese grupo
   */
  protected disponibles(g: GrupoPlataforma): EmpresaPlataforma[] {
    const dentro = new Set((g.empresas ?? []).map(e => e.id));
    return this.empresas().filter(e => !dentro.has(e.id));
  }

  /** @param id empresa; texto que avisa si ya está en otro grupo (moverla la saca del anterior) */
  protected etiquetaOtroGrupo(id: number): string {
    const otro = this.grupoDe().get(id);
    return otro ? ` (hoy en «${otro}»)` : '';
  }

  /** @param grupoId grupo; @param empresaId empresa elegida en su selector */
  protected elegir(grupoId: number, empresaId: number | null): void {
    this.elegida.update(m => ({ ...m, [grupoId]: empresaId }));
  }

  /** Crea un grupo con el nombre escrito. */
  protected crearGrupo(): void {
    const nombre = this.nombreGrupo.trim();
    if (!nombre) {
      return;
    }
    this.ejecutar(this.servicio.crearGrupo(nombre), () => {
      this.nombreGrupo = '';
      this.avisos.ok('Grupo creado.');
    });
  }

  /** @param grupoId grupo al que se asigna la empresa elegida en su selector */
  protected asignar(grupoId: number): void {
    const empresaId = this.elegida()[grupoId];
    if (empresaId) {
      this.ejecutar(this.servicio.asignarEmpresa(empresaId, grupoId), () => {
        this.elegir(grupoId, null);
        this.avisos.ok('Empresa asignada al grupo.');
      });
    }
  }

  /** @param empresaId empresa que sale de su grupo */
  protected quitar(empresaId: number): void {
    this.ejecutar(this.servicio.asignarEmpresa(empresaId, null), () => this.avisos.ok('Empresa fuera del grupo.'));
  }

  /** @param grupoId grupo en el que se abre el formulario del super administrador */
  protected abrirSuper(grupoId: number): void {
    this.superForm.reset();
    this.formSuper.set(grupoId);
  }

  /** @param grupoId grupo del nuevo super administrador */
  protected crearSuper(grupoId: number): void {
    if (this.superForm.invalid) {
      return;
    }
    const v = this.superForm.getRawValue();
    this.ejecutar(this.servicio.crearSuperAdmin(grupoId, { nombre: v.nombre.trim(), correo: v.correo.trim(), password: v.password }), () => {
      this.formSuper.set(null);
      this.superForm.reset();
      this.avisos.ok('Super administrador creado. En su primer ingreso configura el segundo factor.');
    });
  }

  /** @param g grupo; @param s super administrador que se activa o desactiva (al desactivarlo se cortan sus sesiones) */
  protected cambiarEstado(g: GrupoPlataforma, s: SuperAdmin): void {
    this.ejecutar(this.servicio.estadoSuperAdmin(g.id!, s.id!, !s.activo), () =>
      this.avisos.ok(s.activo ? 'Super administrador desactivado.' : 'Super administrador activado.'));
  }

  /** @param g grupo; @param s super administrador que vuelve a configurar su segundo factor */
  protected reiniciarMfa(g: GrupoPlataforma, s: SuperAdmin): void {
    this.ejecutar(this.servicio.reiniciarMfaSuperAdmin(g.id!, s.id!), () =>
      this.avisos.ok('Segundo factor reiniciado: lo configurará en su próximo ingreso.'));
  }

  private cargar(): void {
    this.servicio.grupos().subscribe({
      next: l => this.grupos.set(l),
      error: e => {
        this.grupos.set([]);
        this.error.set(mensajeError(e));
      },
    });
  }

  /** Ejecuta una acción, muestra su error (si falla) en el aviso fijo y recarga la lista al terminar bien. */
  private ejecutar(accion: Observable<unknown>, alExito: () => void): void {
    this.trabajando.set(true);
    this.error.set(null);
    accion.subscribe({
      next: () => {
        this.trabajando.set(false);
        alExito();
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.error.set(mensajeError(e));
      },
    });
  }
}
