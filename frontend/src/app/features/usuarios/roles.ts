import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Permiso, Rol } from '../../core/api/modelos';
import { UsuariosService } from '../../core/servicios/usuarios.service';
import { conPermiso } from '../../core/sesion/acceso';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/** Nombre de cada módulo del catálogo de permisos. */
export const MODULOS_PERMISO: Record<string, string> = {
  COMPRAS: 'Compras', PROVEEDORES: 'Proveedores', PORTAL: 'Portal del proveedor', INVENTARIO: 'Inventario', VENTAS: 'Ventas',
  CAJA: 'Caja', CARTERA: 'Cartera', REPORTES: 'Reportes', CONFIGURACION: 'Configuración', USUARIOS: 'Usuarios',
  AUDITORIA: 'Auditoría',
};

/** Rol en edición. */
interface Edicion {
  id: number | null;
  nombre: string;
  descripcion: string;
  permisos: Set<string>;
}

/** Quien crea y cambia roles propios (spec 022). */
const ROLES_GESTIONAR = conPermiso(['ADMIN_SISTEMA'], 'ROL_GESTIONAR');

/**
 * Roles (spec 022): las plantillas del sistema (Dueño, Administrador, Cajero…) se ven pero no se cambian; para tener
 * uno distinto se copia la plantilla como rol propio y se marcan sus permisos, agrupados por módulo. Un rol que alguien
 * tiene no se borra.
 */
@Component({
  selector: 'sc-roles',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / <a routerLink="/app/usuarios">Usuarios y roles</a> / Roles</p>
        <div class="encabezado">
          <div><h1>Roles</h1><p>Las plantillas vienen listas. Para un rol distinto, copie una plantilla y cambie sus permisos.</p></div>
          @if (puedeGestionar()) {
            <div class="acciones"><button type="button" class="boton primario" (click)="nuevo(null)">Nuevo rol</button></div>
          }
        </div>
      </div>

      <div class="rejilla" [class.principal]="!!edicion()">
        <section class="tarjeta sin-relleno">
          <div class="tabla-contenedor"><table class="tabla">
            <thead><tr><th>Rol</th><th>Tipo</th><th class="num">Permisos</th><th></th></tr></thead>
            <tbody>
              @for (r of roles(); track r.id) {
                <tr [class.seleccionada]="edicion()?.id === r.id">
                  <td><strong>{{ r.nombre }}</strong>@if (r.descripcion) { <span class="sub">{{ r.descripcion }}</span> }</td>
                  <td><span class="chip" [class.azul]="!r.plantilla">{{ r.plantilla ? 'Plantilla' : 'Propio' }}</span></td>
                  <td class="num">{{ r.permisos?.length ?? 0 }}</td>
                  <td class="num"><div class="fila fin">
                    <button type="button" class="boton chico" (click)="ver(r)">{{ r.plantilla || !puedeGestionar() ? 'Ver' : 'Editar' }}</button>
                    @if (puedeGestionar() && r.codigo !== 'PROVEEDOR') { <button type="button" class="boton chico" (click)="nuevo(r)">Copiar</button> }
                  </div></td>
                </tr>
              } @empty { <tr><td colspan="4" class="vacio">Cargando…</td></tr> }
            </tbody>
          </table></div>
        </section>

        @if (edicion(); as e) {
          <section class="tarjeta">
            <h2>{{ soloLectura() ? 'Plantilla ' + e.nombre : e.id ? 'Editar rol' : 'Nuevo rol propio' }}</h2>
            @if (!soloLectura()) {
              <div class="formulario">
                <div class="campo"><label for="rol-nombre">Nombre</label>
                  <input id="rol-nombre" class="entrada" maxlength="100" [ngModel]="e.nombre" (ngModelChange)="cambiar('nombre', $event)"></div>
                <div class="campo"><label for="rol-desc">Descripción</label>
                  <input id="rol-desc" class="entrada" maxlength="250" placeholder="Opcional" [ngModel]="e.descripcion" (ngModelChange)="cambiar('descripcion', $event)"></div>
              </div>
            } @else {
              <p class="texto-chico texto-2">Las plantillas no se cambian. Use "Copiar" para crear un rol propio a partir de esta.</p>
            }
            @for (g of grupos(); track g.modulo) {
              <fieldset class="grupo">
                <legend>{{ g.titulo }}</legend>
                @for (p of g.permisos; track p.codigo) {
                  <label class="permiso">
                    <input type="checkbox" [checked]="e.permisos.has(p.codigo!)" [disabled]="soloLectura()" (change)="alternar(p.codigo!)">
                    <span>{{ p.nombre }}</span>
                  </label>
                }
              </fieldset>
            }
            <div class="fila margen">
              @if (!soloLectura()) {
                <button type="button" class="boton primario" [disabled]="!valido() || trabajando()" (click)="guardar()">Guardar rol</button>
                @if (e.id) { <button type="button" class="boton peligro" [disabled]="trabajando()" (click)="borrar()">Borrar</button> }
              }
              <button type="button" class="boton" (click)="edicion.set(null)">Cerrar</button>
            </div>
          </section>
        }
      </div>
    </div>
  `,
  styles: `
    .grupo { border: 1px solid var(--sc-borde); border-radius: 10px; padding: 8px 12px; margin: 0 0 10px; display: grid; gap: 4px;
      legend { font-weight: 600; padding: 0 4px; } }
    .permiso { display: flex; gap: 8px; align-items: flex-start; }
    .formulario { display: grid; gap: 10px; margin-bottom: 12px; }
  `,
})
export class Roles implements OnInit {
  private readonly servicio = inject(UsuariosService);
  private readonly avisos = inject(Avisos);
  private readonly sesion = inject(SesionService);

  protected readonly roles = signal<Rol[]>([]);
  protected readonly catalogo = signal<Permiso[]>([]);
  protected readonly edicion = signal<Edicion | null>(null);
  /** {@code true} si se está viendo una plantilla (o la sesión no puede cambiar roles). */
  protected readonly soloLectura = signal(false);
  protected readonly trabajando = signal(false);

  protected readonly puedeGestionar = computed(() => this.sesion.tieneRol(ROLES_GESTIONAR) && !this.sesion.soloLectura());
  /** Permisos agrupados por módulo, sin el portal del proveedor (no se da en roles propios). */
  protected readonly grupos = computed(() => {
    const porModulo = new Map<string, Permiso[]>();
    for (const p of this.catalogo()) {
      if (p.codigo === 'PORTAL_PROVEEDOR') {
        continue;
      }
      const m = p.modulo ?? 'OTROS';
      porModulo.set(m, [...(porModulo.get(m) ?? []), p]);
    }
    return [...porModulo.entries()].map(([modulo, permisos]) => ({ modulo, titulo: MODULOS_PERMISO[modulo] ?? modulo, permisos }));
  });
  protected readonly valido = computed(() => {
    const e = this.edicion();
    return !!e && e.nombre.trim().length > 1 && e.permisos.size > 0;
  });

  ngOnInit(): void {
    this.cargar();
    this.servicio.catalogoPermisos().subscribe({ next: l => this.catalogo.set(l), error: e => this.avisos.error(e) });
  }

  /** @param r rol que se abre: plantilla (solo lectura) o propio */
  protected ver(r: Rol): void {
    this.soloLectura.set(!!r.plantilla || !this.puedeGestionar());
    this.edicion.set({ id: r.plantilla ? null : r.id ?? null, nombre: r.nombre ?? '', descripcion: r.descripcion ?? '',
      permisos: new Set(r.permisos ?? []) });
  }

  /** @param base rol que se copia, o {@code null} para empezar sin permisos */
  protected nuevo(base: Rol | null): void {
    this.soloLectura.set(false);
    const permisos = (base?.permisos ?? []).filter(p => p !== 'PORTAL_PROVEEDOR');
    this.edicion.set({ id: null, nombre: base ? `${base.nombre} (copia)` : '', descripcion: base?.descripcion ?? '', permisos: new Set(permisos) });
  }

  /** @param campo campo de texto; @param valor valor nuevo */
  protected cambiar(campo: 'nombre' | 'descripcion', valor: string): void {
    this.edicion.update(e => (e ? { ...e, [campo]: valor } : e));
  }

  /** @param codigo permiso que se marca o desmarca */
  protected alternar(codigo: string): void {
    this.edicion.update(e => {
      if (!e) {
        return e;
      }
      const permisos = new Set(e.permisos);
      if (permisos.has(codigo)) {
        permisos.delete(codigo);
      } else {
        permisos.add(codigo);
      }
      return { ...e, permisos };
    });
  }

  protected guardar(): void {
    const e = this.edicion();
    if (!e || !this.valido()) {
      return;
    }
    const datos = { nombre: e.nombre.trim(), descripcion: e.descripcion.trim() || undefined, permisos: [...e.permisos] };
    this.trabajando.set(true);
    (e.id ? this.servicio.editarRol(e.id, datos) : this.servicio.crearRol(datos)).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.avisos.ok(`Rol ${r.nombre} guardado.`);
        this.edicion.set(null);
        this.cargar();
      },
      error: err => {
        this.trabajando.set(false);
        this.avisos.error(err);
      },
    });
  }

  protected borrar(): void {
    const e = this.edicion();
    if (!e?.id || !window.confirm(`¿Borrar el rol ${e.nombre}? Solo se puede si nadie lo tiene.`)) {
      return;
    }
    this.servicio.eliminarRol(e.id).subscribe({
      next: () => {
        this.avisos.ok('Rol borrado.');
        this.edicion.set(null);
        this.cargar();
      },
      error: err => this.avisos.error(err),
    });
  }

  private cargar(): void {
    this.servicio.roles().subscribe({ next: l => this.roles.set(l), error: e => this.avisos.error(e) });
  }
}
