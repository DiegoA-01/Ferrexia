import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Caja, Turno } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { MiTurno } from './mi-turno';

const abierto: Turno = { id: 4, cajaId: 1, caja: 'Caja 1', estado: 'ABIERTO', abiertoEn: '2026-11-20T08:00:00', baseApertura: 50000,
  debeRetirar: true, movimientos: [{ id: 1, tipo: 'BASE', medioPago: 'EFECTIVO', monto: 50000 }] };

describe('Mi turno', () => {
  let fixture: ComponentFixture<MiTurno>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const raiz = () => fixture.nativeElement as HTMLElement;
  const texto = () => raiz().textContent ?? '';
  const botones = () => [...raiz().querySelectorAll('button, a.boton')].map(b => b.textContent?.trim() ?? '');

  /**
   * @param turno     turno abierto (null = sin turno)
   * @param cajas     cajas activas que responde el backend
   * @param opciones  módulo de cartera y acción pedida en la URL
   */
  const crear = (turno: Turno | null, cajas: Caja[] = [{ id: 1, nombre: 'Caja 1', sede: 'Centro' }],
    opciones: { cartera?: boolean; accion?: string } = {}) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tieneModulo: (m: string) => m === 'CARTERA' && opciones.cartera !== false } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(opciones.accion ? { accion: opciones.accion } : {}) } } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(MiTurno);
    fixture.detectChanges();
    const actual = backend.expectOne('/api/turnos/actual');
    if (turno) {
      actual.flush(turno);
    } else {
      actual.flush({}, { status: 404, statusText: 'Not Found' });
      backend.expectOne(r => r.url === '/api/cajas').flush(cajas);
    }
    fixture.detectChanges();
  };

  /** @param valor acción que se elige en el selector */
  const elegir = (valor: string | null) => {
    c()['elegir'](valor);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  describe('estados de la pantalla (spec 018)', () => {
    it('sin turno solo se ve el formulario de abrir', () => {
      crear(null);
      expect(texto()).toContain('Abrir turno');
      expect(raiz().querySelector('#mt-accion')).toBeNull();
      expect(botones()).not.toContain('lockCerrar turno');
    });

    it('con turno abierto no aparece el formulario de abrir y sí "Cerrar turno" e "Ir a vender"', () => {
      crear(abierto);
      expect(raiz().querySelector('#ab-caja')).toBeNull();
      expect(texto()).not.toContain('Abrir turno');
      expect(botones().some(b => b.includes('Cerrar turno'))).toBe(true);
      expect(botones().some(b => b.includes('Ir a vender'))).toBe(true);
    });

    it('con turno abierto y sin acción elegida no muestra ningún formulario', () => {
      crear(abierto);
      expect(raiz().querySelector('sc-movimiento-caja')).toBeNull();
      expect(raiz().querySelector('sc-cierre-turno')).toBeNull();
      expect(raiz().querySelector('sc-abono-turno')).toBeNull();
    });

    it('sin turno y abriendo uno pasa al turno abierto', () => {
      crear(null);
      c()['cajaId'].set(1);
      c()['base'].set(80000);
      c()['abrir']();
      const r = backend.expectOne('/api/turnos');
      expect(r.request.body).toEqual({ cajaId: 1, baseApertura: 80000 });
      r.flush(abierto);
      fixture.detectChanges();
      expect(raiz().querySelector('#mt-accion')).not.toBeNull();
      expect(raiz().querySelector('#ab-caja')).toBeNull();
    });

    it('recién cerrado muestra el resultado y "Abrir otro turno" recién ahí muestra el formulario', () => {
      crear(abierto);
      c()['alCerrar']({ ...abierto, estado: 'CERRADO', esperado: { EFECTIVO: 80000 }, contado: { EFECTIVO: 79000 },
        diferencias: { EFECTIVO: -1000 }, requiereAprobacion: false });
      fixture.detectChanges();
      expect(texto()).toContain('Turno cerrado');
      expect(texto()).toContain('Esperado');
      expect(texto()).toContain('cuadró dentro de la tolerancia');
      expect(raiz().querySelector('#ab-caja')).toBeNull();
      c()['nuevo']();
      backend.expectOne(r => r.url === '/api/cajas').flush([{ id: 1, nombre: 'Caja 1', sede: 'Centro' }]);
      fixture.detectChanges();
      expect(raiz().querySelector('#ab-caja')).not.toBeNull();
    });
  });

  describe('selector de acción (spec 018)', () => {
    it('elegir "gasto" muestra solo el formulario del gasto', () => {
      crear(abierto);
      elegir('gasto');
      expect(raiz().querySelectorAll('sc-movimiento-caja').length).toBe(1);
      expect(texto()).toContain('Registrar gasto menor');
      expect(raiz().querySelector('sc-cierre-turno')).toBeNull();
    });

    it('cambiar a "cerrar" reemplaza el formulario por el cierre', () => {
      crear(abierto);
      elegir('gasto');
      elegir('cerrar');
      expect(raiz().querySelector('sc-movimiento-caja')).toBeNull();
      expect(raiz().querySelector('sc-cierre-turno')).not.toBeNull();
    });

    it('el botón "Cerrar turno" del encabezado elige el cierre', () => {
      crear(abierto);
      const boton = [...raiz().querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent?.includes('Cerrar turno'))!;
      boton.click();
      fixture.detectChanges();
      expect(c()['accion']()).toBe('cerrar');
      expect(raiz().querySelector('#cc-EFECTIVO')).not.toBeNull();
    });

    it('la acción queda en la URL', () => {
      crear(abierto);
      elegir('retiro');
      expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { accion: 'retiro' }, replaceUrl: true }));
    });

    it('la acción de la URL se recupera al entrar', () => {
      crear(abierto, undefined, { accion: 'ingreso' });
      expect(c()['accion']()).toBe('ingreso');
      expect(texto()).toContain('Registrar ingreso de efectivo');
    });

    it('sin cartera no ofrece el abono, ni desde la URL', () => {
      crear(abierto, undefined, { cartera: false });
      const opciones = [...raiz().querySelectorAll<HTMLOptionElement>('#mt-accion option')].map(o => o.textContent);
      expect(opciones).not.toContain('Recibir abono de cliente');
      elegir('abono');
      expect(c()['accion']()).toBeNull();
    });

    it('con cartera ofrece el abono', () => {
      crear(abierto);
      elegir('abono');
      expect(raiz().querySelector('sc-abono-turno')).not.toBeNull();
    });

    it('el aviso de tope de efectivo lleva al retiro', () => {
      crear(abierto);
      expect(texto()).toContain('pasó su tope de efectivo');
      const boton = [...raiz().querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent?.includes('Hacer retiro'))!;
      boton.click();
      fixture.detectChanges();
      expect(c()['accion']()).toBe('retiro');
    });
  });

  describe('cajas ocupadas (spec 015)', () => {
    const ocupada: Caja = { id: 1, nombre: 'Caja 1 mostrador', sede: 'Centro', turnoAbierto: true,
      turnoAbiertoPor: 'Cajero', turnoAbiertoDesde: '2026-11-20T11:01:23' };
    const libre: Caja = { id: 2, nombre: 'Caja 2 mostrador', sede: 'Centro', turnoAbierto: false };
    const opciones = () => [...raiz().querySelectorAll<HTMLOptionElement>('#ab-caja option')];

    it('una caja ocupada sale con quién la tiene y desde cuándo, y no se puede elegir', () => {
      crear(null, [ocupada, libre]);
      const op = opciones().find(o => o.textContent?.includes('Caja 1'))!;
      expect(op.textContent?.trim()).toBe('Caja 1 mostrador · Centro · ocupada por Cajero desde 20 nov, 11:01');
      expect(op.disabled).toBe(true);
      expect(opciones().find(o => o.textContent?.includes('Caja 2'))!.disabled).toBe(false);
    });

    it('con una sola caja libre, queda elegida sola', () => {
      crear(null, [ocupada, libre]);
      expect(c()['cajaId']()).toBe(2);
    });

    it('con varias cajas libres no elige ninguna', () => {
      crear(null, [libre, { ...libre, id: 3, nombre: 'Caja 3' }]);
      expect(c()['cajaId']()).toBeNull();
    });

    it('con todas ocupadas muestra qué hacer en vez del formulario', () => {
      crear(null, [ocupada]);
      expect(texto()).toContain('Todas las cajas tienen un turno abierto');
      expect(texto()).toContain('Caja 1 mostrador: ocupada por Cajero desde 20 nov, 11:01');
      expect(raiz().querySelector('#ab-caja')).toBeNull();
      expect([...raiz().querySelectorAll('button')].some(b => b.textContent?.includes('Abrir turno'))).toBe(false);
    });
  });
});
