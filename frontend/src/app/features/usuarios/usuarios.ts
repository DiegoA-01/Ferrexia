import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { CentroCosto, PermisoUsuario, Rol, Usuario } from '../../core/api/modelos';
import { nombreRol } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { CajaService } from '../../core/servicios/caja.service';
import { ComprasService } from '../../core/servicios/compras.service';
import { UsuariosService } from '../../core/servicios/usuarios.service';
import { Avisos } from '../../core/ui/avisos';
import { conPermiso } from '../../core/sesion/acceso';
import { SesionService } from '../../core/sesion/sesion.service';
import { MODULOS_PERMISO } from './roles';

type Panel = { tipo: 'ninguno' } | { tipo: 'nuevo' } | { tipo: 'editar'; usuario: Usuario } | { tipo: 'clave'; usuario: Usuario }
  | { tipo: 'permisos'; usuario: Usuario };

/** Ajuste de un permiso en la ficha: como dice su rol, sumado o quitado (spec 022). */
type Ajuste = 'ROL' | 'SUMADO' | 'QUITADO';

/** Quien administra usuarios (spec 022: por permiso). */
const ROLES_GESTIONAR_USUARIOS = conPermiso(['ADMIN_SISTEMA'], 'USUARIO_GESTIONAR');

/** Una clave o PIN que el backend aceptaría según el rol (allá está la regla completa, spec 022 D10). */
const LARGO_MINIMO = 6;

/**
 * Usuarios y roles: el administrador de la empresa crea a sus empleados, les asigna roles (plantillas o propios), les
 * suma o quita permisos sueltos y los activa o desactiva; el auditor solo consulta. El personal entra con un PIN de 6
 * dígitos que asigna el jefe; quien administra usuarios, con clave fuerte (spec 022, D10). Ninguna clave se puede ver:
 * se restablece. Los usuarios de proveedores nacen en el portal, no aquí.
 */
@Component({
  selector: 'sc-usuarios',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, FechaPipe],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
})
export class Usuarios implements OnInit {
  private readonly servicio = inject(UsuariosService);
  private readonly compras = inject(ComprasService);
  private readonly caja = inject(CajaService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly lista = signal<Usuario[] | null>(null);
  /** Spec 025: cajas asignadas a la persona en edición (null = no se muestran o cargando). */
  protected readonly cajasDelUsuario = signal<string[] | null>(null);
  /** Quien edita ve las cajas si la empresa tiene el módulo de caja y puede ver las cajas. */
  protected readonly veCajas = computed(() => this.sesion.tieneModulo('CAJA') && this.sesion.tienePermiso('CAJA_VER'));
  protected readonly roles = signal<Rol[]>([]);
  protected readonly centros = signal<CentroCosto[]>([]);
  protected readonly texto = signal('');
  protected readonly rol = signal<string | null>(null);
  protected readonly activo = signal<boolean | null>(true);
  protected readonly panel = signal<Panel>({ tipo: 'ninguno' });
  protected readonly trabajando = signal(false);
  protected readonly verClave = signal(false);

  protected readonly puedeEditar = computed(() => this.sesion.tieneRol(ROLES_GESTIONAR_USUARIOS) && !this.sesion.soloLectura());
  /** Ficha de permisos de la persona abierta: ajuste elegido por permiso. */
  protected readonly fichaPermisos = signal<PermisoUsuario[] | null>(null);
  protected readonly ajustes = signal<Record<string, Ajuste>>({});
  /** Permisos agrupados por módulo para la ficha. */
  protected readonly gruposPermisos = computed(() => {
    const porModulo = new Map<string, PermisoUsuario[]>();
    for (const p of this.fichaPermisos() ?? []) {
      const m = p.modulo ?? 'OTROS';
      porModulo.set(m, [...(porModulo.get(m) ?? []), p]);
    }
    return [...porModulo.entries()].map(([modulo, permisos]) => ({ modulo, titulo: MODULOS_PERMISO[modulo] ?? modulo, permisos }));
  });
  /** Roles que el administrador asigna aquí (PROVEEDOR se asigna al habilitar un proveedor). */
  protected readonly asignables = computed(() => this.roles().filter(r => r.codigo !== 'PROVEEDOR'));

  /** {@code true} si el formulario da el rol Solicitante sin centro de costo (el backend lo rechaza). */
  protected faltaCentro(): boolean {
    const v = this.form.getRawValue();
    return (v.roles ?? []).includes('SOLICITANTE') && v.centroCostoId == null;
  }
  protected readonly editando = computed(() => {
    const p = this.panel();
    return p.tipo === 'editar' ? p.usuario : null;
  });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    nombreCompleto: ['', [Validators.required, Validators.maxLength(150)]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    password: ['', [Validators.minLength(LARGO_MINIMO), Validators.maxLength(72)]],
    roles: [[] as string[]],
    centroCostoId: [null as number | null],
  });
  protected readonly claveNueva = signal('');

  ngOnInit(): void {
    this.servicio.roles().subscribe({ next: l => this.roles.set(l), error: () => undefined });
    this.compras.centros().subscribe({ next: l => this.centros.set(l.filter(c => c.activo)), error: () => undefined });
    this.cargar();
  }

  protected cargar(): void {
    this.lista.set(null);
    this.servicio.buscar({ texto: this.texto(), rol: this.rol(), activo: this.activo() }).subscribe({
      next: p => this.lista.set(p.contenido ?? []),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }

  /** @param codigo rol */
  protected nombre(codigo: string): string {
    return this.roles().find(r => r.codigo === codigo)?.nombre ?? nombreRol(codigo);
  }

  /** @param id centro de costo */
  protected centro(id: number | undefined): string {
    return id ? (this.centros().find(c => c.id === id)?.nombre ?? `#${id}`) : '—';
  }

  protected nuevo(): void {
    this.form.reset({ nombreCompleto: '', correo: '', password: '', roles: [], centroCostoId: null });
    this.form.controls.password.setValidators([Validators.required, Validators.minLength(LARGO_MINIMO), Validators.maxLength(72)]);
    this.form.controls.password.updateValueAndValidity();
    this.verClave.set(false);
    this.panel.set({ tipo: 'nuevo' });
  }

  /** @param u usuario a editar */
  protected editar(u: Usuario): void {
    this.form.reset({
      nombreCompleto: u.nombreCompleto ?? '', correo: u.correo ?? '', password: '',
      roles: [...(u.roles ?? [])], centroCostoId: u.centroCostoId ?? null,
    });
    this.form.controls.password.setValidators([Validators.minLength(LARGO_MINIMO), Validators.maxLength(72)]);
    this.form.controls.password.updateValueAndValidity();
    this.panel.set({ tipo: 'editar', usuario: u });
    // spec 025: sus cajas asignadas, solo para consultar (se cambian desde la caja)
    this.cajasDelUsuario.set(null);
    if (u.id && this.veCajas()) {
      this.caja.cajasDeUsuario(u.id).subscribe({
        next: l => this.cajasDelUsuario.set(l.map(c => c.nombre ?? '')),
        error: () => this.cajasDelUsuario.set(null),
      });
    }
  }

  /** @param u usuario al que se le pone una contraseña nueva */
  protected abrirClave(u: Usuario): void {
    this.claveNueva.set('');
    this.verClave.set(false);
    this.panel.set({ tipo: 'clave', usuario: u });
  }

  /** @param codigo rol que se marca o desmarca */
  protected alternarRol(codigo: string): void {
    const actuales = this.form.controls.roles.value;
    this.form.controls.roles.setValue(actuales.includes(codigo) ? actuales.filter(r => r !== codigo) : [...actuales, codigo]);
  }

  protected guardar(): void {
    if (this.form.invalid || !this.form.controls.roles.value.length) {
      this.form.markAllAsTouched();
      if (!this.form.controls.roles.value.length) {
        this.avisos.ok('Asigna al menos un rol.');
      }
      return;
    }
    const v = this.form.getRawValue();
    const base = { nombreCompleto: v.nombreCompleto.trim(), correo: v.correo.trim(), roles: v.roles, centroCostoId: v.centroCostoId ?? undefined };
    const u = this.editando();
    const peticion = u?.id
      ? this.servicio.actualizar(u.id, { ...base, proveedorId: u.proveedorId })
      : this.servicio.crear({ ...base, password: v.password });
    this.trabajando.set(true);
    peticion.subscribe({
      next: r => {
        this.trabajando.set(false);
        this.panel.set({ tipo: 'ninguno' });
        this.avisos.ok(u ? `${r.nombreCompleto}: cambios guardados.` : `${r.nombreCompleto} ya puede ingresar con su correo y su contraseña.`);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected guardarClave(): void {
    const p = this.panel();
    if (p.tipo !== 'clave' || !p.usuario.id) {
      return;
    }
    if (this.claveNueva().length < LARGO_MINIMO) {
      this.avisos.ok('Escriba un PIN de 6 dígitos o una clave (quien administra usuarios necesita una fuerte).');
      return;
    }
    this.trabajando.set(true);
    this.servicio.restablecerPassword(p.usuario.id, this.claveNueva()).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.panel.set({ tipo: 'ninguno' });
        this.avisos.ok(`Clave de ${p.usuario.nombreCompleto} restablecida. Entrégasela por un canal seguro: no se puede volver a ver.`);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param u persona cuya ficha de permisos se abre */
  protected abrirPermisos(u: Usuario): void {
    if (!u.id) {
      return;
    }
    this.fichaPermisos.set(null);
    this.panel.set({ tipo: 'permisos', usuario: u });
    this.servicio.permisosDe(u.id).subscribe({ next: l => this.mostrarFicha(l), error: e => this.avisos.error(e) });
  }

  /** @param codigo permiso; @param ajuste como su rol, sumado o quitado */
  protected ajustar(codigo: string, ajuste: Ajuste): void {
    this.ajustes.update(a => ({ ...a, [codigo]: ajuste }));
  }

  protected guardarPermisos(): void {
    const p = this.panel();
    if (p.tipo !== 'permisos' || !p.usuario.id) {
      return;
    }
    const lista = Object.entries(this.ajustes()).filter(([, a]) => a !== 'ROL')
      .map(([permiso, a]) => ({ permiso, concedido: a === 'SUMADO' }));
    this.trabajando.set(true);
    this.servicio.cambiarPermisos(p.usuario.id, lista).subscribe({
      next: l => {
        this.trabajando.set(false);
        this.mostrarFicha(l);
        this.avisos.ok(`Permisos de ${p.usuario.nombreCompleto} guardados.`);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param l ficha que respondió el servidor */
  private mostrarFicha(l: PermisoUsuario[]): void {
    this.fichaPermisos.set(l);
    const a: Record<string, Ajuste> = {};
    l.forEach(x => (a[x.codigo!] = x.ajuste === 'SUMADO' ? 'SUMADO' : x.ajuste === 'QUITADO' ? 'QUITADO' : 'ROL'));
    this.ajustes.set(a);
  }

  /** @param u usuario con MFA activo; el administrador reinicia su segundo factor (cierra sus sesiones) */
  protected reiniciarMfa(u: Usuario): void {
    if (!u.id || !window.confirm(`¿Reiniciar el segundo factor de ${u.nombreCompleto}? Tendrá que vincular su app de nuevo al ingresar y se cierran sus sesiones.`)) {
      return;
    }
    this.servicio.reiniciarMfa(u.id).subscribe({
      next: () => {
        this.avisos.ok(`Segundo factor de ${u.nombreCompleto} reiniciado.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param u usuario; activa o desactiva su acceso (su historial se conserva) */
  protected cambiarEstado(u: Usuario): void {
    if (!u.id) {
      return;
    }
    if (u.activo && !window.confirm(`¿Desactivar a ${u.nombreCompleto}? No podrá ingresar; su historial se conserva.`)) {
      return;
    }
    this.servicio.cambiarEstado(u.id, !u.activo).subscribe({
      next: r => {
        this.avisos.ok(`${r.nombreCompleto} quedó ${r.activo ? 'activo' : 'inactivo'}.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }
}
