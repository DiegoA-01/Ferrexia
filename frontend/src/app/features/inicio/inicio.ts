import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Factura, Producto, Proveedor, SolicitudResumen, SugerenciaReorden } from '../../core/api/modelos';
import { horasDesde } from '../../core/formato/formato';
import { OpcionMenu, ROLES_PROVEEDORES, SeccionMenu, seccionesVisibles } from '../../core/navegacion/menu';
import { ComprasService } from '../../core/servicios/compras.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { ReportesService } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

/** Pendiente que pide una acción de quien entra. */
interface Atencion {
  tipo: string;
  tono: 'ambar' | 'rojo' | 'gris';
  titulo: string;
  detalle: string;
  accion: string;
  ruta?: string;
  proveedorId?: number;
}

/** Pendiente contado en la tarjeta de su categoría (spec 036): la ruta dice a qué categoría pertenece. */
interface Pendiente {
  ruta: string;
  cantidad: number;
  texto: (n: number) => string;
}

/**
 * Vista 2 · Inicio. Spec 036: solo las categorías del menú (cada una dice lo que tiene pendiente) y "Requiere tu
 * atención". Las cifras, gráficas y el periodo quedan en sus pantallas de reportes.
 */
@Component({
  selector: 'sc-inicio',
  imports: [RouterLink],
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio implements OnInit {
  protected readonly sesion = inject(SesionService);
  private readonly reportes = inject(ReportesService);
  private readonly compras = inject(ComprasService);
  private readonly ordenes = inject(OrdenesService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);

  protected readonly porAprobar = signal<SolicitudResumen[] | null>(null);
  protected readonly bajoMinimo = signal<Producto[] | null>(null);
  protected readonly sugerencias = signal<SugerenciaReorden[] | null>(null);
  protected readonly facturasRevision = signal<Factura[]>([]);
  protected readonly proveedoresNuevos = signal<Proveedor[]>([]);

  /** Categorías del menú (sin Inicio) con las opciones que el usuario ve en su barra lateral (spec 012). */
  protected readonly accesos = computed(() => seccionesVisibles(r => this.sesion.tieneRol(r)).filter(s => s.titulo));

  protected readonly saludo = computed(() => {
    const h = new Date().getHours();
    const nombre = (this.sesion.usuario()?.nombre ?? '').split(' ')[0];
    return `${h < 12 ? 'Buen día' : h < 19 ? 'Buenas tardes' : 'Buenas noches'}, ${nombre}`;
  });

  protected readonly sinExistencias = computed(() => (this.bajoMinimo() ?? []).filter(p => (p.stockTotal ?? 0) <= 0).length);
  protected readonly porReponer = computed(() => (this.sugerencias() ?? []).filter(s => !s.yaCubierto && (s.cantidadSugerida ?? 0) > 0).length);

  protected readonly atencion = computed<Atencion[]>(() => [
    ...this.facturasRevision().map(f => ({
      tipo: 'Factura en revisión', tono: 'ambar' as const,
      titulo: `${f.ordenConsecutivo} · ${f.proveedorRazonSocial}`,
      detalle: `La factura ${f.numero} no cuadra con lo recibido.`,
      accion: 'Revisar', ruta: `/app/ordenes/${f.ordenId}`,
    })),
    ...(this.porAprobar() ?? []).slice(0, 3).map(s => {
      const horas = horasDesde(s.enviadoEn) ?? 0;
      const limite = this.sesion.empresa()?.slaAprobacionHoras ?? 24;
      return {
        tipo: horas >= limite * 0.8 ? 'SLA en riesgo' : 'Por aprobar', tono: horas >= limite * 0.8 ? 'rojo' as const : 'gris' as const,
        titulo: `${s.consecutivo}`,
        detalle: `Lleva ${horas} h esperando. El plazo de la empresa es de ${limite} h.`,
        accion: 'Aprobar', ruta: '/app/solicitudes',
      };
    }),
    ...this.proveedoresNuevos().map(p => ({
      tipo: 'Proveedor nuevo', tono: 'gris' as const,
      titulo: p.razonSocial ?? '', detalle: 'Espera habilitación.',
      accion: 'Habilitar', proveedorId: p.id,
    })),
    ...((this.bajoMinimo() ?? []).length ? [{
      tipo: 'Inventario', tono: (this.sinExistencias() > 0 ? 'rojo' : 'ambar') as 'rojo' | 'ambar',
      titulo: `${this.bajoMinimo()!.length} ${this.bajoMinimo()!.length === 1 ? 'producto bajo' : 'productos bajo'} el mínimo`,
      detalle: this.porReponer() > 0
        ? `${this.sinExistencias()} sin existencias. El sistema sugiere reponer ${this.porReponer()}; tú decides si se envía.`
        : `${this.sinExistencias()} sin existencias.`,
      accion: 'Ver', ruta: '/app/inventario',
    }] : []),
  ]);

  /** Lo pendiente de cada pantalla, para contarlo en la tarjeta de su categoría (spec 036). */
  private readonly pendientes = computed<Pendiente[]>(() => [
    { ruta: '/app/solicitudes', cantidad: (this.porAprobar() ?? []).length, texto: n => `${n} por aprobar` },
    { ruta: '/app/ordenes', cantidad: this.facturasRevision().length, texto: n => `${n} ${n === 1 ? 'factura' : 'facturas'} en revisión` },
    { ruta: '/app/proveedores', cantidad: this.proveedoresNuevos().length, texto: n => `${n} ${n === 1 ? 'proveedor' : 'proveedores'} por habilitar` },
    { ruta: '/app/inventario', cantidad: (this.bajoMinimo() ?? []).length, texto: n => `${n} bajo el mínimo` },
  ]);

  /**
   * @param categoria categoría del menú
   * @return lo que tiene pendiente, solo de las pantallas que la persona ve en esa categoría
   */
  protected pendientesDe(categoria: SeccionMenu): string[] {
    const rutas = new Set(categoria.opciones.filter(o => !this.bloqueada(o)).map(o => o.ruta));
    return this.pendientes().filter(p => p.cantidad > 0 && rutas.has(p.ruta)).map(p => p.texto(p.cantidad));
  }

  /**
   * Pantalla a la que entra la tarjeta de una categoría: la primera de su lista que la persona puede usar (spec 013).
   *
   * @param categoria categoría del menú, ya filtrada por los roles del usuario
   * @return ruta de la primera opción sin candado, o {@code null} si todas tienen el módulo apagado
   */
  protected destino(categoria: SeccionMenu): string | null {
    return categoria.opciones.find(o => !this.bloqueada(o))?.ruta ?? null;
  }

  /**
   * @param opcion opción del menú
   * @return {@code true} si su módulo no está contratado (sale con candado, como en la barra lateral)
   */
  protected bloqueada(opcion: OpcionMenu): boolean {
    return !this.sesion.tieneModulo(opcion.modulo);
  }

  ngOnInit(): void {
    if (this.sesion.tieneModulo('COMPRAS')) {
      this.compras.pendientes(50).subscribe({ next: p => this.porAprobar.set(p.contenido ?? []), error: () => this.porAprobar.set([]) });
    }
    if (this.sesion.tieneModulo('INVENTARIO')) {
      this.inventario.alertas().subscribe({ next: l => this.bajoMinimo.set(l), error: () => undefined });
    }
    if (this.sesion.tieneRol(['ADMIN_COMPRAS', 'COMPRADOR', 'ALMACENISTA', 'SOLICITANTE', 'AUDITOR']) && this.sesion.tieneModulo('COMPRAS')) {
      this.inventario.sugerencias().subscribe({ next: l => this.sugerencias.set(l), error: () => undefined });
    }
    if (this.sesion.tieneRol(['ADMINISTRATIVO', 'ADMIN_COMPRAS', 'AUDITOR']) && this.sesion.tieneModulo('ORDENES_PAGOS')) {
      this.ordenes.facturasPorEstado('EN_REVISION').subscribe({ next: p => this.facturasRevision.set(p.contenido ?? []), error: () => undefined });
    }
    if (this.sesion.tieneRol(ROLES_PROVEEDORES) && this.sesion.tieneModulo('PROVEEDORES')) {
      this.reportes.proveedores('PENDIENTE').subscribe({ next: p => this.proveedoresNuevos.set(p.contenido ?? []), error: () => undefined });
    }
  }

  /** @param id proveedor pendiente (solo ADMIN_COMPRAS) */
  protected habilitar(id: number | undefined): void {
    if (!id) {
      return;
    }
    this.reportes.habilitar(id).subscribe({
      next: p => {
        this.proveedoresNuevos.update(l => l.filter(x => x.id !== id));
        this.avisos.ok(`${p.razonSocial} quedó habilitado y ya puede cotizar.`);
      },
      error: e => this.avisos.error(e),
    });
  }
}
