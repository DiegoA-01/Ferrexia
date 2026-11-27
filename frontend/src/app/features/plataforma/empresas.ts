import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

import { EmpresaDetalle, EmpresaPlataforma, EstadoSuscripcion, ModuloPlataforma } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { PlataformaService } from '../../core/servicios/plataforma.service';
import { Avisos } from '../../core/ui/avisos';

/** Nombre de cada campo del alta para el aviso (spec 036). */
const ETIQUETAS: Record<string, string> = {
  razonSocial: 'Razón social', nit: 'NIT', estadoInicial: 'Estado inicial',
  adminNombre: 'Nombre del administrador', adminCorreo: 'Correo del administrador', adminPassword: 'Contraseña inicial',
};

/** Qué decir según el error de cada campo (spec 036). */
const PROBLEMAS: Record<string, (e: Record<string, unknown>) => string> = {
  razonSocial: () => 'es obligatoria',
  nit: () => 'es obligatorio',
  estadoInicial: () => 'elige uno',
  adminNombre: () => 'es obligatorio',
  adminCorreo: e => e['required'] ? 'es obligatorio' : 'no es un correo válido',
  adminPassword: e => e['required'] ? 'es obligatoria' : 'mínimo 8 caracteres, con al menos una letra y un número',
};

/**
 * Vista 9 · Consola del dueño: empresas clientes, sus módulos y el estado de la suscripción. Desde la spec 024 no hay
 * planes: al crear la empresa se marcan sus módulos y luego se prenden o apagan uno por uno.
 * No muestra datos de compra de ninguna empresa.
 */
@Component({
  selector: 'sc-empresas',
  imports: [FormsModule, ReactiveFormsModule, Estado, FechaPipe],
  templateUrl: './empresas.html',
  styleUrl: './empresas.scss',
})
export class Empresas implements OnInit {
  private readonly servicio = inject(PlataformaService);
  private readonly avisos = inject(Avisos);

  protected readonly empresas = signal<EmpresaPlataforma[] | null>(null);
  protected readonly detalle = signal<EmpresaDetalle | null>(null);
  protected readonly cambios = signal<Record<string, boolean>>({});
  /** Spec 024: catálogo de módulos para el panel del alta y los que se marcaron. */
  protected readonly catalogo = signal<ModuloPlataforma[]>([]);
  protected readonly modulosAlta = signal<string[]>([]);
  /** Spec 036: ya se oprimió "Crear empresa" (desde ahí se muestra la lista de lo que falta). */
  protected readonly intentoCrear = signal(false);
  protected readonly creando = signal(false);
  protected readonly trabajando = signal(false);
  protected readonly busqueda = signal('');
  /** Muestra la contraseña inicial del administrador para revisarla antes de crear la empresa. */
  protected readonly verClave = signal(false);

  protected readonly filtradas = computed(() => {
    const t = this.busqueda().trim().toLowerCase();
    return (this.empresas() ?? []).filter(e => !t || [e.razonSocial, e.nit].some(x => x?.toLowerCase().includes(t)));
  });
  protected readonly conteo = computed(() => {
    const l = this.empresas() ?? [];
    const de = (e: EstadoSuscripcion) => l.filter(x => x.estado === e).length;
    return { activas: de('ACTIVA'), prueba: de('PRUEBA'), mora: de('MOROSA'), suspendidas: de('SUSPENDIDA') };
  });
  protected readonly hayCambios = computed(() => Object.keys(this.cambios()).length > 0);

  protected readonly nueva = inject(FormBuilder).nonNullable.group({
    razonSocial: ['', Validators.required],
    nit: ['', Validators.required],
    estadoInicial: ['PRUEBA' as EstadoSuscripcion],
    adminNombre: ['', Validators.required],
    adminCorreo: ['', [Validators.required, Validators.email]],
    // la misma regla del backend (ReglasPassword): 8 a 72 caracteres, con al menos una letra y un número
    adminPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72),
      Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)]],
  });

  ngOnInit(): void {
    this.cargar();
    this.servicio.modulos().subscribe({ next: l => this.catalogo.set(l), error: () => undefined });
  }

  /** @param codigo módulo del panel del alta que se marca o desmarca */
  protected marcarModulo(codigo: string): void {
    this.modulosAlta.update(l => (l.includes(codigo) ? l.filter(x => x !== codigo) : [...l, codigo]));
  }

  /** @param todos {@code true} marca todo el catálogo; {@code false} lo desmarca */
  protected marcarTodos(todos: boolean): void {
    this.modulosAlta.set(todos ? this.catalogo().map(m => m.codigo!) : []);
  }

  /** @param e empresa de la tabla */
  protected abrir(e: EmpresaPlataforma): void {
    this.cambios.set({});
    this.servicio.empresa(e.id!).subscribe({ next: d => this.detalle.set(d), error: err => this.avisos.error(err) });
  }

  /** @param codigo módulo; estado que se verá (con el cambio pendiente, si hay) */
  protected activo(codigo: string, actual: boolean | undefined): boolean {
    return this.cambios()[codigo] ?? !!actual;
  }

  /** @param codigo módulo; @param actual estado guardado */
  protected alternar(codigo: string, actual: boolean | undefined): void {
    this.cambios.update(c => {
      const nuevo = { ...c };
      const destino = !(c[codigo] ?? !!actual);
      if (destino === !!actual) {
        delete nuevo[codigo];
      } else {
        nuevo[codigo] = destino;
      }
      return nuevo;
    });
  }

  protected guardarModulos(): void {
    const d = this.detalle();
    const id = d?.empresa?.id;
    const pendientes = Object.entries(this.cambios());
    if (!id || !pendientes.length) {
      return;
    }
    this.trabajando.set(true);
    let ultimo: EmpresaDetalle | null = null;
    const siguiente = (i: number): void => {
      if (i >= pendientes.length) {
        this.trabajando.set(false);
        this.cambios.set({});
        if (ultimo) {
          this.detalle.set(ultimo);
        }
        this.avisos.ok('Módulos guardados. Queda en la auditoría global.');
        return;
      }
      const [modulo, habilitado] = pendientes[i]!;
      this.servicio.modulo(id, modulo, habilitado).subscribe({
        next: r => {
          ultimo = r;
          siguiente(i + 1);
        },
        error: e => {
          this.trabajando.set(false);
          this.avisos.error(e);
        },
      });
    };
    siguiente(0);
  }

  /** @param estado nuevo estado de la suscripción */
  protected cambiarEstado(estado: EstadoSuscripcion): void {
    const e = this.detalle()?.empresa;
    if (!e?.id) {
      return;
    }
    const motivo = window.prompt(estado === 'SUSPENDIDA' ? 'Motivo de la suspensión (corta el acceso de todos sus usuarios):' : 'Motivo del cambio:');
    if (!motivo?.trim()) {
      return;
    }
    this.servicio.estado(e.id, estado, motivo.trim()).subscribe({
      next: d => {
        this.detalle.set(d);
        this.avisos.ok(`${d.empresa?.razonSocial}: ${estado === 'SUSPENDIDA' ? 'suspendida' : 'actualizada'}.`);
        this.cargar();
      },
      error: err => this.avisos.error(err),
    });
  }

  /**
   * @param campo control del formulario de alta
   * @returns qué tiene mal ({@code null} si está bien o aún no se ha tocado)
   */
  protected problema(campo: keyof typeof this.nueva.controls): string | null {
    const c = this.nueva.controls[campo];
    return c.invalid && (c.touched || c.dirty) ? PROBLEMAS[campo](c.errors ?? {}) : null;
  }

  /** @returns lo que impide crear la empresa, en el orden del formulario (spec 036) */
  protected pendientes(): string[] {
    const lista = (Object.keys(this.nueva.controls) as (keyof typeof this.nueva.controls)[])
      .filter(k => this.nueva.controls[k].invalid)
      .map(k => `${ETIQUETAS[k]}: ${PROBLEMAS[k](this.nueva.controls[k].errors ?? {})}`);
    if (!this.modulosAlta().length) {
      lista.push('Módulos: marca al menos uno');
    }
    return lista;
  }

  protected crear(): void {
    // spec 036: si algo falta o está mal, se dice qué es en vez de no hacer nada
    this.intentoCrear.set(true);
    const pendientes = this.pendientes();
    if (pendientes.length) {
      this.nueva.markAllAsTouched();
      this.avisos.ok(`No se puede crear la empresa. Corrige: ${pendientes.join(' · ')}`);
      return;
    }
    this.trabajando.set(true);
    const datos = this.nueva.getRawValue();
    // spec 024: sin plan; los módulos marcados en el panel son los que la empresa puede usar
    this.servicio.crear({ ...datos, modulos: [...this.modulosAlta()] }).subscribe({
      next: d => {
        this.trabajando.set(false);
        this.creando.set(false);
        this.nueva.reset();
        this.modulosAlta.set([]);
        this.intentoCrear.set(false);
        this.detalle.set(d);
        this.avisos.ok(`Empresa ${d.empresa?.razonSocial} creada con su administrador.`);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    this.servicio.empresas().subscribe({
      next: l => {
        this.empresas.set(l);
        if (!this.detalle() && l.length) {
          this.abrir(l[0]!);
        }
      },
      error: e => {
        this.empresas.set([]);
        this.avisos.error(e);
      },
    });
  }
}
