import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Turno } from '../../../core/api/modelos';
import { AbonoTurno } from './abono-turno';
import { CierreTurno } from './cierre-turno';
import { FacturasTurno } from './facturas-turno';
import { MovimientoCaja } from './movimiento-caja';

const abierto: Turno = { id: 4, cajaId: 1, caja: 'Caja 1', estado: 'ABIERTO', abiertoEn: '2026-11-20T08:00:00', baseApertura: 50000,
  movimientos: [{ id: 1, tipo: 'BASE', medioPago: 'EFECTIVO', monto: 50000 }] };

/** Formularios de "Mi turno" separados en la spec 018: hacen lo mismo que antes. */
describe('Formularios del turno', () => {
  let backend: HttpTestingController;

  const preparar = () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  };

  afterEach(() => backend.verify());

  it('el retiro va con el permiso del supervisor y entrega el turno actualizado', () => {
    preparar();
    const f = TestBed.createComponent(MovimientoCaja);
    f.componentRef.setInput('turnoId', 4);
    f.componentRef.setInput('tipo', 'RETIRO');
    f.detectChanges();
    // el retiro ofrece las categorías de egreso, pero no las exige
    const cat = backend.expectOne(r => r.url === '/api/cajas/categorias-movimiento');
    expect(cat.request.params.get('tipo')).toBe('EGRESO');
    expect(cat.request.params.get('activa')).toBe('true');
    cat.flush([]);
    const c = f.componentInstance as unknown as Record<string, any>;
    let recibido: Turno | null = null;
    f.componentInstance.actualizado.subscribe(t => (recibido = t));
    expect((f.nativeElement as HTMLElement).textContent).toContain('Registrar retiro a la bóveda');
    c['monto'].set(30000);
    c['motivo'].set('A la bóveda');
    c['registrar']('permiso-9');
    const r = backend.expectOne('/api/turnos/4/movimientos');
    expect(r.request.body).toEqual({ tipo: 'RETIRO', monto: 30000, motivo: 'A la bóveda', soporte: null, autorizacion: 'permiso-9', categoriaId: null });
    r.flush({ ...abierto, debeRetirar: false });
    expect(recibido).not.toBeNull();
    expect(c['monto']()).toBeNull();
  });

  it('sin monto o sin motivo no deja pedir el PIN', () => {
    preparar();
    const f = TestBed.createComponent(MovimientoCaja);
    f.componentRef.setInput('turnoId', 4);
    f.componentRef.setInput('tipo', 'GASTO');
    f.detectChanges();
    backend.expectOne(r => r.url === '/api/cajas/categorias-movimiento').flush([]);
    const boton = (f.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')!;
    expect(boton.disabled).toBe(true);
  });

  it('el gasto exige su categoría de egreso y la envía (spec 021)', () => {
    preparar();
    const f = TestBed.createComponent(MovimientoCaja);
    f.componentRef.setInput('turnoId', 4);
    f.componentRef.setInput('tipo', 'GASTO');
    f.detectChanges();
    backend.expectOne(r => r.url === '/api/cajas/categorias-movimiento' && r.params.get('tipo') === 'EGRESO')
      .flush([{ id: 7, tipo: 'EGRESO', nombre: 'Papelería', activa: true }]);
    f.detectChanges();
    const c = f.componentInstance as unknown as Record<string, any>;
    const raiz = f.nativeElement as HTMLElement;
    expect([...raiz.querySelectorAll('#mt-categoria option')].map(o => o.textContent?.trim())).toEqual(['Elija la categoría', 'Papelería']);
    c['monto'].set(5000);
    c['motivo'].set('Resma de papel');
    expect(c['valido']()).toBe(false);
    c['categoriaId'].set(7);
    expect(c['valido']()).toBe(true);
    c['registrar']('permiso-3');
    const r = backend.expectOne('/api/turnos/4/movimientos');
    expect(r.request.body.categoriaId).toBe(7);
    r.flush(abierto);
    expect(c['categoriaId']()).toBeNull();
  });

  it('el ingreso pide categorías de ingreso', () => {
    preparar();
    const f = TestBed.createComponent(MovimientoCaja);
    f.componentRef.setInput('turnoId', 4);
    f.componentRef.setInput('tipo', 'INGRESO');
    f.detectChanges();
    backend.expectOne(r => r.url === '/api/cajas/categorias-movimiento' && r.params.get('tipo') === 'INGRESO').flush([]);
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).textContent).toContain('No hay categorías activas');
  });

  it('las facturas del turno se listan paginadas, se reimprimen y muestran su detalle (spec 021)', () => {
    preparar();
    const f = TestBed.createComponent(FacturasTurno);
    f.componentRef.setInput('turnoId', 4);
    f.detectChanges();
    const lista = backend.expectOne(r => r.url === '/api/ventas');
    expect(lista.request.params.get('turnoId')).toBe('4');
    expect(lista.request.params.get('size')).toBe('20');
    lista.flush({ contenido: [{ id: 11, consecutivo: 'C1-000011', estado: 'CONFIRMADA', cliente: 'Consumidor final', totalAPagar: 11950,
      confirmadaEn: '2026-11-20T10:00:00', medios: ['EFECTIVO', 'TRANSFERENCIA'] }], pagina: 0, tamano: 20, totalElementos: 21, totalPaginas: 2 });
    f.detectChanges();
    const raiz = f.nativeElement as HTMLElement;
    expect(raiz.textContent).toContain('C1-000011');
    expect(raiz.textContent).toContain('Efectivo y Transferencia');
    expect(raiz.textContent).toContain('Página 1 de 2');
    const editar = [...raiz.querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent?.trim() === 'Editar')!;
    expect(editar.disabled).toBe(true);
    const c = f.componentInstance as unknown as Record<string, any>;
    c['ver']({ id: 11 });
    backend.expectOne('/api/ventas/11').flush({ id: 11, consecutivo: 'C1-000011', cliente: 'Consumidor final', totalAPagar: 11950,
      lineas: [{ nombre: 'Cable 12', cantidad: 1, unidad: 'm', precioUnitario: 11930, totalLinea: 11930 }], pagos: [{ medio: 'EFECTIVO', monto: 11950 }] });
    f.detectChanges();
    expect(raiz.textContent).toContain('Cable 12');
    c['cargar'](1);
    expect(backend.expectOne(r => r.url === '/api/ventas').request.params.get('page')).toBe('1');
  });

  it('el abono de un cliente va al servidor y avisa para refrescar el turno', () => {
    preparar();
    const f = TestBed.createComponent(AbonoTurno);
    f.detectChanges();
    const c = f.componentInstance as unknown as Record<string, any>;
    let avisado = false;
    f.componentInstance.registrado.subscribe(() => (avisado = true));
    c['buscarCliente'].set('5566');
    c['buscarClientes']();
    backend.expectOne(r => r.url === '/api/clientes').flush({ contenido: [{ id: 4, nombre: 'Ferretería cliente', numeroDocumento: '5566' }] });
    c['clienteAbono'].set({ id: 4, nombre: 'Ferretería cliente' });
    c['valorAbono'].set(5000);
    const imprimir = window.print;
    c['abonar']();
    const r = backend.expectOne('/api/abonos');
    expect(r.request.body).toEqual({ clienteId: 4, valor: 5000, medioPago: 'EFECTIVO', referencia: null });
    r.flush({ id: 1, consecutivo: 'AB-000001', valor: 5000, saldoPendiente: 20000, aplicaciones: [], creadoEn: '2026-11-20T10:00:00' });
    expect(avisado).toBe(true);
    expect(c['clienteAbono']()).toBeNull();
    window.print = imprimir;
  });

  it('el cierre es ciego: no muestra lo esperado y envía lo contado', () => {
    preparar();
    const f = TestBed.createComponent(CierreTurno);
    f.componentRef.setInput('turnoId', 4);
    f.detectChanges();
    const c = f.componentInstance as unknown as Record<string, any>;
    expect((f.nativeElement as HTMLElement).textContent).not.toContain('Esperado');
    let cerrado: Turno | null = null;
    f.componentInstance.cerrado.subscribe(t => (cerrado = t));
    c['contar']('EFECTIVO', 79000);
    c['cerrar']();
    const r = backend.expectOne('/api/turnos/4/cerrar');
    expect(r.request.body.contadoEfectivo).toBe(79000);
    expect(r.request.body.contadoOtros.TARJETA_DEBITO).toBe(0);
    r.flush({ ...abierto, estado: 'CERRADO', esperado: { EFECTIVO: 80000 } });
    expect(cerrado).not.toBeNull();
  });

  it('el cierre no se puede confirmar sin el efectivo contado', () => {
    preparar();
    const f = TestBed.createComponent(CierreTurno);
    f.componentRef.setInput('turnoId', 4);
    f.detectChanges();
    const boton = [...(f.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent?.includes('Confirmar cierre'))!;
    expect(boton.disabled).toBe(true);
  });
});
