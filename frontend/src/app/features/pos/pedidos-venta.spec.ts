import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { PedidoVenta } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { PedidosVenta } from './pedidos-venta';

const pagina = (contenido: PedidoVenta[]) => ({ contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: 1 });
const pendiente: PedidoVenta = { id: 3, consecutivo: 'PV-000003', estado: 'PENDIENTE_COBRO', cliente: 'Consumidor final',
  vendedor: 'Vendedor', creadoEn: '2026-11-24T09:00:00', lineas: [] };
const eliminado: PedidoVenta = { id: 4, consecutivo: 'PV-000004', estado: 'DESCARTADO', cliente: 'Consumidor final',
  vendedor: 'Vendedor', creadoEn: '2026-11-24T09:10:00', lineas: [], descartadoPor: 'Cajero',
  descartadoEn: '2026-11-24T10:05:00', motivoDescarte: 'El cliente desistió' };
const viejo: PedidoVenta = { id: 2, consecutivo: 'PV-000002', estado: 'DESCARTADO', cliente: 'Consumidor final',
  vendedor: 'Vendedor', creadoEn: '2026-11-20T09:00:00', lineas: [] };

describe('Pedidos de venta (spec 016)', () => {
  let fixture: ComponentFixture<PedidosVenta>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  /** Crea la pantalla como cajero y responde la primera búsqueda (pendientes). */
  const crear = (pendientes: PedidoVenta[] = [pendiente]) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tieneRol: () => true, soloLectura: () => false, empresa: signal(null) } }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PedidosVenta);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/pedidos-venta').flush(pagina(pendientes));
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('el filtro "Eliminados" muestra quién, cuándo y por qué, y los viejos dicen que no hay dato', () => {
    crear();
    const opciones = [...(fixture.nativeElement as HTMLElement).querySelectorAll('#pv-estado option')].map(o => o.textContent?.trim());
    expect(opciones).toContain('Eliminados');
    c()['estado'].set('DESCARTADO');
    c()['buscar']();
    const r = backend.expectOne(x => x.url === '/api/pedidos-venta');
    expect(r.request.params.get('estado')).toBe('DESCARTADO');
    r.flush(pagina([eliminado, viejo]));
    fixture.detectChanges();
    expect(texto()).toContain('Eliminado por');
    expect(texto()).toContain('Cajero');
    expect(texto()).toContain('24 nov, 10:05');
    expect(texto()).toContain('El cliente desistió');
    expect(texto()).toContain('Sin dato');
    expect(texto()).not.toContain('Cobrar');
  });

  it('descartar pide el motivo y lo envía; sin motivo no se puede', () => {
    crear();
    c()['descartar'](pendiente);
    fixture.detectChanges();
    expect(texto()).toContain('¿Por qué se descarta?');
    const boton = () => [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')]
      .find(b => b.textContent?.includes('Descartar pedido')) as HTMLButtonElement;
    expect(boton().disabled).toBe(true);
    c()['confirmarDescarte']();
    backend.expectNone(x => x.url.endsWith('/descartar'));

    c()['motivo'].set('  Pedido repetido  ');
    fixture.detectChanges();
    expect(boton().disabled).toBe(false);
    c()['confirmarDescarte']();
    const r = backend.expectOne('/api/pedidos-venta/3/descartar');
    expect(r.request.body).toEqual({ motivo: 'Pedido repetido' });
    r.flush({ ...pendiente, estado: 'DESCARTADO', motivoDescarte: 'Pedido repetido' });
    backend.expectOne(x => x.url === '/api/pedidos-venta').flush(pagina([]));
    fixture.detectChanges();
    expect(c()['descartando']()).toBeNull();
  });
});
