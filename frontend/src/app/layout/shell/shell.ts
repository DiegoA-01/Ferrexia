import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { Notificacion, Proveedor } from '../../core/api/modelos';
import { FechaPipe, iniciales } from '../../core/formato/formato';
import { nombreRol } from '../../core/formato/etiquetas';
import { OpcionMenu, SeccionMenu, seccionesVisibles } from '../../core/navegacion/menu';
import { ComprasService } from '../../core/servicios/compras.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { NotificacionesService } from '../../core/servicios/notificaciones.service';
import { ReportesService } from '../../core/servicios/reportes.service';
import { SesionService } from '../../core/sesion/sesion.service';

/** Clave de {@code localStorage} con las categorías del menú que el usuario dejó abiertas. */
const CLAVE_ABIERTAS = 'sc.menu.abiertas';

/** Marco de la empresa: menú lateral según roles y módulos, buscador, notificaciones y usuario. */
@Component({
  selector: 'sc-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, MatMenuModule, MatTooltipModule, FechaPipe],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell implements OnInit {
  protected readonly sesion = inject(SesionService);
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly compras = inject(ComprasService);
  private readonly inventario = inject(InventarioService);
  private readonly reportes = inject(ReportesService);
  private readonly router = inject(Router);
  private readonly destruir = inject(DestroyRef);

  /** Pantalla angosta: el menú se vuelve un panel que se abre con el botón. */
  protected readonly movil = toSignal(inject(BreakpointObserver).observe('(max-width: 1023px)').pipe(map(r => r.matches)), { initialValue: false });
  protected readonly menuAbierto = signal(false);
  protected readonly busqueda = signal('');
  protected readonly recientes = signal<Notificacion[]>([]);
  protected readonly contadores = signal<Record<string, number>>({});
  /** Proveedores que se registraron y esperan que alguien los acepte o rechace. */
  protected readonly pendientes = signal<Proveedor[]>([]);
  protected readonly rechazando = signal<number | null>(null);
  protected readonly motivo = signal('');
  protected readonly errorDecision = signal('');
  /** Lo que muestra la campana: avisos sin leer más decisiones pendientes. */
  protected readonly totalAvisos = computed(() => this.notificaciones.noLeidas() + this.pendientes().length);

  /** Secciones con solo las opciones que el rol del usuario puede usar. */
  protected readonly secciones = computed(() => seccionesVisibles(r => this.sesion.tieneRol(r)));

  /** Categorías del menú lateral que están desplegadas (spec 013). */
  protected readonly abiertas = signal<ReadonlySet<string>>(this.leerAbiertas());

  protected readonly iniciales = computed(() => iniciales(this.sesion.usuario()?.nombre));
  protected readonly rolPrincipal = computed(() => {
    const roles = this.sesion.usuario()?.roles ?? [];
    return roles.length ? `${nombreRol(roles[0])} · ${roles[0]}` : '';
  });

  ngOnInit(): void {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed(this.destruir))
      .subscribe(e => { this.menuAbierto.set(false); this.abrirLaDe((e as NavigationEnd).urlAfterRedirects); });
    this.abrirLaDe(this.router.url);
    this.notificaciones.contar();
    this.cargarContadores();
    this.cargarPendientes();
  }

  /**
   * Despliega o recoge una categoría del menú y lo recuerda en este navegador.
   *
   * @param titulo nombre de la categoría
   */
  protected alternar(titulo: string): void {
    const nuevas = new Set(this.abiertas());
    if (nuevas.has(titulo)) { nuevas.delete(titulo); } else { nuevas.add(titulo); }
    this.abiertas.set(nuevas);
    this.guardarAbiertas();
  }

  /**
   * @param seccion categoría del menú
   * @return {@code true} si está desplegada; la sección sin título (Inicio) siempre lo está
   */
  protected abierta(seccion: SeccionMenu): boolean {
    return !seccion.titulo || this.abiertas().has(seccion.titulo);
  }

  /**
   * @param seccion categoría del menú
   * @return suma de los contadores de sus opciones, para verla con la categoría recogida
   */
  protected contadorDe(seccion: SeccionMenu): number {
    const c = this.contadores();
    return seccion.opciones.reduce((total, o) => total + (o.contador ? c[o.contador] ?? 0 : 0), 0);
  }

  /**
   * Abre la categoría que contiene la pantalla actual (la opción cuya ruta coincide más largo), sin cerrar otras.
   *
   * @param url dirección actual
   */
  private abrirLaDe(url: string): void {
    const ruta = url.split(/[?#]/)[0];
    let mejor: { titulo: string; largo: number } | null = null;
    for (const s of this.secciones()) {
      for (const o of s.opciones) {
        if ((ruta === o.ruta || ruta.startsWith(o.ruta + '/')) && (!mejor || o.ruta.length > mejor.largo)) {
          mejor = { titulo: s.titulo, largo: o.ruta.length };
        }
      }
    }
    if (mejor?.titulo && !this.abiertas().has(mejor.titulo)) {
      this.abiertas.set(new Set([...this.abiertas(), mejor.titulo]));
    }
  }

  /** @return categorías guardadas en este navegador (vacío si no hay o no se puede leer) */
  private leerAbiertas(): ReadonlySet<string> {
    try {
      const guardado = JSON.parse(localStorage.getItem(CLAVE_ABIERTAS) ?? '[]');
      return new Set(Array.isArray(guardado) ? guardado.filter((x): x is string => typeof x === 'string') : []);
    } catch {
      return new Set();
    }
  }

  /** Guarda las categorías abiertas; si el navegador no deja guardar, el menú sigue funcionando. */
  private guardarAbiertas(): void {
    try {
      localStorage.setItem(CLAVE_ABIERTAS, JSON.stringify([...this.abiertas()]));
    } catch {
      // sin almacenamiento: se pierde solo el recuerdo entre visitas
    }
  }

  /** @param opcion opción del menú */
  protected bloqueada(opcion: OpcionMenu): boolean {
    return !this.sesion.tieneModulo(opcion.modulo);
  }

  /** Busca por consecutivo: OC-…, SOL-… o RFQ-… abre el listado filtrado. */
  protected buscar(): void {
    const texto = this.busqueda().trim();
    if (!texto) {
      return;
    }
    const t = texto.toUpperCase();
    const ruta = t.startsWith('SOL') ? '/app/solicitudes' : t.startsWith('RFQ') ? '/app/cotizaciones' : '/app/ordenes';
    void this.router.navigate([ruta], { queryParams: { texto } });
    this.busqueda.set('');
  }

  /** Pantalla a la que lleva cada aviso (si la hay). */
  protected rutaDe(n: Notificacion): string | undefined {
    switch (n.evento) {
      case 'PROVEEDOR_PENDIENTE': return '/app/proveedores';
      case 'COTIZACION_RECIBIDA': case 'RFQ_CERRADA': case 'EXTRACCION_REQUIERE_REVISION': return '/app/cotizaciones';
      case 'ADJUDICACION_PENDIENTE_APROBACION': case 'ADJUDICACION_DECIDIDA': case 'COTIZACION_SELECCIONADA': return '/app/adjudicaciones';
      case 'SOLICITUD_PENDIENTE_APROBACION': case 'SOLICITUD_DECIDIDA': case 'SOLICITUD_APROBADA':
      case 'SOLICITUD_SLA_POR_VENCER': case 'SOLICITUD_ESCALADA': return '/app/solicitudes';
      case 'ORDEN_EMITIDA': case 'ORDEN_ACTUALIZADA': case 'ORDEN_RESPUESTA_PROVEEDOR': case 'ORDEN_DESPACHADA': return '/app/ordenes';
      case 'STOCK_BAJO_MINIMO': return '/app/inventario';
      case 'REABASTECIMIENTO_DIARIO': return '/app/inventario/reabastecimiento';
      case 'STOCK_BAJO_SEDE': return '/app/inventario/reabastecimiento';
      case 'PEDIDO_INTERNO':
      case 'PEDIDO_INTERNO_RESPONDIDO': return '/app/pedidos';
      case 'CATALOGO_ACTUALIZADO': return '/app/catalogos';
      case 'PRECIO_BAJO_COSTO': return '/app/ventas';
      case 'CREDITO_BLOQUEADO': return '/app/cartera';
      case 'ALERTA_CAJERO': return '/app/ventas/reportes';
      case 'INFORME_PEDIDO': case 'INFORME_RECIBIDO': return '/app/informes';
      case 'TURNO_DIFERENCIA': return '/app/caja';
      case 'CUADRE_DESCUADRE': return '/app/ventas/reportes';
      default: return undefined;
    }
  }

  /** Filtro con el que se abre la pantalla de destino del aviso. */
  protected paramsDe(n: Notificacion): Record<string, string> | undefined {
    return n.evento === 'PROVEEDOR_PENDIENTE' ? { estado: 'PENDIENTE' } : undefined;
  }

  /** @param id proveedor pendiente que se quiere rechazar */
  protected pedirMotivo(id: number): void {
    this.motivo.set('');
    this.errorDecision.set('');
    this.rechazando.set(id);
  }

  /** @param id proveedor pendiente que se acepta */
  protected aceptarProveedor(id: number): void {
    this.errorDecision.set('');
    this.reportes.habilitar(id).subscribe({
      next: () => this.quitarPendiente(id),
      error: () => this.errorDecision.set('No se pudo aceptar al proveedor. Inténtalo de nuevo.'),
    });
  }

  /** @param id proveedor pendiente que se rechaza (el motivo queda registrado) */
  protected rechazarProveedor(id: number): void {
    this.errorDecision.set('');
    this.reportes.rechazar(id, this.motivo().trim()).subscribe({
      next: () => this.quitarPendiente(id),
      error: () => this.errorDecision.set('No se pudo rechazar al proveedor. Inténtalo de nuevo.'),
    });
  }

  private quitarPendiente(id: number): void {
    this.pendientes.update(l => l.filter(p => p.id !== id));
    this.rechazando.set(null);
  }

  /** Solo quien administra compras decide sobre proveedores; para el resto no se pide nada. */
  private cargarPendientes(): void {
    if (!this.sesion.tieneRol(['ADMIN_COMPRAS']) || !this.sesion.tieneModulo('PROVEEDORES')) {
      return;
    }
    this.reportes.proveedores('PENDIENTE').subscribe({ next: p => this.pendientes.set(p.contenido ?? []), error: () => undefined });
  }

  /** Abre la campana y trae las notificaciones recientes. */
  protected abrirNotificaciones(): void {
    this.cargarPendientes();
    this.notificaciones.recientes().subscribe({ next: p => this.recientes.set(p.contenido ?? []), error: () => undefined });
  }

  protected marcarLeidas(): void {
    this.notificaciones.marcarTodas().subscribe({
      next: () => this.recientes.update(l => l.map(n => ({ ...n, leida: true }))),
      error: () => undefined,
    });
  }

  private cargarContadores(): void {
    if (this.sesion.tieneModulo('COMPRAS')) {
      this.compras.pendientes(1).subscribe({
        next: p => this.contadores.update(c => ({ ...c, solicitudes: p.totalElementos ?? 0 })),
        error: () => undefined,
      });
    }
    if (this.sesion.tieneModulo('INVENTARIO')) {
      this.inventario.alertas().subscribe({
        next: l => this.contadores.update(c => ({ ...c, bajoMinimo: l.length })),
        error: () => undefined,
      });
    }
  }
}
