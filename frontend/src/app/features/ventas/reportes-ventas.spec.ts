import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { TableroVentas, VentaReporte } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { TableroDueno } from '../tablero/tablero';
import { ReportesVentas } from './reportes-ventas';

const reporte: VentaReporte = {
  desde: '2026-11-01', hasta: '2026-11-20', agrupar: 'PRODUCTO', soloPropio: false,
  filas: [{ clave: '7', nombre: 'CAB-12 - Cable 12', ventas: 2, unidades: 4, bruto: 50000, devoluciones: 10000, neto: 40000,
    baseNeta: 33613.45, costo: 24000, margen: 9613.45, margenPct: 28.6, baseSinCosto: 0 }],
  totales: { ventas: 2, bruto: 50000, devoluciones: 10000, redondeo: 0, neto: 40000, baseNeta: 33613.45, costo: 24000,
    margen: 9613.45, margenPct: 28.6, baseSinCosto: 0, ticketPromedio: 20000 },
};
const sesion = (roles: string[], modulos = ['VENTAS', 'REPORTES'], permisos: string[] = []) => ({ provide: SesionService, useValue: {
  tieneRol: (r: string[]) => r.some(x => roles.includes(x)), tieneModulo: (m: string) => modulos.includes(m),
  tienePermiso: (p: string) => permisos.includes(p), soloLectura: () => false, empresa: signal(null) } });

describe('Reportes de ventas', () => {
  let fixture: ComponentFixture<ReportesVentas>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[], permisos: string[] = [], sedes: object[] = []) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles, ['VENTAS', 'REPORTES'], permisos)] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportesVentas);
    fixture.detectChanges();
    backend.match(r => r.url === '/api/sedes').forEach(r => r.flush(sedes));
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('muestra las cifras del servidor tal como llegan y pide la agrupación elegida', () => {
    crear(['ADMIN_SISTEMA'], ['REPORTE_MARGEN']);
    backend.expectOne(r => r.url === '/api/reportes/ventas' && r.params.get('agrupar') === 'DIA').flush({ ...reporte, agrupar: 'DIA' });
    c()['agrupar'].set('PRODUCTO');
    c()['consultar']();
    const req = backend.expectOne(r => r.url === '/api/reportes/ventas');
    expect(req.request.params.get('agrupar')).toBe('PRODUCTO');
    expect(req.request.params.get('desde')).toMatch(/^\d{4}-\d{2}-01$/);
    req.flush(reporte);
    fixture.detectChanges();
    expect(texto()).toContain('CAB-12 - Cable 12');
    expect(texto()).toContain('40.000');
    expect(texto()).toContain('28,6 %');
    expect(texto()).toContain('Caja');
    expect(texto()).toContain('Cuadre diario');
  });

  it('el cajero solo ve la pestaña de ventas y el aviso de que son las suyas', () => {
    crear(['CAJERO']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush({ ...reporte, soloPropio: true });
    fixture.detectChanges();
    expect(texto()).toContain('Solo se muestran tus ventas');
    expect(texto()).not.toContain('Cuadre diario');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('[role=tab]').length).toBe(1);
  });

  it('el dueño ve los días que no cuadran y puede revisarlos otra vez', () => {
    crear(['ADMIN_SISTEMA']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush(reporte);
    c()['elegir']('cuadre');
    const lista = backend.expectOne(r => r.url === '/api/reportes/cuadre');
    expect(lista.request.params.get('soloDescuadres')).toBe('true');
    lista.flush({ contenido: [{ fecha: '2026-11-19', cuadra: false, ventasConfirmadas: 60000, ventasEnCaja: 59000,
      unidadesVendidas: 6, unidadesSalida: 6, turnosAbiertos24h: 0,
      ventas: [{ ventaId: 1, consecutivo: 'R1-000001', cobrado: 20000, enCaja: 19000, vendidas: 2, salidas: 2 }], turnos: [] }],
    pagina: 0, tamano: 31, totalElementos: 1, totalPaginas: 1 });
    fixture.detectChanges();
    expect(texto()).toContain('No cuadra');
    expect(texto()).toContain('R1-000001');
    c()['revisar']('2026-11-19');
    backend.expectOne(r => r.url === '/api/reportes/cuadre/2026-11-19' && r.method === 'POST').flush({ fecha: '2026-11-19', cuadra: true });
    backend.expectOne(r => r.url === '/api/reportes/cuadre').flush({ contenido: [], pagina: 0, tamano: 31, totalElementos: 0, totalPaginas: 0 });
  });

  it('ventas por turno (spec 023): filtra por caja, muestra las cifras del servidor y exporta a Excel', () => {
    crear(['SUPERVISOR_VENTAS']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush(reporte);
    c()['elegir']('turnos');
    backend.expectOne('/api/cajas').flush([{ id: 3, nombre: 'Caja 1 mostrador', activa: true }]);
    backend.expectOne(r => r.url === '/api/reportes/caja/turnos' && !r.params.has('cajaId')).flush({
      desde: '2026-11-01', hasta: '2026-11-25',
      turnos: [{ id: 9, caja: 'Caja 1 mostrador', cajero: 'Pedro Cajero', estado: 'CERRADO', abiertoEn: '2026-11-25T08:00:00',
        cerradoEn: '2026-11-25T18:00:00', ventas: { EFECTIVO: 120000, TARJETA_DEBITO: 80000 }, totalVentas: 200000,
        anulaciones: 0, devoluciones: 10000, abonos: 5000, ingresos: 2000, gastos: 7000, retiros: 100000, esperado: 140000,
        contado: 139500, diferencia: -500 }],
      totales: { turnos: 1, ventas: { EFECTIVO: 120000, TARJETA_DEBITO: 80000 }, totalVentas: 200000, devoluciones: 10000,
        ingresos: 7000, egresos: 107000, diferencia: -500 },
    });
    fixture.detectChanges();
    expect(texto()).toContain('Ventas por turno');
    expect(texto()).toContain('Pedro Cajero');
    expect(texto()).toContain('200.000');
    expect(texto()).toContain('139.500');
    expect(texto()).toContain('107.000');
    expect(texto()).not.toContain('PDF');
    c()['cajaId'].set(3);
    c()['consultar']();
    backend.expectOne(r => r.url === '/api/reportes/caja/turnos' && r.params.get('cajaId') === '3')
      .flush({ turnos: [], totales: { turnos: 0, ventas: {}, totalVentas: 0, devoluciones: 0, ingresos: 0, egresos: 0, diferencia: 0 } });
    c()['exportar']('xlsx');
    const req = backend.expectOne(r => r.url === '/api/exportaciones/reportes/caja/turnos');
    expect(req.request.params.get('formato')).toBe('xlsx');
    expect(req.request.params.get('cajaId')).toBe('3');
    req.flush(new Blob(['xlsx']));
  });

  it('ventas sin existencia (spec 030): filtra por estado y muestra lo pendiente', () => {
    crear(['SUPERVISOR_VENTAS']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush(reporte);
    c()['elegir']('sinExistencia');
    backend.expectOne(r => r.url === '/api/reportes/ventas-sin-existencia').flush([
      { id: 1, consecutivo: 'C1-000009', fecha: '2026-11-26T10:00:00', producto: 'Cable 12', codigo: 'CAB-12', unidad: 'm',
        cajero: 'Pedro', caja: 'Caja 1', cantidad: 3, pendiente: 1, motivo: 'No registrado', estado: 'PENDIENTE' }]);
    fixture.detectChanges();
    expect(texto()).toContain('C1-000009');
    expect(texto()).toContain('No registrado');
    expect(texto()).toContain('Pendiente');
    c()['estadoSinExistencia'].set('REGULARIZADA');
    c()['consultar']();
    const r = backend.expectOne(x => x.url === '/api/reportes/ventas-sin-existencia');
    expect(r.request.params.get('estado')).toBe('REGULARIZADA');
    r.flush([]);
  });

  it('exporta con los mismos filtros de la pantalla', () => {
    crear(['SUPERVISOR_VENTAS']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush(reporte);
    c()['agrupar'].set('MEDIO_PAGO');
    c()['exportar']('pdf');
    const req = backend.expectOne(r => r.url === '/api/exportaciones/reportes/ventas');
    expect(req.request.params.get('formato')).toBe('pdf');
    expect(req.request.params.get('agrupar')).toBe('MEDIO_PAGO');
    req.flush(new Blob(['%PDF']));
  });

  it('spec 034: ofrece "Tipo de servicio", filtra por sede y exporta con los mismos filtros', () => {
    crear(['ADMIN_SISTEMA'], ['REPORTE_MARGEN'], [{ id: 1, nombre: 'Centro' }, { id: 2, nombre: 'Norte' }]);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush(reporte);
    fixture.detectChanges();
    expect(texto()).toContain('Tipo de servicio');
    expect(texto()).toContain('Norte');
    c()['agrupar'].set('TIPO_SERVICIO');
    c()['sedeId'].set(2);
    c()['consultar']();
    const req = backend.expectOne(r => r.url === '/api/reportes/ventas');
    expect(req.request.params.get('agrupar')).toBe('TIPO_SERVICIO');
    expect(req.request.params.get('sedeId')).toBe('2');
    req.flush({ ...reporte, agrupar: 'TIPO_SERVICIO', filas: [{ ...reporte.filas![0], clave: 'DOMICILIO', nombre: 'Domicilio' }] });
    fixture.detectChanges();
    expect(texto()).toContain('Domicilio');
    c()['exportar']('xlsx');
    const exp = backend.expectOne(r => r.url === '/api/exportaciones/reportes/ventas');
    expect(exp.request.params.get('agrupar')).toBe('TIPO_SERVICIO');
    expect(exp.request.params.get('sedeId')).toBe('2');
    exp.flush(new Blob(['xlsx']));
  });

  it('spec 034: sin REPORTE_MARGEN no se muestran las columnas ni la tarjeta de costo y margen', () => {
    crear(['CAJERO']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush({ ...reporte, agrupar: 'DIA', soloPropio: true,
      filas: [{ ...reporte.filas![0], costo: null, margen: null, margenPct: null, baseSinCosto: null }],
      totales: { ...reporte.totales!, costo: null, margen: null, margenPct: null, baseSinCosto: null } });
    fixture.detectChanges();
    expect(texto()).toContain('Ventas netas');
    expect(texto()).not.toContain('Margen');
    expect(texto()).not.toContain('Costo');
  });

  it('spec 034: con REPORTE_MARGEN se ven las columnas de costo y margen', () => {
    crear(['AUDITOR'], ['REPORTE_MARGEN']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush(reporte);
    fixture.detectChanges();
    expect(texto()).toContain('Margen %');
    expect(texto()).toContain('Costo');
  });
});

describe('Bloque de ventas del tablero', () => {
  let backend: HttpTestingController;
  const ventas: TableroVentas = { hoy: '2026-11-20', desdeMes: '2026-11-01', ventasHoy: 40000, numeroVentasHoy: 2,
    ventasMes: 1250000, numeroVentasMes: 61, ticketPromedioMes: 20491.8, margenMes: 300000, margenMesPct: 28,
    masVendidos: [{ clave: '7', nombre: 'CAB-12 - Cable 12', neto: 400000 }], porMedio: [{ clave: 'EFECTIVO', nombre: 'Efectivo', neto: 900000 }],
    porCajero: [{ clave: '3', nombre: 'Pedro Cajero', neto: 1250000 }], diferenciaCajaMes: -5000, faltantesMes: -5000, turnosPorAprobar: 1 };

  const crear = (modulos: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      sesion(['ADMIN_SISTEMA'], modulos)] });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TableroDueno);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/reportes/tablero').flush(null);
    backend.match(r => r.url === '/api/sedes').forEach(r => r.flush([]));
    return fixture;
  };

  afterEach(() => backend.verify());

  it('con el módulo de ventas pide el bloque y lo muestra con las cifras del servidor', () => {
    const fixture = crear(['VENTAS', 'REPORTES']);
    backend.expectOne('/api/reportes/tablero/ventas').flush(ventas);
    expect((fixture.componentInstance as unknown as Record<string, any>)['ventas']()).toEqual(ventas);
  });

  it('sin el módulo de ventas no lo pide', () => {
    crear(['REPORTES']);
    backend.expectNone('/api/reportes/tablero/ventas');
  });
});
