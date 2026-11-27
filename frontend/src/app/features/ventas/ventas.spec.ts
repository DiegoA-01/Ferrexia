import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { VentaResumen } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { PedidosVenta } from '../pos/pedidos-venta';
import { Ventas } from './ventas';

const resumen: VentaResumen = { id: 5, consecutivo: 'C1-000005', estado: 'CONFIRMADA', caja: 'Caja 1', cliente: 'Consumidor final', cajero: 'Pedro', totalAPagar: 18500 };
const pagina = <T>(l: T[]) => ({ contenido: l, pagina: 0, tamano: 20, totalElementos: l.length, totalPaginas: 1 });
const sesion = (roles: string[]) => ({ provide: SesionService,
  useValue: { tieneRol: (r: string[]) => r.some(x => roles.includes(x)), soloLectura: () => false, empresa: signal(null),
    // spec 029: Dueño y Supervisor anulan de otro turno y cambian pagos sin PIN
    tienePermiso: (p: string) => (['VENTA_ANULAR_OTRO_TURNO', 'VENTA_CAMBIAR_PAGO'].includes(p)
      && roles.some(x => x === 'ADMIN_SISTEMA' || x === 'SUPERVISOR_VENTAS'))
      || (p === 'VENTA_VER_TODAS' && roles.some(x => ['ADMIN_SISTEMA', 'SUPERVISOR_VENTAS', 'AUDITOR'].includes(x))) } });
const limites = [{ rol: 'CAJERO', descuentoMaximoPct: 5 }, { rol: 'SUPERVISOR_VENTAS', descuentoMaximoPct: 15 }, { rol: 'VENDEDOR', descuentoMaximoPct: 5 }];

describe('Ventas', () => {
  let fixture: ComponentFixture<Ventas>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  /** @param roles roles de la sesión; @param turno turno abierto de quien tiene caja */
  const crear = (roles: string[], turno: object | null = null) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles)] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Ventas);
    fixture.detectChanges();
    if (roles.some(r => r === 'CAJERO' || r === 'SUPERVISOR_VENTAS')) {
      const actual = backend.expectOne('/api/turnos/actual');
      if (turno) {
        actual.flush(turno);
      } else {
        actual.flush({}, { status: 404, statusText: 'Not Found' });
      }
    }
    backend.expectOne(r => r.url === '/api/ventas').flush(pagina([resumen]));
    if (roles.some(r => ['ADMIN_SISTEMA', 'SUPERVISOR_VENTAS', 'AUDITOR'].includes(r))) {
      backend.expectOne('/api/ventas/limites-descuento').flush(limites);
    }
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('el historial muestra las ventas y el detalle con quién autorizó', () => {
    crear(['SUPERVISOR_VENTAS']);
    expect(texto()).toContain('C1-000005');
    c()['ver'](resumen);
    backend.expectOne('/api/ventas/5').flush({ ...resumen, autorizadaPor: 'Ana Supervisora', subtotal: 18500, lineas: [{ nombre: 'Cable', cantidad: 5, totalLinea: 18500 }], pagos: [] });
    fixture.detectChanges();
    expect(texto()).toContain('Ana Supervisora');
    // el supervisor ve los límites pero no los cambia
    expect(c()['esDueno']()).toBe(false);
  });

  it('spec 036: el detalle se despliega en la misma fila y se cierra con el mismo botón', () => {
    crear(['SUPERVISOR_VENTAS']);
    c()['ver'](resumen);
    backend.expectOne('/api/ventas/5').flush({ ...resumen, lineas: [], pagos: [] });
    fixture.detectChanges();
    const filas = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.tabla > tbody > tr')];
    expect(filas[0].classList).toContain('abierta');
    expect(filas[1].classList).toContain('fila-detalle');
    expect(filas[1].querySelector('.detalle')).not.toBeNull();
    c()['ver'](resumen);
    backend.expectNone('/api/ventas/5');
    expect(c()['detalle']()).toBeNull();
  });

  it('con permiso anula una factura de un turno cerrado sin PIN y ve el historial de pagos (spec 029)', () => {
    crear(['SUPERVISOR_VENTAS'], { id: 9, estado: 'ABIERTO' });
    c()['ver'](resumen);
    backend.expectOne('/api/ventas/5').flush({ ...resumen, turnoId: 3, turnoAbierto: false, totalDevuelto: 0, lineas: [],
      pagos: [{ medio: 'TARJETA_DEBITO', monto: 18500 }],
      cambiosPago: [{ fecha: '2026-11-25T10:00:00', usuario: 'Ana', antes: { EFECTIVO: 18500 }, despues: { TARJETA_DEBITO: 18500 }, motivo: 'Era tarjeta' }] });
    fixture.detectChanges();
    expect(texto()).toContain('Pago corregido');
    expect(texto()).toContain('Tarjeta débito');
    expect(c()['puedeAnular']()).toBe(false);
    expect(c()['puedeAnularOtro']()).toBe(true);
    expect(c()['puedeCambiarPago']()).toBe(true);
    c()['empezar']('anularOtro');
    c()['motivo'].set('Factura duplicada');
    c()['anularDeOtroTurno']();
    const r = backend.expectOne(x => x.url === '/api/ventas/5/anular-otro-turno' && x.method === 'POST');
    expect(r.request.body).toEqual({ motivo: 'Factura duplicada' });
    r.flush({ ...resumen, estado: 'ANULADA', lineas: [], pagos: [] });
    backend.expectOne(x => x.url === '/api/ventas').flush(pagina([resumen]));
  });

  it('el dueño guarda los límites de descuento', () => {
    crear(['ADMIN_SISTEMA']);
    c()['cambiarLimite']('CAJERO', 8);
    c()['guardarLimites']();
    const r = backend.expectOne(req => req.url === '/api/ventas/limites-descuento' && req.method === 'PUT');
    expect(r.request.body[0]).toEqual({ rol: 'CAJERO', descuentoMaximoPct: 8 });
    r.flush(limites);
  });

  it('el cajero ve sus ventas al abrir, filtra por fecha y estado, busca por consecutivo y anula con el PIN', () => {
    crear(['CAJERO'], { id: 4, estado: 'ABIERTO' });
    expect(texto()).toContain('ventas que usted cobró');
    expect(texto()).toContain('C1-000005');
    c()['desde'].set('2026-11-01');
    c()['hasta'].set('2026-11-26');
    c()['estado'].set('ANULADA');
    c()['buscar']();
    const f = backend.expectOne(r => r.url === '/api/ventas');
    expect(f.request.params.get('desde')).toBe('2026-11-01');
    expect(f.request.params.get('hasta')).toBe('2026-11-26');
    expect(f.request.params.get('estado')).toBe('ANULADA');
    f.flush(pagina([]));
    c()['consecutivo'].set('C1-000005');
    c()['buscar']();
    backend.expectOne(r => r.url === '/api/ventas' && r.params.get('consecutivo') === 'C1-000005').flush(pagina([resumen]));
    c()['ver'](resumen);
    const venta = { ...resumen, turnoId: 4, turnoAbierto: true, totalDevuelto: 0, lineas: [{ id: 11, nombre: 'Cable', cantidad: 5, cantidadDevuelta: 0, totalLinea: 18500 }], pagos: [] };
    backend.expectOne('/api/ventas/5').flush(venta);
    fixture.detectChanges();
    expect(c()['puedeAnular']()).toBe(true);
    c()['empezar']('anular');
    c()['motivo'].set('El cliente desistió');
    c()['autorizado']('permiso-7');
    const r = backend.expectOne('/api/ventas/5/anular');
    expect(r.request.body).toEqual({ motivo: 'El cliente desistió', autorizacion: 'permiso-7' });
    r.flush({ ...venta, estado: 'ANULADA', motivoAnulacion: 'El cliente desistió' });
    backend.expectOne(x => x.url === '/api/ventas').flush(pagina([]));
    fixture.detectChanges();
    expect(texto()).toContain('Anulada');
    expect(c()['puedeAnular']()).toBe(false);
  });

  it('la devolución no deja pasar lo vendido y viaja con la línea, el estado y el reembolso', () => {
    crear(['CAJERO'], { id: 9, estado: 'ABIERTO' });
    c()['detalle'].set({ ...resumen, turnoId: 4, turnoAbierto: false, totalDevuelto: 0,
      lineas: [{ id: 11, nombre: 'Cable', cantidad: 5, cantidadDevuelta: 2, totalLinea: 18500 }], pagos: [] });
    expect(c()['puedeAnular']()).toBe(false);
    expect(c()['puedeDevolver']()).toBe(true);
    c()['empezar']('devolver');
    expect(c()['lineasDevueltas']()[0].maximo).toBe(3);
    c()['motivo'].set('No le sirvió');
    c()['cambiarLinea'](0, 'cantidad', 4);
    expect(c()['totalDevolucionValido']()).toBe(false);
    c()['cambiarLinea'](0, 'cantidad', 1);
    c()['cambiarLinea'](0, 'estado', 'DANADO');
    c()['forma'].set('SALDO_A_FAVOR');
    expect(c()['totalDevolucionValido']()).toBe(true);
    c()['autorizado']('permiso-8');
    const r = backend.expectOne('/api/devoluciones');
    expect(r.request.body).toEqual({ ventaId: 5, motivo: 'No le sirvió', formaReembolso: 'SALDO_A_FAVOR', autorizacion: 'permiso-8',
      lineas: [{ ventaLineaId: 11, cantidad: 1, estadoProducto: 'DANADO' }] });
    r.flush({ id: 1, consecutivo: 'DV-000001', totalReembolso: 3700 });
    backend.expectOne('/api/ventas/5').flush({ ...resumen, lineas: [], pagos: [] });
  });

  it('el aviso de "cobró o vendió" sale según el permiso VENTA_VER_TODAS, no por rol', () => {
    crear(['CAJERO', 'VENDEDOR'], null);
    expect(texto()).toContain('ventas que usted cobró o vendió');
    expect(texto()).toContain('consecutivo completo');
    TestBed.resetTestingModule();
    crear(['SUPERVISOR_VENTAS']);
    expect(texto()).not.toContain('ventas que usted cobró');
  });

  it('el consecutivo sugiere hasta 10 al escribir (con pausa) y al elegir una busca; fechas y estado se combinan', () => {
    vi.useFakeTimers();
    try {
      crear(['CAJERO'], { id: 4, estado: 'ABIERTO' });
      c()['desde'].set('2026-11-01');
      c()['estado'].set('CONFIRMADA');
      c()['escribirConsecutivo']('C');
      vi.advanceTimersByTime(500);
      backend.expectNone(r => r.url === '/api/ventas');
      c()['escribirConsecutivo']('C1');
      c()['escribirConsecutivo']('C1-');
      vi.advanceTimersByTime(500);
      const s = backend.expectOne(r => r.url === '/api/ventas');
      expect(s.request.params.get('consecutivo')).toBe('C1-');
      expect(s.request.params.get('size')).toBe('10');
      expect(s.request.params.get('desde')).toBe('2026-11-01');
      expect(s.request.params.get('estado')).toBe('CONFIRMADA');
      s.flush(pagina([resumen, { ...resumen, id: 6, consecutivo: 'C1-000006' }]));
      fixture.detectChanges();
      expect(c()['sugerencias']()).toEqual(['C1-000005', 'C1-000006']);
      expect((fixture.nativeElement as HTMLElement).querySelectorAll('#vf-cons-sugeridos option').length).toBe(2);
      // elegir una sugerencia lanza la búsqueda con todos los filtros
      c()['escribirConsecutivo']('C1-000006');
      const b = backend.expectOne(r => r.url === '/api/ventas');
      expect(b.request.params.get('consecutivo')).toBe('C1-000006');
      expect(b.request.params.get('size')).toBe('20');
      expect(b.request.params.get('estado')).toBe('CONFIRMADA');
      b.flush(pagina([]));
    } finally {
      vi.useRealTimers();
    }
  });

  it('los filtros viajan al servidor', () => {
    crear(['AUDITOR']);
    c()['consecutivo'].set(' c1-000005 ');
    c()['estado'].set('CONFIRMADA');
    c()['desde'].set('2026-11-01');
    c()['hasta'].set('2026-11-26');
    c()['buscar']();
    const r = backend.expectOne(req => req.url === '/api/ventas');
    expect(r.request.params.get('desde')).toBe('2026-11-01');
    expect(r.request.params.get('hasta')).toBe('2026-11-26');
    expect(r.request.params.get('consecutivo')).toBe('c1-000005');
    expect(r.request.params.get('estado')).toBe('CONFIRMADA');
    r.flush(pagina([]));
  });
});

describe('Pedidos de venta', () => {
  let fixture: ComponentFixture<PedidosVenta>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;

  it('el cajero ve los pendientes y descarta uno', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(['CAJERO'])] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PedidosVenta);
    fixture.detectChanges();
    const lista = backend.expectOne(r => r.url === '/api/pedidos-venta');
    expect(lista.request.params.get('estado')).toBe('PENDIENTE_COBRO');
    lista.flush(pagina([{ id: 3, consecutivo: 'PV-000003', estado: 'PENDIENTE_COBRO', lineas: [] }]));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Cobrar');
    // desde la spec 016, descartar pide el motivo antes de enviar
    c()['descartar']({ id: 3, consecutivo: 'PV-000003' });
    backend.expectNone('/api/pedidos-venta/3/descartar');
    c()['motivo'].set('El cliente desistió');
    c()['confirmarDescarte']();
    const descarte = backend.expectOne('/api/pedidos-venta/3/descartar');
    expect(descarte.request.body).toEqual({ motivo: 'El cliente desistió' });
    descarte.flush({ id: 3, estado: 'DESCARTADO' });
    backend.expectOne(r => r.url === '/api/pedidos-venta').flush(pagina([]));
    backend.verify();
  });
});
