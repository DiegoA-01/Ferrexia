import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

import { Venta } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { CambioPago } from './cambio-pago';

const venta: Venta = { id: 7, consecutivo: 'C1-000007', estado: 'CONFIRMADA', totalAPagar: 20000,
  pagos: [{ medio: 'EFECTIVO', monto: 20000, recibido: 20000 }] };

/** Spec 029 (D16): cambiar el método de pago con el total fijo. */
describe('Cambiar método de pago', () => {
  let backend: HttpTestingController;

  /**
   * @param permisos permisos de la sesión
   * @param turnoId  turno abierto de la sesión
   */
  const crear = (permisos: string[], turnoId: number | null = 4) => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tienePermiso: (p: string) => permisos.includes(p) } }],
    });
    backend = TestBed.inject(HttpTestingController);
    const f: ComponentFixture<CambioPago> = TestBed.createComponent(CambioPago);
    f.componentRef.setInput('venta', venta);
    f.componentRef.setInput('turnoId', turnoId);
    f.detectChanges();
    return { f, c: f.componentInstance as unknown as Record<string, any>, texto: () => (f.nativeElement as HTMLElement).textContent ?? '' };
  };

  afterEach(() => backend.verify());

  it('arranca con los pagos de la factura, muestra lo que falta y exige motivo y que cuadre', () => {
    const { f, c, texto } = crear(['VENTA_CAMBIAR_PAGO']);
    expect(c['pagos']()).toEqual([{ medio: 'EFECTIVO', monto: 20000, referencia: '' }]);
    c['cambiar'](0, 'medio', 'TARJETA_DEBITO');
    c['cambiar'](0, 'monto', 15000);
    f.detectChanges();
    expect(texto()).toContain('Faltan');
    c['agregar']();
    expect(c['pagos']()[1]).toEqual({ medio: 'TARJETA_DEBITO', monto: 5000, referencia: '' });
    c['cambiar'](1, 'medio', 'EFECTIVO');
    f.detectChanges();
    expect(texto()).toContain('Cuadra con el total');
    expect(c['valido']()).toBe(false);
    c['motivo'].set('Marcó efectivo y era tarjeta');
    expect(c['valido']()).toBe(true);
  });

  it('con el permiso guarda sin PIN y envía los pagos nuevos', () => {
    const { c } = crear(['VENTA_CAMBIAR_PAGO']);
    let cambiada: Venta | null = null;
    c['cambiado'].subscribe((v: Venta) => (cambiada = v));
    c['cambiar'](0, 'medio', 'TARJETA_DEBITO');
    c['cambiar'](0, 'referencia', ' V77 ');
    c['motivo'].set('Marcó efectivo y era tarjeta');
    c['guardar']();
    const r = backend.expectOne(x => x.url === '/api/ventas/7/cambiar-pago' && x.method === 'POST');
    expect(r.request.body).toEqual({ pagos: [{ medio: 'TARJETA_DEBITO', monto: 20000, referencia: 'V77' }],
      motivo: 'Marcó efectivo y era tarjeta', autorizacion: undefined });
    r.flush({ ...venta, pagos: [{ medio: 'TARJETA_DEBITO', monto: 20000 }] });
    expect(cambiada).not.toBeNull();
  });

  it('sin el permiso pide el PIN del supervisor y manda la autorización', () => {
    const { f, c } = crear([]);
    c['cambiar'](0, 'medio', 'TRANSFERENCIA');
    c['motivo'].set('Pagó por transferencia');
    c['guardar']();
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).querySelector('sc-autorizacion-pin')).not.toBeNull();
    c['enviar']('token-1');
    const r = backend.expectOne('/api/ventas/7/cambiar-pago');
    expect(r.request.body.autorizacion).toBe('token-1');
    r.flush(venta);
  });

  it('sin el permiso y sin turno abierto no se puede', () => {
    const { c } = crear([], null);
    c['cambiar'](0, 'medio', 'TRANSFERENCIA');
    c['motivo'].set('Pagó por transferencia');
    expect(c['valido']()).toBe(false);
  });
});
