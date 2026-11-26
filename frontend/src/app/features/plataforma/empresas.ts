import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

import { EmpresaDetalle, EmpresaPlataforma, EstadoSuscripcion, ModuloPlataforma } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { PlataformaService } from '../../core/servicios/plataforma.service';
import { Avisos } from '../../core/ui/avisos';

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
  protected readonly creando = signal(false);
  protected readonly trabajando = signal(false);
  protected readonly busqueda = signal('');
  /** Muestra la contraseña inicial del administrador para revisarla antes de crear la empresa. */
  protected readonly verClave = signal(false);
  /** Spec 020: el código de ingreso se genera solo; esto permite escribirlo a mano. */
  protected readonly codigoAMano = signal(false);
  /** Empresa recién creada y su código de ingreso, para mostrarlo y copiarlo. */
  protected readonly creada = signal<{ razonSocial: string; codigo: string } | null>(null);

  protected readonly filtradas = computed(() => {
    const t = this.busqueda().trim().toLowerCase();
    return (this.empresas() ?? []).filter(e => !t || [e.razonSocial, e.nit, e.codigoPublico].some(x => x?.toLowerCase().includes(t)));
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
    // Opcional (spec 020): vacío = el backend lo genera desde la razón social. Si se escribe: texto libre (espacios,
    // mayúsculas, tildes); solo se excluye lo que rompe la dirección del registro: / \ ? # %
    codigoPublico: ['', [Validators.minLength(3), Validators.maxLength(40), Validators.pattern(/^[^/\\?#%\u0000-\u001f]+$/)]],
    estadoInicial: ['PRUEBA' as EstadoSuscripcion],
    adminNombre: ['', Validators.required],
    adminCorreo: ['', [Validators.required, Validators.email]],
    adminPassword: ['', [Validators.required, Validators.minLength(8)]],
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

  /** Muestra u oculta el campo para escribir el código a mano; al ocultarlo se borra lo escrito. */
  protected alternarCodigoAMano(): void {
    const aMano = !this.codigoAMano();
    this.codigoAMano.set(aMano);
    if (!aMano) {
      this.nueva.controls.codigoPublico.setValue('');
    }
  }

  /** @param codigo código de ingreso que se copia al portapapeles */
  protected copiarCodigo(codigo: string): void {
    navigator.clipboard?.writeText(codigo).then(
      () => this.avisos.ok('Código copiado.'),
      () => this.avisos.ok(`Código de ingreso: ${codigo}`),
    );
  }

  protected crear(): void {
    if (this.nueva.invalid) {
      this.nueva.markAllAsTouched();
      return;
    }
    if (!this.modulosAlta().length) {
      this.avisos.ok('Marca al menos un módulo para la empresa.');
      return;
    }
    this.trabajando.set(true);
    const datos = this.nueva.getRawValue();
    const escrito = this.codigoAMano() ? datos.codigoPublico.trim() : '';
    // spec 024: sin plan; los módulos marcados en el panel son los que la empresa puede usar
    this.servicio.crear({ ...datos, codigoPublico: escrito || undefined, modulos: [...this.modulosAlta()] }).subscribe({
      next: d => {
        this.trabajando.set(false);
        this.creando.set(false);
        this.nueva.reset();
        this.modulosAlta.set([]);
        this.codigoAMano.set(false);
        this.detalle.set(d);
        this.creada.set({ razonSocial: d.empresa?.razonSocial ?? '', codigo: d.empresa?.codigoPublico ?? '' });
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
