import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Bodega, Caja, ConteoDenominacion, EsperadoTurno, MedioPago, Pagina, Turno, Usuario } from '../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { CajaService } from '../../core/servicios/caja.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { UsuariosService } from '../../core/servicios/usuarios.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

import { Cifra } from '../../core/ui/cifra';
import { VentanaVidrio } from '../../core/ui/ventana-vidrio';
import { CategoriasCaja } from './categorias-caja';
/** Nombre de cada medio de pago. */
export const MEDIOS: Record<MedioPago, string> = {
  EFECTIVO: 'Efectivo', TARJETA_DEBITO: 'Tarjeta débito', TARJETA_CREDITO: 'Tarjeta crédito',
  TRANSFERENCIA: 'Transferencia', BILLETERA: 'Billetera digital',
};

/** Caja en edición. */
interface EdicionCaja {
  id: number | null;
  nombre: string;
  bodegaId: number | null;
  prefijo: string;
  tope: number | null;
  activa: boolean;
}

/**
 * Cajas y turnos (spec 003), para supervisión, el dueño y auditoría: turnos con su tiquete Z, diferencias por aprobar,
 * configuración de cajas, de la tolerancia y de las categorías de ingresos y egresos (spec 021), y el PIN de supervisor.
 * Quien supervisa ve lo esperado de un turno abierto en cualquier momento (D20). Esperados y diferencias los calcula el
 * backend.
 */
@Component({
  selector: 'sc-caja',
  imports: [Cifra, FormsModule, RouterLink, PesosPipe, FechaPipe, CategoriasCaja, VentanaVidrio],
  templateUrl: './caja.html',
  styleUrl: './caja.scss',
})
export class CajasTurnos implements OnInit {
  private readonly servicio = inject(CajaService);
  private readonly inventario = inject(InventarioService);
  private readonly usuarios = inject(UsuariosService);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(Avisos);

  protected readonly medios = MEDIOS;
  protected readonly ordenMedios = Object.keys(MEDIOS) as MedioPago[];
  protected readonly pestana = signal<'turnos' | 'cajas' | 'pin'>('turnos');
  protected readonly turnos = signal<Pagina<Turno> | null>(null);
  protected readonly soloPendientes = signal(true);
  protected readonly detalle = signal<Turno | null>(null);
  /** Lo que debería haber en el turno abierto que se está viendo (spec 021, D20; solo para quien supervisa). */
  protected readonly esperado = signal<EsperadoTurno | null>(null);
  protected readonly explicacion = signal('');
  protected readonly cajas = signal<Caja[]>([]);
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly edicion = signal<EdicionCaja | null>(null);
  /** Spec 025: personas activas de la empresa y las asignadas a la caja en edición (null = cargando). */
  protected readonly personas = signal<Usuario[]>([]);
  protected readonly asignados = signal<number[] | null>(null);
  protected readonly tolerancia = signal<number | null>(null);
  /** Bodega para lo devuelto en mal estado (spec 006); null = no reingresa. */
  protected readonly averias = signal<number | null>(null);
  /** Anulaciones y descuentos autorizados por cajero en un día que avisan al dueño (spec 010). */
  protected readonly umbralAnulaciones = signal<number | null>(null);
  protected readonly umbralDescuentos = signal<number | null>(null);
  protected readonly tienePin = signal<boolean | null>(null);
  protected readonly passwordActual = signal('');
  protected readonly pinNuevo = signal('');
  protected readonly guardando = signal(false);

  protected readonly esDueno = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly puedeAprobar = computed(() =>
    this.sesion.tieneRol(['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly sedeDeBodega = computed(() => {
    const e = this.edicion();
    return this.bodegas().find(b => b.id === e?.bodegaId)?.sedeNombre ?? '—';
  });

  ngOnInit(): void {
    this.cargarTurnos();
    this.servicio.cajas().subscribe({ next: l => this.cajas.set(l), error: e => this.avisos.error(e) });
    this.servicio.configuracion().subscribe({
      next: c => {
        this.tolerancia.set(c.toleranciaDiferencia);
        this.averias.set(c.bodegaAveriasId ?? null);
        this.umbralAnulaciones.set(c.umbralAnulacionesDia ?? null);
        this.umbralDescuentos.set(c.umbralDescuentosDia ?? null);
      },
      error: () => undefined,
    });
    if (this.puedeAprobar()) {
      this.servicio.tienePin().subscribe({ next: r => this.tienePin.set(r.tienePin), error: () => undefined });
    }
  }

  protected cargarTurnos(pagina = 0): void {
    this.servicio.turnos({ pendientes: this.soloPendientes(), pagina }).subscribe({
      next: p => this.turnos.set(p),
      error: e => this.avisos.error(e),
    });
  }

  /** @param t turno; abre su detalle (tiquete Z si está cerrado) */
  protected ver(t: Turno): void {
    this.explicacion.set('');
    this.esperado.set(null);
    this.servicio.turno(t.id!).subscribe({
      next: d => {
        this.detalle.set(d);
        if (d.estado === 'ABIERTO' && this.puedeAprobar()) {
          this.servicio.esperado(d.id!).subscribe({ next: e => this.esperado.set(e), error: e => this.avisos.error(e) });
        }
      },
      error: e => this.avisos.error(e),
    });
  }

  protected aprobar(): void {
    const d = this.detalle();
    const texto = this.explicacion().trim();
    if (!d || texto.length < 10) {
      this.avisos.ok('Escribe qué pasó con la diferencia (al menos 10 caracteres).');
      return;
    }
    this.servicio.aprobar(d.id!, texto).subscribe({
      next: t => {
        this.detalle.set(t);
        this.avisos.ok('Diferencia aprobada.');
        this.cargarTurnos();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param c caja a editar, o null para una nueva */
  protected editarCaja(c: Caja | null): void {
    if (this.bodegas().length === 0) {
      this.inventario.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
    }
    this.edicion.set(c
      ? { id: c.id!, nombre: c.nombre ?? '', bodegaId: c.bodegaId ?? null, prefijo: c.prefijoConsecutivo ?? '', tope: c.topeEfectivo ?? 0, activa: c.activa ?? true }
      : { id: null, nombre: '', bodegaId: null, prefijo: '', tope: 0, activa: true });
    // spec 025: quién puede usar la caja (solo al editar una que ya existe)
    this.asignados.set(null);
    if (c?.id) {
      if (!this.personas().length) {
        // el usuario de un proveedor nunca abre caja: no se ofrece
        this.usuarios.buscar({ activo: true }).subscribe({
          next: p => this.personas.set((p.contenido ?? []).filter(u => u.proveedorId == null)),
          error: () => undefined,
        });
      }
      this.servicio.usuariosDeCaja(c.id).subscribe({
        next: l => this.asignados.set(l.map(u => u.usuarioId!)),
        error: e => this.avisos.error(e),
      });
    }
  }

  /** @param id persona que se marca o desmarca para la caja en edición */
  protected marcarPersona(id: number): void {
    this.asignados.update(l => (l ?? []).includes(id) ? (l ?? []).filter(x => x !== id) : [...(l ?? []), id]);
  }

  /** Guarda quiénes pueden usar la caja en edición (spec 025). */
  protected guardarAsignados(): void {
    const id = this.edicion()?.id;
    const lista = this.asignados();
    if (!id || !lista) {
      return;
    }
    this.servicio.asignarUsuarios(id, lista).subscribe({
      next: l => {
        this.asignados.set(l.map(u => u.usuarioId!));
        this.avisos.ok(l.length ? `La caja la pueden usar ${l.length} personas.` : 'La caja no tiene personas asignadas.');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param campo dato; @param valor valor escrito */
  protected cambiar<K extends keyof EdicionCaja>(campo: K, valor: EdicionCaja[K]): void {
    this.edicion.update(e => (e ? { ...e, [campo]: valor } : e));
  }

  protected guardarCaja(): void {
    const e = this.edicion();
    const bodega = this.bodegas().find(b => b.id === e?.bodegaId);
    if (!e || !bodega || !e.nombre.trim() || !e.prefijo.trim() || e.tope === null || e.tope < 0) {
      this.avisos.ok('Completa nombre, bodega, prefijo y tope de efectivo.');
      return;
    }
    const datos = { nombre: e.nombre.trim(), sedeId: bodega.sedeId!, bodegaId: bodega.id!, prefijoConsecutivo: e.prefijo.trim(), topeEfectivo: e.tope, activa: e.activa };
    this.guardando.set(true);
    const peticion = e.id ? this.servicio.editarCaja(e.id, datos) : this.servicio.crearCaja(datos);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.edicion.set(null);
        this.avisos.ok('Caja guardada.');
        this.servicio.cajas().subscribe({ next: l => this.cajas.set(l), error: () => undefined });
      },
      error: err => {
        this.guardando.set(false);
        this.avisos.error(err);
      },
    });
  }

  protected guardarTolerancia(): void {
    const t = this.tolerancia();
    if (t === null || t < 0) {
      return;
    }
    this.servicio.configurar(t, this.averias(), { anulaciones: this.umbralAnulaciones(), descuentos: this.umbralDescuentos() }).subscribe({
      next: c => {
        this.tolerancia.set(c.toleranciaDiferencia);
        this.averias.set(c.bodegaAveriasId ?? null);
        this.umbralAnulaciones.set(c.umbralAnulacionesDia ?? null);
        this.umbralDescuentos.set(c.umbralDescuentosDia ?? null);
        this.avisos.ok('Configuración guardada.');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** Trae las bodegas la primera vez que se abre la lista de bodegas de averías. */
  protected cargarBodegas(): void {
    if (this.bodegas().length === 0) {
      this.inventario.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
    }
  }

  protected guardarPin(): void {
    if (!/^\d{6}$/.test(this.pinNuevo()) || !this.passwordActual()) {
      this.avisos.ok('Escribe tu contraseña y un PIN de 6 dígitos.');
      return;
    }
    const password = this.passwordActual();
    const pin = this.pinNuevo();
    this.passwordActual.set('');
    this.pinNuevo.set('');
    this.servicio.cambiarPin(password, pin).subscribe({
      next: () => {
        this.tienePin.set(true);
        this.avisos.ok('PIN guardado. Úsalo para autorizar en el mostrador.');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param valor diferencia; tono del número */
  protected tono(valor: number | null | undefined): string {
    return valor == null || valor === 0 ? '' : valor < 0 ? 'rojo' : 'ambar';
  }

  /** @param mapa valores por medio; @param medio medio */
  protected valor(mapa: Record<string, number> | undefined, medio: MedioPago): number {
    return mapa?.[medio] ?? 0;
  }

  /** @param conteo billetes y monedas contados al cerrar (spec 023) @returns efectivo total del conteo */
  protected totalConteo(conteo: ConteoDenominacion[]): number {
    return conteo.reduce((s, f) => s + f.valor * f.cantidad, 0);
  }
}
